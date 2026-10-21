import {test} from 'node:test';
import assert from 'node:assert/strict';
import {TrialStore,hashValue} from '../src/store.js';
import {PlayClaimLedger} from '../src/play-claim-ledger.js';
import {PlayVoidReconciler} from '../src/play-void-reconciler.js';
import {GooglePlayReceiptReader} from '../src/google-play-receipts.js';
import {
  evaluateOneTime,evaluateMonthly,expectedObfuscatedAccountId
} from '../src/play-purchase-policy.js';

const DAY=24*60*60*1000;
const NOW=2_100_000_000_000;
const TOKEN_ONE='test-voided-token-0000000001';
const TOKEN_TWO='test-voided-token-0000000002';
const TOKEN_THREE='test-voided-token-0000000003';
const ORDER_ONE='GPA.1234-5678-9012-34567';

function setup(nowFn=()=>NOW){
  const store=new TrialStore(':memory:',nowFn);
  const user=store.signIn('verified-user').user;
  const owner=expectedObfuscatedAccountId(user.id,hashValue);
  const ledger=new PlayClaimLedger(store);
  let pageResponder=()=>({data:{voidedPurchases:[]}});
  const calls=[];
  const reader=new GooglePlayReceiptReader({googleClient:{
    request:async options=>{
      calls.push(options);
      return pageResponder(options);
    }
  }});
  const worker=new PlayVoidReconciler({store,reader,ledger});
  const voided=(token=TOKEN_ONE,orderId=ORDER_ONE,ms=NOW-30_000)=>({
    purchaseToken:token,orderId,voidedTimeMillis:String(ms),
    voidedReason:7,voidedSource:2
  });
  const lifetime=(token=TOKEN_ONE,sku='pro_lifetime')=>{
    const decision=evaluateOneTime({
      purchaseStateContext:{purchaseState:'PURCHASED'},
      productLineItem:[{productId:sku,productOfferDetails:{
        consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED'}}],
      obfuscatedExternalAccountId:owner,
      acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'
    },sku,owner);
    return ledger.verifiedClaim({user,token,serverDecision:decision});
  };
  const monthly=(token=TOKEN_TWO)=>{
    const decision=evaluateMonthly({
      subscriptionState:'SUBSCRIPTION_STATE_ACTIVE',
      lineItems:[{productId:'pro_monthly',expiryTime:new Date(NOW+DAY).toISOString(),autoRenewingPlan:{autoRenewEnabled:true},offerDetails:{basePlanId:'monthly'}}],
      externalAccountIdentifiers:{obfuscatedExternalAccountId:owner},
      acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'
    },NOW,owner);
    return ledger.verifiedClaim({user,token,serverDecision:decision});
  };
  const respond=value=>{pageResponder=value;};
  return {store,user,ledger,reader,worker,voided,lifetime,monthly,respond,calls,
    close:()=>store.close()};
}

test('Google Publisher void pagination is pinned to production app, type=1 and 30-day window',async()=>{
  const f=setup();
  try{
    f.respond(()=>({data:{voidedPurchases:[],tokenPagination:{nextPageToken:null}}}));
    const first=await f.reader.getVoided({
      fromMs:NOW-DAY,toMs:NOW,nowMs:NOW
    });
    assert.deepEqual(first,{items:[],nextPageToken:null});
    assert.equal(f.calls.length,1);
    let url=new URL(f.calls[0].url);
    assert.equal(url.origin,'https://androidpublisher.googleapis.com');
    assert.equal(url.pathname,
      '/androidpublisher/v3/applications/com.costavong.promptoverlay/purchases/voidedpurchases');
    assert.equal(url.searchParams.get('type'),'1');
    assert.equal(url.searchParams.get('startTime'),String(NOW-DAY));
    assert.equal(url.searchParams.get('endTime'),String(NOW));
    assert.equal(url.searchParams.get('includeQuantityBasedPartialRefund'),'true');
    await f.reader.getVoided({fromMs:NOW-DAY,toMs:NOW,nowMs:NOW,
      pageToken:'nextpage_2+='});
    url=new URL(f.calls.at(-1).url);
    assert.equal(url.searchParams.get('pageSelection.token'),'nextpage_2+=');
    assert.equal(url.searchParams.get('startTime'),null);
    assert.equal(url.searchParams.get('endTime'),null);
    assert.ok(f.calls.every(call=>call.method==='GET'));
  }finally{f.close();}
});

test('reject query gaps and untrusted response formats before publishing a checkpoint',async()=>{
  const f=setup();
  try{
    await assert.rejects(()=>f.reader.getVoided({
      fromMs:NOW-31*DAY,toMs:NOW,nowMs:NOW
    }),/last 30 days/);
    await assert.rejects(()=>f.reader.getVoided({
      fromMs:NOW-DAY,toMs:NOW+1,nowMs:NOW
    }),/last 30 days/);
    await assert.rejects(()=>f.reader.getVoided({
      fromMs:NOW-DAY,toMs:NOW,nowMs:NOW,pageToken:'malicious&x=1'
    }),/pagination token/);
    f.respond(()=>({data:{voidedPurchases:'wrong'}}));
    await assert.rejects(()=>f.worker.run(),/Invalid Google voided-purchases response/);
    assert.equal(f.ledger.lastVoidSync(),null);
  }finally{f.close();}
});

test('voided one-time purchase is revoked and cannot be restored from old receipt',async()=>{
  const f=setup();
  try{
    assert.equal(f.lifetime().saved,true);
    assert.equal(f.ledger.status(f.user.id).ownsProLifetime,true);
    f.respond(()=>({data:{voidedPurchases:[f.voided()]}}));
    const result=await f.worker.run();
    assert.equal(result.complete,true);
    assert.equal(result.newEvents,1);
    assert.equal(f.ledger.lastVoidSync(),NOW);
    assert.equal(f.ledger.status(f.user.id).ownsProLifetime,false);
    assert.equal(f.ledger.blockedReason(TOKEN_ONE),'REFUND_REVIEW_REQUIRED');
    assert.equal(f.store.db.prepare(
      'SELECT status FROM play_claims WHERE token_hash=?'
    ).get(hashValue(TOKEN_ONE)).status,'revoked');
    assert.equal(f.lifetime().reason,'REFUND_REVIEW_REQUIRED');
    const snapshot=JSON.stringify(f.store.db.prepare(
      'SELECT * FROM play_void_events').all());
    assert.ok(!snapshot.includes(TOKEN_ONE));
    assert.ok(!snapshot.includes(ORDER_ONE));
    assert.ok(snapshot.includes(hashValue(ORDER_ONE)));

    const again=await f.worker.run();
    assert.equal(again.newEvents,0);
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_void_events').get().n,1);
  }finally{f.close();}
});

test('void arriving before an account claim must block subsequent purchased receipt',async()=>{
  const f=setup();
  try{
    f.respond(()=>({data:{voidedPurchases:[f.voided(TOKEN_THREE)]}}));
    assert.equal((await f.worker.run()).newEvents,1);
    assert.equal(f.store.db.prepare(
      'SELECT claim_kind FROM play_refund_holds').get().claim_kind,'unknown');
    assert.equal(f.lifetime(TOKEN_THREE).reason,'REFUND_REVIEW_REQUIRED');
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_claims').get().n,0);
  }finally{f.close();}
});

test('refunded subscription renewal is held by token AND order, not irrevocably tombstoned',async()=>{
  const f=setup();
  try{
    assert.equal(f.monthly().saved,true);
    assert.equal(f.ledger.status(f.user.id).monthlyAccessUntilMs,NOW+DAY);
    f.respond(()=>({data:{voidedPurchases:[
      f.voided(TOKEN_TWO,'GPA.1234-5678-9012-34567..0'),
      f.voided(TOKEN_TWO,'GPA.1234-5678-9012-34567..1')
    ]}}));
    const result=await f.worker.run();
    assert.equal(result.newEvents,2);
    assert.equal(f.ledger.status(f.user.id).monthlyAccessUntilMs,0);
    assert.equal(f.store.db.prepare(
      'SELECT status FROM play_claims WHERE token_hash=?'
    ).get(hashValue(TOKEN_TWO)).status,'active',
      'Subscription token is shared by renewals; do not permanently revoke for one order');
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_refund_holds').get().n,1);
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_void_events').get().n,2);
    assert.equal(f.monthly().reason,'REFUND_REVIEW_REQUIRED');
  }finally{f.close();}
});

test('checkpoint advances ONLY after every page succeeds; retry is idempotent',async()=>{
  const f=setup();
  try{
    let failSecond=true;
    f.respond(req=>{
      const url=new URL(req.url);
      if(url.searchParams.has('pageSelection.token')){
        if(failSecond)throw new Error('Publisher 503');
        return {data:{voidedPurchases:[f.voided(TOKEN_THREE)],
          tokenPagination:{}}};
      }
      return {data:{voidedPurchases:[f.voided(TOKEN_ONE)],
        tokenPagination:{nextPageToken:'page2'}}};
    });
    await assert.rejects(()=>f.worker.run(),/Publisher 503/);
    assert.equal(f.ledger.lastVoidSync(),null);
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_void_events').get().n,1);
    failSecond=false;
    const result=await f.worker.run();
    assert.equal(result.pages,2);
    assert.equal(result.seen,2);
    assert.equal(result.newEvents,1);
    assert.equal(f.ledger.lastVoidSync(),NOW);
    assert.equal(f.store.db.prepare(
      'SELECT COUNT(*) AS n FROM play_void_events').get().n,2);
  }finally{f.close();}
});

test('overlapping 24h query window covers delayed notifications',async()=>{
  let clock=NOW;
  const f=setup(()=>clock);
  try{
    f.respond(()=>({data:{voidedPurchases:[]}}));
    await f.worker.run();
    const oldCheckpoint=f.ledger.lastVoidSync();
    clock+=2*60*60*1000;
    await f.worker.run();
    assert.equal(f.ledger.lastVoidSync(),clock);
    const last=new URL(f.calls.at(-1).url);
    assert.equal(Number(last.searchParams.get('startTime')),oldCheckpoint-DAY);
    assert.equal(Number(last.searchParams.get('endTime')),clock);
  }finally{f.close();}
});

test('if scheduled reconciliation stopped for over 28 days, refuse silent truncated scan',async()=>{
  let clock=NOW;
  const f=setup(()=>clock);
  try{
    f.respond(()=>({data:{voidedPurchases:[]}}));
    await f.worker.run();
    const initialCalls=f.calls.length;
    clock+=29*DAY;
    await assert.rejects(()=>f.worker.run(),/VOID_SYNC_GAP_REQUIRES_REVIEW/);
    assert.equal(f.calls.length,initialCalls);
    assert.equal(f.ledger.lastVoidSync(),NOW);
  }finally{f.close();}
});

test('reject malformed void records and repeated page tokens without checkpoint advancement',async()=>{
  const f=setup();
  try{
    f.respond(()=>({data:{
      voidedPurchases:[f.voided(TOKEN_ONE,ORDER_ONE,NOW+60*60*1000)]
    }}));
    await assert.rejects(()=>f.worker.run(),/Invalid voided event timestamp/);
    assert.equal(f.ledger.lastVoidSync(),null);

    f.respond(()=>({data:{voidedPurchases:[],
      tokenPagination:{nextPageToken:'repeat-me'}}}));
    await assert.rejects(()=>f.worker.run(),/INVALID_GOOGLE_VOID_PAGINATION/);
    assert.equal(f.ledger.lastVoidSync(),null);
  }finally{f.close();}
});
