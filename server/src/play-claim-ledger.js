import {hashValue} from './store.js';
import {mayGrantFromGoogleReceipt,KNOWN_PRODUCTS} from './play-purchase-policy.js';

/**
 * Durable, account-bound Play token ledger. No purchase tokens or Google
 * identity tokens are written to SQLite. No migration of existing accounts.
 * This module is NOT enabled on the currently deployed production server.
 */
export class PlayClaimLedger {
  constructor(store){
    if(!store?.db||typeof store.now!=='function')
      throw new TypeError('Persistent TrialStore required');
    this.store=store;
    store.db.exec(`
      CREATE TABLE IF NOT EXISTS play_claims(
        token_hash TEXT PRIMARY KEY,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        product_id TEXT NOT NULL,
        kind TEXT NOT NULL CHECK(kind IN ('monthly','lifetime')),
        expires_ms INTEGER NOT NULL DEFAULT 0,
        checked_ms INTEGER NOT NULL,
        status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','revoked')),
        UNIQUE(token_hash,user_id,product_id)
      );
      CREATE INDEX IF NOT EXISTS idx_play_claims_by_user ON
        play_claims(user_id,status,expires_ms);

      -- A void event can precede receipt submission by the Android app.
      -- Never store unencrypted purchase/order identifiers; hash both.
      CREATE TABLE IF NOT EXISTS play_void_events(
        event_hash TEXT PRIMARY KEY,
        token_hash TEXT NOT NULL,
        order_hash TEXT,
        voided_ms INTEGER NOT NULL,
        observed_ms INTEGER NOT NULL,
        claim_kind TEXT NOT NULL CHECK(claim_kind IN ('lifetime','monthly','unknown'))
      );
      CREATE INDEX IF NOT EXISTS idx_play_void_events_token ON
        play_void_events(token_hash,voided_ms);

      -- Subscriptions reuse tokens across monthly renewals. One refunded
      -- renewal must not automatically become a lifetime revoked tombstone.
      -- Instead block access until an authenticated current-order review.
      CREATE TABLE IF NOT EXISTS play_refund_holds(
        token_hash TEXT PRIMARY KEY,
        claim_kind TEXT NOT NULL CHECK(claim_kind IN ('lifetime','monthly','unknown')),
        since_ms INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS play_void_sync(
        key TEXT PRIMARY KEY,
        last_complete_ms INTEGER NOT NULL
      );
    `);
    this.record=store.db.transaction((values)=>{
      // The event may have arrived before the phone submitted its receipt.
      // A later Google "purchased" receipt cannot resurrect a refunded token.
      const held=store.db.prepare(
        'SELECT claim_kind FROM play_refund_holds WHERE token_hash=?'
      ).get(values.tokenHash);
      if(held){
        return {saved:false,reason:'REFUND_REVIEW_REQUIRED'};
      }
      const existing=store.db.prepare(
        'SELECT user_id,product_id,status FROM play_claims WHERE token_hash=?'
      ).get(values.tokenHash);
      if(existing&&(existing.user_id!==values.userId||
          existing.product_id!==values.productId)){
        return {saved:false,reason:'TOKEN_ALREADY_BOUND'};
      }
      if(existing?.status==='revoked'){
        // A refunded/voided token must never resurrect from a repeated
        // request. Fresh repurchases get new token values.
        return {saved:false,reason:'TOKEN_REVOKED'};
      }
      store.db.prepare(`INSERT INTO play_claims(
        token_hash,user_id,product_id,kind,expires_ms,checked_ms,status)
        VALUES(?,?,?,?,?,?,'active')
        ON CONFLICT(token_hash) DO UPDATE SET
          expires_ms=excluded.expires_ms,
          checked_ms=excluded.checked_ms
      `).run(values.tokenHash,values.userId,values.productId,
        values.kind,values.expiresMs,values.checkedAtMs);
      return {saved:true,reason:'VERIFIED'};
    });

    // Invoked ONLY by an authenticated Google Publisher API reconciliation
    // worker (not from an HTTP endpoint or phone). All changes are atomic.
    this.recordVoided=store.db.transaction(({eventHash,tokenHash,
      orderHash,voidedMs,observedMs})=>{
      const claim=store.db.prepare(
        'SELECT kind FROM play_claims WHERE token_hash=?'
      ).get(tokenHash);
      const kind=claim?.kind||'unknown';
      const event=store.db.prepare(`INSERT OR IGNORE INTO play_void_events(
        event_hash,token_hash,order_hash,voided_ms,observed_ms,claim_kind)
        VALUES(?,?,?,?,?,?)`).run(eventHash,tokenHash,orderHash,
        voidedMs,observedMs,kind);
      // Holds also cover refunds seen *before* a successful claim. They
      // cannot be removed via client purchase replay, login, or refresh.
      store.db.prepare(`INSERT INTO play_refund_holds(
        token_hash,claim_kind,since_ms) VALUES(?,?,?)
        ON CONFLICT(token_hash) DO NOTHING`).run(tokenHash,kind,observedMs);
      if(kind==='lifetime'){
        store.db.prepare(`UPDATE play_claims SET status='revoked'
          WHERE token_hash=?`).run(tokenHash);
      }
      return {recorded:event.changes===1,kind};
    });
  }

