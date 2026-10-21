import {randomBytes,createCipheriv,createDecipheriv,timingSafeEqual} from 'node:crypto';
import {readFile,stat} from 'node:fs/promises';
import {hashValue} from './store.js';

const IV_BYTES=12;
const TAG_BYTES=16;
const AAD_PREFIX='PromptOverlayPlayReceiptVault:v1';

/**
 * Strong encryption at rest for Google Play purchase tokens, which must
 * remain retrievable to recheck renewals/refunds and restore ownership.
 * No token or master key is exposed in logs, HTTP responses or GitHub.
 * This component is not instantiated by deployed server/src/index.js.
 */
export class PlayReceiptVault {
  constructor({store,key}={}){
    if(!store?.db||typeof store.now!=='function')
      throw new TypeError('Persistent TrialStore required');
    if(!Buffer.isBuffer(key)||key.length!==32)
      throw new TypeError('32-byte protected receipt-encryption key required');
    this.store=store;
    this.key=Buffer.from(key);
    store.db.exec(`
      CREATE TABLE IF NOT EXISTS play_receipt_tokens(
        token_hash TEXT PRIMARY KEY REFERENCES play_claims(token_hash)
          ON DELETE CASCADE,
        user_id TEXT NOT NULL,
        product_id TEXT NOT NULL,
        iv BLOB NOT NULL,
        ciphertext BLOB NOT NULL,
        tag BLOB NOT NULL,
        updated_ms INTEGER NOT NULL
      );
    `);
  }

  static associatedData(tokenHash,userId,productId){
    return Buffer.from(AAD_PREFIX+':'+tokenHash+':'+userId+':'+productId,'utf8');
  }

  /**
   * Must be called AFTER PlayClaimLedger.verifiedClaim in the *same* SQLite
   * transaction. Store only tokens that the Google Publisher API validated.
   */
  remember({token,userId,productId}){
    if(typeof token!=='string'||token.length<20||token.length>4096||
        typeof userId!=='string'||typeof productId!=='string')
      throw new TypeError('Verified purchase metadata required');
    const tokenHash=hashValue(token);
    const claim=this.store.db.prepare(`SELECT c.user_id,c.product_id
      FROM play_claims c LEFT JOIN play_refund_holds h
        ON h.token_hash=c.token_hash
      WHERE c.token_hash=? AND c.status='active' AND h.token_hash IS NULL`)
      .get(tokenHash);
    if(!claim||claim.user_id!==userId||claim.product_id!==productId)
      throw new Error('No active, verified owner-bound Play claim');

    const iv=randomBytes(IV_BYTES);
    const cipher=createCipheriv('aes-256-gcm',this.key,iv,{authTagLength:TAG_BYTES});
    cipher.setAAD(PlayReceiptVault.associatedData(tokenHash,userId,productId));
    const ciphertext=Buffer.concat([
      cipher.update(Buffer.from(token,'utf8')),cipher.final()
    ]);
    const tag=cipher.getAuthTag();
    this.store.db.prepare(`INSERT INTO play_receipt_tokens(
      token_hash,user_id,product_id,iv,ciphertext,tag,updated_ms)
      VALUES(?,?,?,?,?,?,?)
      ON CONFLICT(token_hash) DO UPDATE SET
        iv=excluded.iv,ciphertext=excluded.ciphertext,tag=excluded.tag,
        updated_ms=excluded.updated_ms`).run(
          tokenHash,userId,productId,iv,ciphertext,tag,this.store.now());
    return {stored:true,tokenHash};
  }

  /** Normal worker reads exclude revoked or refund-held receipts. */
  unlock({tokenHash,userId,productId}){
    return this.decryptReceipt({tokenHash,userId,productId},false);
  }

  /**
   * PRIVILEGED reconciliation worker ONLY: obtain a held subscription token
   * to check Google's latest successful renewal order before any release.
   * Do not expose this method from a web route or API response.
   */
  unlockForReconciliation({tokenHash,userId,productId}){
    return this.decryptReceipt({tokenHash,userId,productId},true);
  }

  decryptReceipt({tokenHash,userId,productId},allowHeld){
    if(!/^[a-f0-9]{64}$/.test(tokenHash)||typeof userId!=='string'||
        typeof productId!=='string')throw new TypeError('Valid claim identity required');
    const record=this.store.db.prepare(`SELECT r.*
      FROM play_receipt_tokens r JOIN play_claims c
        ON c.token_hash=r.token_hash
      LEFT JOIN play_refund_holds h ON h.token_hash=c.token_hash
      WHERE r.token_hash=? AND c.user_id=? AND c.product_id=?
        AND r.user_id=c.user_id AND r.product_id=c.product_id
        AND c.status='active' AND (h.token_hash IS NULL OR ?=1)`)
      .get(tokenHash,userId,productId,allowHeld?1:0);
    if(!record)throw new Error('No active encrypted receipt for account');
    if(record.iv.length!==IV_BYTES||record.tag.length!==TAG_BYTES)
      throw new Error('Invalid encrypted purchase-token payload');
    const decipher=createDecipheriv('aes-256-gcm',this.key,record.iv,
      {authTagLength:TAG_BYTES});
    decipher.setAAD(PlayReceiptVault.associatedData(tokenHash,userId,productId));
    decipher.setAuthTag(record.tag);
    const clear=Buffer.concat([
      decipher.update(record.ciphertext),decipher.final()
    ]).toString('utf8');
    // AES-GCM authenticates ciphertext; this additional check binds
    // the decrypted purchase token to its immutable token-hash primary key.
    if(!timingSafeEqual(
      Buffer.from(hashValue(clear),'hex'),Buffer.from(tokenHash,'hex')
    ))throw new Error('Receipt integrity check failed');
    return clear;
  }

  /** Can schedule rechecks without exposing raw purchase tokens. */
  candidates(){
    return this.store.db.prepare(`SELECT r.token_hash,r.user_id,
      r.product_id,c.kind,c.expires_ms,c.checked_ms
      FROM play_receipt_tokens r JOIN play_claims c
        ON c.token_hash=r.token_hash
      LEFT JOIN play_refund_holds h ON h.token_hash=r.token_hash
      WHERE c.status='active' AND h.token_hash IS NULL
      ORDER BY c.checked_ms ASC`).all();
  }

  dispose(){
    this.key.fill(0);
  }
}

/**
 * Optional protected key-file preflight. Key is BASE64(32 random bytes),
 * stored outside Git checkout; never committed, logged or sent to a chat.
 */
export async function loadPlayReceiptKey(filePath){
  if(typeof filePath!=='string'||!filePath)
    throw new Error('Private receipt key-file path required');
  const info=await stat(filePath);
  if(!info.isFile()||(process.platform!=='win32'&&(info.mode&0o077)!==0))
    throw new Error('Unsafe receipt encryption key-file permissions');
  const value=(await readFile(filePath,'utf8')).trim();
  if(!/^[A-Za-z0-9+/]{43}=$/.test(value))
    throw new Error('Invalid receipt encryption key-file encoding');
  const bytes=Buffer.from(value,'base64');
  if(bytes.length!==32||bytes.toString('base64')!==value)
    throw new Error('Invalid receipt encryption key-file length');
  return bytes;
}
