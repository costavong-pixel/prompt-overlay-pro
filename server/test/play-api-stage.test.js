import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createApp} from '../src/app.js';
import {TrialStore,hashValue} from '../src/store.js';
import {expectedObfuscatedAccountId} from '../src/play-purchase-policy.js';

async function withAuthenticatedUser(purchaseService,run){
  const store=new TrialStore(':memory:',()=>2_100_000_000_000);
  let nonce='';
  const server=createApp({
    store,purchaseService,
    verifyGoogleToken:async token=>{
      if(token!=='X'.repeat(101))throw new Error('Fake JWT');
      return {sub:'auth-user',nonce,iss:'https://accounts.google.com'};
    }
  }).listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  const call=async(path,options={})=>{
    const res=await fetch(base+path,{...options,headers:{
      'Content-Type':'application/json',...(options.headers||{})
    }});
    return {status:res.status,json:res.status===204?null:await res.json()};
  };
  try{
    const before=await call('/v1/billing/account-id');
    assert.equal(before.status,401);
    const challenge=await call('/v1/auth/nonce');
    assert.equal(challenge.status,200);
    nonce=challenge.json.nonce;
    const auth=await call('/v1/auth/google',{method:'POST',
      body:JSON.stringify({idToken:'X'.repeat(101),nonce})});
    assert.equal(auth.status,200);
    await run({call,store,user:store.userFromToken(auth.json.sessionToken),
      bearer:{Authorization:'Bearer '+auth.json.sessionToken}});
  }finally{await new Promise(resolve=>server.close(resolve));store.close();}
}

test('authenticated purchase binding is stable and no server purchase processor stays 503',async()=>{
  await withAuthenticatedUser(null,async({call,user,bearer})=>{
    const identity=await call('/v1/billing/account-id',{headers:bearer});
    assert.equal(identity.status,200);
    assert.equal(identity.json.obfuscatedAccountId,
      expectedObfuscatedAccountId(user.id,hashValue));
    assert.equal(identity.json.obfuscatedAccountId.length,64);
    const disabled=await call('/v1/purchases/verify',{method:'POST',headers:bearer,
      body:JSON.stringify({productId:'pro_lifetime',purchaseToken:'real-looking-token-1234'})});
    assert.equal(disabled.status,503);
    assert.equal(disabled.json.error,'BILLING_NOT_ENABLED');
    const grants=await call('/v1/entitlements',{headers:bearer});
    assert.equal(grants.json.ownsProLifetime,false);
    assert.equal(grants.json.ownsBasicLifetime,false);
  });
});

test('staged injected purchase verifier sees only authenticated users and exposes no tokens',async()=>{
  const history=[];
  const service={
    access:()=>({ownsBasicLifetime:false,ownsProLifetime:false,monthlyAccessUntilMs:0}),
    verify:async(user,input)=>{
      history.push({id:user.id,input});
      return {verified:false,reason:'GOOGLE_PLAY_UNAVAILABLE'};
    }
  };
  await withAuthenticatedUser(service,async({call,user,bearer})=>{
    const body={productId:'pro_monthly',purchaseToken:'fake-purchase-token-1234'};
    const rejected=await call('/v1/purchases/verify',{method:'POST',
      body:JSON.stringify(body)});
    assert.equal(rejected.status,401);
    assert.equal(history.length,0);
    const result=await call('/v1/purchases/verify',{method:'POST',
      headers:bearer,body:JSON.stringify(body)});
    assert.equal(result.status,409);
    assert.equal(result.json.verified,false);
    assert.equal(result.json.reason,'GOOGLE_PLAY_UNAVAILABLE');
    assert.equal(history.length,1);
    assert.equal(history[0].id,user.id);
    assert.ok(!JSON.stringify(result.json).includes(body.purchaseToken));
  });
});
