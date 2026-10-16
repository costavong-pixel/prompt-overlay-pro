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
    `);
    this.record=store.db.transaction((values)=>{
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
    const rows=this.store.db.prepare(`SELECT product_id,kind,
      expires_ms,checked_ms,status FROM play_claims WHERE user_id=?`).all(userId);
    // Only a recently checked valid Play entitlement can grant access here.
    // Real launch requires RTDN, voided/refunded monitoring, periodic
    // publisher reconciliation and a documented offline grace policy.
    const active=rows.filter(row=>{
      if(row.status!=='active'||row.checked_ms>now)return false;
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
