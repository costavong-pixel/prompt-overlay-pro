import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TrialStore, TRIAL_MS } from '../src/store.js';

test('90-day trial persists across sessions and identity',()=>{
  let now=2_000_000_000_000;
  const s=new TrialStore(':memory:',()=>now);
  const one=s.signIn('google-user-01');
  assert.equal(s.entitlement(one.user).trialActivatedAtMs,now);
  now+=5*24*60*60*1000;
  const two=s.signIn('google-user-01');
  assert.equal(one.user.id,two.user.id);
  assert.equal(s.entitlement(two.user).trialActivatedAtMs,2_000_000_000_000);
  assert.equal(s.entitlement(two.user).trialEndsAtMs,2_000_000_000_000+TRIAL_MS);
  assert.ok(s.userFromToken(one.token));
  s.revokeToken(one.token);
  assert.equal(s.userFromToken(one.token),null);
  assert.ok(s.userFromToken(two.token));
  s.close();
});

test('nonce single use and expiry',()=>{
  let now=1_900_000_000_000;
  const s=new TrialStore(':memory:',()=>now);
  const a=s.newNonce();
  assert.ok(s.consumeNonce(a));
  assert.equal(s.consumeNonce(a),false);
  const b=s.newNonce();now+=301_000;
  assert.equal(s.consumeNonce(b),false);
  s.close();
});

test('a different Google subject does not receive a previous trial timestamp',()=>{
  const s=new TrialStore(':memory:',()=>2_000_000_000_000);
  const one=s.signIn('google-user-01');
  const two=s.signIn('google-user-02');
  assert.notEqual(one.user.id,two.user.id);
  assert.equal(s.entitlement(two.user).ownsProLifetime,false);
  assert.equal(s.userFromToken('fake-session'),null);
  s.close();
});
