import Database from 'better-sqlite3';
import { randomBytes, randomUUID, createHash } from 'node:crypto';

const DAY=24*60*60*1000;
export const TRIAL_MS=90*DAY;
export const SESSION_MS=30*DAY;
const NONCE_MS=5*60*1000;
export const hashValue=(value)=>createHash('sha256').update(value).digest('hex');

export class TrialStore {
  constructor(filename,now=()=>Date.now()){
    this.db=new Database(filename);
    this.db.pragma('journal_mode = WAL');
    this.db.pragma('foreign_keys = ON');
    this.now=now;
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        google_sub TEXT NOT NULL UNIQUE,
        trial_activated_ms INTEGER NOT NULL,
        created_ms INTEGER NOT NULL,
        legacy_paid_basic INTEGER NOT NULL DEFAULT 0,
        basic_lifetime INTEGER NOT NULL DEFAULT 0,
        pro_lifetime INTEGER NOT NULL DEFAULT 0,
        feedback_award_lifetime INTEGER NOT NULL DEFAULT 0,
        monthly_until_ms INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS sessions (
        token_hash TEXT PRIMARY KEY,
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        expires_ms INTEGER NOT NULL,
        created_ms INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
      CREATE TABLE IF NOT EXISTS login_nonces (
        nonce_hash TEXT PRIMARY KEY,
        expires_ms INTEGER NOT NULL
      );
    `);
  }
  close(){this.db.close();}
  newNonce(){
    const nonce=randomBytes(24).toString('base64url');
    this.db.prepare('DELETE FROM login_nonces WHERE expires_ms <= ?').run(this.now());
    this.db.prepare('INSERT INTO login_nonces(nonce_hash,expires_ms) VALUES (?,?)')
      .run(hashValue(nonce),this.now()+NONCE_MS);
    return nonce;
  }
  consumeNonce(nonce){
    if(typeof nonce!=='string'||nonce.length<20||nonce.length>200)return false;
    // SQLite serializes this DELETE, so a second use of the nonce cannot pass.
    return this.db.prepare('DELETE FROM login_nonces WHERE nonce_hash = ? AND expires_ms > ?')
      .run(hashValue(nonce),this.now()).changes===1;
  }
  signIn(googleSub){
    if(typeof googleSub!=='string'||!googleSub||googleSub.length>256)
      throw new Error('Invalid verified Google subject');
    const now=this.now(),id=randomUUID();
    this.db.prepare(`INSERT INTO users(id,google_sub,trial_activated_ms,created_ms)
      VALUES(?,?,?,?) ON CONFLICT(google_sub) DO NOTHING`)
      .run(id,googleSub,now,now);
    const user=this.db.prepare('SELECT * FROM users WHERE google_sub = ?').get(googleSub);
    const token=randomBytes(32).toString('base64url');
    this.db.prepare('INSERT INTO sessions(token_hash,user_id,expires_ms,created_ms) VALUES(?,?,?,?)')
      .run(hashValue(token),user.id,now+SESSION_MS,now);
    return {token,user,sessionExpiresMs:now+SESSION_MS};
  }
  userFromToken(token){
    if(typeof token!=='string'||!(/^[a-zA-Z0-9_-]{40,80}$/).test(token))return null;
    return this.db.prepare(`SELECT u.* FROM sessions s
      JOIN users u ON u.id = s.user_id WHERE s.token_hash=? AND s.expires_ms>?`)
      .get(hashValue(token),this.now())||null;
  }
  revokeToken(token){this.db.prepare('DELETE FROM sessions WHERE token_hash=?').run(hashValue(token));}
  entitlement(user){
    // Never trust timestamps supplied by a phone; do not automatically extend
    // the first verified activation time on subsequent logins or reinstalls.
    const now=this.now();
    return {
      serverNowMs: now,
      trialActivatedAtMs: user.trial_activated_ms,
      trialEndsAtMs: user.trial_activated_ms + TRIAL_MS,
      ownsBasicLifetime: Boolean(user.basic_lifetime),
      previouslyPurchasedPaidBasic: Boolean(user.legacy_paid_basic),
      ownsProLifetime: Boolean(user.pro_lifetime),
      ownsFeedbackAward: Boolean(user.feedback_award_lifetime),
      monthlyAccessUntilMs: user.monthly_until_ms,
      serverVerified: true
    };
  }
}
