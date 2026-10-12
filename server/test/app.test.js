import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TrialStore } from '../src/store.js';
import { createApp } from '../src/app.js';

async function runScenario(fn) {
  let now=2_000_000_000_000;
  const store=new TrialStore(':memory:',()=>now);
  const app=createApp({
    store,
    verifyGoogleToken:async token=>{
      if(token!=='X'.repeat(101))throw new Error('invalid signature');
      return {iss:'https://accounts.google.com',sub:'user_verified',nonce:currentNonce};
    }
  });
  let currentNonce='';
  const server=app.listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  async function request(path,init={}){
    const response=await fetch(base+path,{
      ...init,headers:{'Content-Type':'application/json',...(init.headers||{})}
    });
    return {code:response.status,body:response.status===204?null:await response.json()};
  }
  try {
    await fn({request,store,setNonce:value=>{currentNonce=value;},setNow:value=>{now=value;}});
  }finally{await new Promise(resolve=>server.close(resolve));store.close();}
}

test('Google login requires valid token and one-time nonce, only first sign-in activates trial',async()=>{
  await runScenario(async({request,setNonce,setNow})=>{
    const n=await request('/v1/auth/nonce');assert.equal(n.code,200);
    setNonce(n.body.nonce);
    const bad=await request('/v1/auth/google',{method:'POST',body:JSON.stringify({
      idToken:'Z'.repeat(101),nonce:n.body.nonce})});
    assert.equal(bad.code,401);
    const result=await request('/v1/auth/google',{method:'POST',body:JSON.stringify({
      idToken:'X'.repeat(101),nonce:n.body.nonce})});
    assert.equal(result.code,200);
    const t=result.body.sessionToken;
    assert.equal(result.body.entitlement.trialEndsAtMs,result.body.entitlement.trialActivatedAtMs+90*24*3600*1000);
    const replay=await request('/v1/auth/google',{method:'POST',body:JSON.stringify({
      idToken:'X'.repeat(101),nonce:n.body.nonce})});
    assert.equal(replay.code,401);
    assert.equal((await request('/v1/entitlements')).code,401);
    const ent=await request('/v1/entitlements',{headers:{Authorization:'Bearer '+t}});
    assert.equal(ent.code,200);
    assert.equal(ent.body.ownsProLifetime,false);
    const failClosed=await request('/v1/purchases/verify',{method:'POST',headers:{Authorization:'Bearer '+t},body:'{}'});
    assert.equal(failClosed.code,503);
    setNow(2_000_000_000_000+91*24*60*60*1000);
    // The 30-day bearer session expires, but a new Google login must
    // preserve the original 90-day trial start rather than restarting it.
    assert.equal((await request('/v1/entitlements',{headers:{Authorization:'Bearer '+t}})).code,401);
    const newNonce=await request('/v1/auth/nonce');
    setNonce(newNonce.body.nonce);
    const again=await request('/v1/auth/google',{method:'POST',body:JSON.stringify({
      idToken:'X'.repeat(101),nonce:newNonce.body.nonce})});
    assert.equal(again.code,200);
    const laterToken=again.body.sessionToken;
    const later=await request('/v1/entitlements',{headers:{Authorization:'Bearer '+laterToken}});
    assert.equal(later.code,200);
    assert.equal(later.body.trialActivatedAtMs,2_000_000_000_000);
    assert.equal(later.body.serverNowMs,2_000_000_000_000+91*24*60*60*1000);
    const out=await request('/v1/logout',{method:'POST',headers:{Authorization:'Bearer '+laterToken}});
    assert.equal(out.code,204);
    assert.equal((await request('/v1/entitlements',{headers:{Authorization:'Bearer '+laterToken}})).code,401);
  });
});
