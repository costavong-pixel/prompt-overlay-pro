import {test} from 'node:test';
import assert from 'node:assert/strict';
import {TrialStore,hashValue} from '../src/store.js';
import {GooglePlayReceiptReader} from '../src/google-play-receipts.js';
import {PlayPurchaseService} from '../src/play-purchase-service.js';
import {expectedObfuscatedAccountId} from '../src/play-purchase-policy.js';

const HOUR=60*60*1000,DAY=24*HOUR;
const TOKENS=['test-purchase-token-0000001','test-purchase-token-0000002',
  'test-purchase-token-0000003'];
const TS=2_100_000_000_000;

function fixture(nowFn=()=>TS){
  const store=new TrialStore(':memory:',nowFn);
  const bob=store.signIn('bob-verified').user;
  const alice=store.signIn('alice-verified').user;
  const receipts=new Map();
  const calls=[];
  const client={request:async request=>{
    calls.push(request);
    if(request.method==='POST'){
      return {data:{}};
    }
    for(const [token,receipt] of receipts){
      if(request.url.endsWith('/tokens/'+token))return {data:receipt};
    }
    throw Error('No such Google Play purchase');
  }};
  const reader=new GooglePlayReceiptReader({googleClient:client});
  const service=new PlayPurchaseService({reader,store,receiptKey:Buffer.alloc(32,0x17)});
  const owner=user=>expectedObfuscatedAccountId(user.id,hashValue);
  const inapp=(user,productId='pro_lifetime',state='PURCHASED')=>({
    purchaseStateContext:{purchaseState:state},
    productLineItem:[{productId,productOfferDetails:{
      consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED'}}],
    obfuscatedExternalAccountId:owner(user),
    acknowledgementState:'ACKNOWLEDGEMENT_STATE_PENDING'
  });
  const monthly=(user,state='SUBSCRIPTION_STATE_ACTIVE')=>({
    subscriptionState:state,acknowledgementState:'ACKNOWLEDGEMENT_STATE_PENDING',
    externalAccountIdentifiers:{obfuscatedExternalAccountId:owner(user)},
    lineItems:[{productId:'pro_monthly',expiryTime:new Date(TS+2*DAY).toISOString(),autoRenewingPlan:{autoRenewEnabled:true},offerDetails:{basePlanId:'monthly'}}]
  });
  return {store,bob,alice,receipts,calls,service,owner,inapp,monthly,close:()=>store.close()};
}

test('Google verified lifetime receipt is acknowledged and bound to exactly one account',async()=>{
  const f=fixture();
  try{
    f.receipts.set(TOKENS[0],f.inapp(f.bob));
    const ok=await f.service.verify(f.bob,{productId:'pro_lifetime',purchaseToken:TOKENS[0]});
    assert.deepEqual(ok,{verified:true,productId:'pro_lifetime',kind:'lifetime',expiresAtMs:0});
    assert.equal(f.calls.filter(c=>c.method==='POST').length,1);
    assert.ok(f.calls.some(c=>c.url.endsWith('/purchases/products/pro_lifetime/tokens/'+TOKENS[0]+':acknowledge')));
    assert.equal(f.service.access(f.bob).ownsProLifetime,true);
    assert.equal(f.service.access(f.alice).ownsProLifetime,false);
    assert.equal(f.store.entitlement(f.bob).ownsProLifetime,false,
      'Live entitlement only changes after purchaseService injection');
    assert.equal(f.store.db.prepare('SELECT token_hash FROM play_claims').get().token_hash,
      hashValue(TOKENS[0]));
    assert.ok(!JSON.stringify(f.store.db.prepare('SELECT * FROM play_claims').all()).includes(TOKENS[0]),
      'Raw Play purchase token must not be saved in DB');

    // Google returns same purchase details even when a different user tries
    // to present a stolen purchase token: owner binding must reject it.
    const thief=await f.service.verify(f.alice,{productId:'pro_lifetime',purchaseToken:TOKENS[0]});
    assert.equal(thief.verified,false);
    assert.equal(thief.reason,'ACCOUNT_MISMATCH');
  }finally{f.close();}
});

test('a token already associated with one account cannot be rebound',async()=>{
  const f=fixture();
  try{
    f.receipts.set(TOKENS[0],f.inapp(f.bob));
    const a=await f.service.verify(f.bob,{productId:'pro_lifetime',purchaseToken:TOKENS[0]});
    assert.equal(a.verified,true);
    // Even if Google data incorrectly repeats with a different owner, the
    // token hash is uniquely assigned in the durable claim database.
    f.receipts.set(TOKENS[0],f.inapp(f.alice));
    const b=await f.service.verify(f.alice,{productId:'pro_lifetime',purchaseToken:TOKENS[0]});
    assert.equal(b.reason,'TOKEN_ALREADY_BOUND');
    assert.equal(f.service.access(f.alice).ownsProLifetime,false);
  }finally{f.close();}
});

test('pending and canceled Google purchases never add a paid grant',async()=>{
  const f=fixture();
  try{
    f.receipts.set(TOKENS[0],f.inapp(f.bob,'pro_lifetime','PENDING'));
    assert.equal((await f.service.verify(f.bob,{productId:'pro_lifetime',
      purchaseToken:TOKENS[0]})).verified,false);
    f.receipts.set(TOKENS[1],f.monthly(f.bob,'SUBSCRIPTION_STATE_ON_HOLD'));
    const onHold=await f.service.verify(f.bob,{productId:'pro_monthly',purchaseToken:TOKENS[1]});
    assert.equal(onHold.reason,'SUBSCRIPTION_NOT_ENTITLED');
    assert.equal(f.store.db.prepare('SELECT COUNT(*) AS c FROM play_claims').get().c,0);
    assert.equal(f.calls.filter(c=>c.method==='POST').length,0);
  }finally{f.close();}
});

test('verified monthly expires, can be revoked, and denies replay after revocation',async()=>{
  let now=TS;
  const f=fixture(()=>now);
  try{
    f.receipts.set(TOKENS[0],f.monthly(f.bob));
    const a=await f.service.verify(f.bob,{productId:'pro_monthly',purchaseToken:TOKENS[0]});
    assert.equal(a.verified,true);
    assert.equal(f.service.access(f.bob).monthlyAccessUntilMs,TS+2*DAY);
    now+=16*60*1000;
    assert.equal(f.service.access(f.bob).monthlyAccessUntilMs,0,
      'Until automatic reconciliation exists, stale receipts fail closed');
    const b=await f.service.verify(f.bob,{productId:'pro_monthly',purchaseToken:TOKENS[0]});
    assert.equal(b.verified,true);
    assert.equal(f.service.access(f.bob).monthlyAccessUntilMs,TS+2*DAY);
    assert.equal(f.service.ledger.revoke(TOKENS[0]),true);
    assert.equal(f.service.access(f.bob).monthlyAccessUntilMs,0);
    const replay=await f.service.verify(f.bob,{productId:'pro_monthly',purchaseToken:TOKENS[0]});
    assert.equal(replay.reason,'TOKEN_REVOKED');
  }finally{f.close();}
});

test('Google Publisher outage or acknowledgement error cannot create a grant',async()=>{
  const f=fixture();
  try{
    assert.equal((await f.service.verify(f.bob,{productId:'pro_lifetime',
      purchaseToken:TOKENS[0]})).reason,'GOOGLE_PLAY_UNAVAILABLE');
    const rejectAck={request:async request=>{
      if(request.method==='POST')throw Error('Google ack temporarily offline');
      return {data:f.inapp(f.bob)};
    }};
    const reader=new GooglePlayReceiptReader({googleClient:rejectAck});
    const service=new PlayPurchaseService({reader,store:f.store,receiptKey:Buffer.alloc(32,0x17)});
    const result=await service.verify(f.bob,{productId:'pro_lifetime',purchaseToken:TOKENS[0]});
    assert.equal(result.reason,'ACKNOWLEDGEMENT_RETRY_REQUIRED');
    assert.equal(f.store.db.prepare('SELECT COUNT(*) AS c FROM play_claims').get().c,0);
  }finally{f.close();}
});

test('unverified client claims and fake SKU do not access Google or SQLite',async()=>{
  const f=fixture();
  try{
    const variants=[
      {productId:'pro_lifetime',purchaseToken:'one'},
      {productId:'fake_pro',purchaseToken:TOKENS[0]},
      {productId:'pro_monthly',purchaseToken:12345},
      {productId:'basic_lifetime',purchaseToken:'fake/token?evil=1'}
    ];
    for(const data of variants){
      assert.equal((await f.service.verify(f.bob,data)).verified,false);
    }
    assert.equal(f.calls.length,0);
    assert.equal(f.store.db.prepare('SELECT COUNT(*) AS c FROM play_claims').get().c,0);
  }finally{f.close();}
});