  /**
   * Record a Google-reported void event without retaining raw IDs. The
   * scheduler must have fetched this from the pinned Google API origin.
   */
  ingestVoided({purchaseToken,orderId=null,voidedTimeMillis}){
    if(typeof purchaseToken!=='string'||purchaseToken.length<20||
       purchaseToken.length>4096)throw new TypeError('Invalid voided token');
    const when=typeof voidedTimeMillis==='string'&&/^[0-9]{10,17}$/.test(voidedTimeMillis)
      ?Number(voidedTimeMillis):NaN;
    const now=this.store.now();
    if(!Number.isSafeInteger(when)||when<=0||when>now+5*60*1000)
      throw new TypeError('Invalid voided event timestamp');
    if(orderId!==null&&(typeof orderId!=='string'||orderId.length>256||
        orderId.length<6))throw new TypeError('Invalid Google order ID');
    const tokenHash=hashValue(purchaseToken);
    const orderHash=orderId?hashValue(orderId):null;
    // A duplicate observation can be replayed without changing access.
    const eventHash=hashValue(tokenHash+':'+(orderHash||'')+':'+String(when));
    return this.recordVoided({eventHash,tokenHash,orderHash,
      voidedMs:when,observedMs:now});
  }

  /** Safe to call after a verified publisher receipt; cannot clear holds. */
  blockedReason(token){
    if(typeof token!=='string'||token.length<20)return 'INVALID_TOKEN';
    const tokenHash=hashValue(token);
    if(this.store.db.prepare(
      'SELECT 1 FROM play_refund_holds WHERE token_hash=?').get(tokenHash))
      return 'REFUND_REVIEW_REQUIRED';
    if(this.store.db.prepare(
      "SELECT 1 FROM play_claims WHERE token_hash=? AND status='revoked'"
    ).get(tokenHash))return 'TOKEN_REVOKED';
    return null;
  }

  lastVoidSync(){
    const row=this.store.db.prepare(
      "SELECT last_complete_ms FROM play_void_sync WHERE key='google-voided-v1'"
    ).get();
    return row?.last_complete_ms??null;
  }

  completeVoidSync(ts){
    if(!Number.isSafeInteger(ts)||ts<=0)
      throw new TypeError('Invalid completed sync timestamp');
    this.store.db.prepare(`INSERT INTO play_void_sync(key,last_complete_ms)
      VALUES('google-voided-v1',?)
      ON CONFLICT(key) DO UPDATE SET
        last_complete_ms=MAX(last_complete_ms,excluded.last_complete_ms)`).run(ts);
  }

  verifiedClaim({user,token,serverDecision}){
    if(typeof user?.id!=='string'||typeof token!=='string'||
        token.length<20||token.length>4096)
      return {saved:false,reason:'INVALID_REQUEST'};
    // A plain JSON object created by the client does not contain the
    // private in-process server verification brand.
    if(!mayGrantFromGoogleReceipt(serverDecision))
      return {saved:false,reason:'NOT_SERVER_VERIFIED'};
    const productId=serverDecision.productId;
    const kind=serverDecision.kind;
    if(KNOWN_PRODUCTS[productId] !==
        (kind==='monthly'?'subs':'inapp'))return {saved:false,reason:'PRODUCT_MISMATCH'};
    const now=this.store.now();
    const expiresMs=kind==='monthly'?serverDecision.expiresAtMs:0;
    if(kind==='monthly'&&(!Number.isSafeInteger(expiresMs)||expiresMs<=now))
      return {saved:false,reason:'EXPIRED'};
    return this.record({
      userId:user.id,tokenHash:hashValue(token),productId,kind,
      expiresMs,checkedAtMs:now
    });
  }

  revoke(token){
    if(typeof token!=='string'||token.length<20||token.length>4096)
      return false;
    const update=this.store.db.prepare(
      "UPDATE play_claims SET status='revoked' WHERE token_hash=?"
    ).run(hashValue(token));
    return update.changes===1;
  }

  status(userId){
    const now=this.store.now();
    const rows=this.store.db.prepare(`SELECT c.product_id,c.kind,
      c.expires_ms,c.checked_ms,c.status,h.token_hash AS refund_hold
      FROM play_claims c LEFT JOIN play_refund_holds h
        ON h.token_hash=c.token_hash
      WHERE c.user_id=?`).all(userId);
    // Only a recently checked valid Play entitlement can grant access here.
    // Real launch requires RTDN, voided/refunded monitoring, periodic
    // publisher reconciliation and a documented offline grace policy.
    const active=rows.filter(row=>{
      if(row.status!=='active'||row.refund_hold||row.checked_ms>now)return false;
      const age=now-row.checked_ms;
      if(row.kind==='monthly')
        return age<=15*60*1000&&row.expires_ms>now;
      return age<=24*60*60*1000;
    });
    return Object.freeze({
      ownsBasicLifetime:active.some(r=>r.product_id==='basic_lifetime'),
      ownsProLifetime:active.some(r=>r.product_id==='pro_lifetime'),
      monthlyAccessUntilMs:Math.max(0,...active.filter(
        r=>r.product_id==='pro_monthly').map(r=>r.expires_ms))
    });
  }
}
