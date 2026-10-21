import {test} from 'node:test';
import assert from 'node:assert/strict';
import {hashValue} from '../src/store.js';
import {
  evaluateOneTime,evaluateMonthly,expectedObfuscatedAccountId,
  GOOGLE_PLAY_PACKAGE,mayGrantFromGoogleReceipt
} from '../src/play-purchase-policy.js';

const account='4e513b0d-bfd1-42b7-a1d9-e3c18101f2c4';
const otherAccount='87eb3f45-e4d7-4576-95cf-f741e87b6932';
const bound=expectedObfuscatedAccountId(account,hashValue);
const otherBound=expectedObfuscatedAccountId(otherAccount,hashValue);
const NOW=2_100_000_000_000;

const lifetime=(product='pro_lifetime',state='PURCHASED',owner=bound,ack='ACKNOWLEDGEMENT_STATE_PENDING')=>({
  purchaseStateContext:{purchaseState:state},
  productLineItem:[{productId:product,productOfferDetails:{
    consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED'}}],
  obfuscatedExternalAccountId:owner,
  acknowledgementState:ack
});

const monthly=(state='SUBSCRIPTION_STATE_ACTIVE',owner=bound,expiry=new Date(NOW+86400000).toISOString())=>({
  subscriptionState:state,
  externalAccountIdentifiers:{obfuscatedExternalAccountId:owner},
  acknowledgementState:'ACKNOWLEDGEMENT_STATE_PENDING',
  lineItems:[{productId:'pro_monthly',expiryTime:expiry}]
});

test('Google Play package and per-user billing binding are unique',()=>{
  assert.equal(GOOGLE_PLAY_PACKAGE,'com.costavong.promptoverlay');
  assert.equal(bound.length,64);
  assert.notEqual(bound,otherBound);
  assert.equal(bound,expectedObfuscatedAccountId(account,hashValue));
  assert.throws(()=>expectedObfuscatedAccountId('fake-email@example.com',hashValue));
});

test('verified one-time Basic and Pro purchases require account matching',()=>{
  for(const sku of ['basic_lifetime','pro_lifetime']){
    const approved=evaluateOneTime(lifetime(sku),sku,bound);
    assert.equal(approved.approved,true);
    assert.equal(approved.needsAcknowledgement,true);
    assert.equal(approved.kind,'lifetime');
    assert.equal(mayGrantFromGoogleReceipt(approved),true);
    assert.equal(evaluateOneTime(lifetime(sku),'fake_premium',bound).approved,false);
    assert.equal(evaluateOneTime(lifetime(sku),sku,otherBound).reason,'ACCOUNT_MISMATCH');
  }
});

test('pending, canceled, consumed, mismatched and unbound products do not grant',()=>{
  const cases=[
    [lifetime('pro_lifetime','PENDING'),'NOT_PURCHASED'],
    [lifetime('pro_lifetime','CANCELLED'),'NOT_PURCHASED'],
    [lifetime('basic_lifetime'),'PRODUCT_MISMATCH'],
    [lifetime('pro_lifetime','PURCHASED',null),'ACCOUNT_MISMATCH'],
    [lifetime('pro_lifetime','PURCHASED',otherBound),'ACCOUNT_MISMATCH'],
    [{...lifetime(),acknowledgementState:'bad'},'UNKNOWN_ACKNOWLEDGEMENT_STATE'],
    [{...lifetime(),productLineItem:[]},'PRODUCT_MISMATCH'],
    [{...lifetime(),productLineItem:[lifetime().productLineItem[0],{productId:'unknown'}]},'PRODUCT_MISMATCH'],
    [{...lifetime(),productLineItem:[{
      productId:'pro_lifetime',productOfferDetails:{consumptionState:'CONSUMPTION_STATE_CONSUMED'}
    }]},'CONSUMED_PURCHASE']
  ];
  for(const [receipt,reason] of cases){
    const result=evaluateOneTime(receipt,'pro_lifetime',bound);
    assert.equal(result.reason,reason);
    assert.equal(mayGrantFromGoogleReceipt(result),false);
  }
  assert.equal(mayGrantFromGoogleReceipt({productId:'pro_lifetime',kind:'lifetime'}),false);
});

test('prepaid cancellation retains access until exact Google expiry',()=>{
  for(const state of ['SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_CANCELED','SUBSCRIPTION_STATE_IN_GRACE_PERIOD']){
    const result=evaluateMonthly(monthly(state),NOW,bound);
    assert.equal(result.approved,true);
    assert.equal(result.expiresAtMs,NOW+86400000);
    assert.equal(result.needsAcknowledgement,true);
  }
  assert.equal(evaluateMonthly(monthly('SUBSCRIPTION_STATE_CANCELED',bound,new Date(NOW).toISOString()),NOW,bound).reason,'SUBSCRIPTION_EXPIRED');
});

test('subscription on hold, paused, expired, pending or wrong account always denied',()=>{
  for(const state of [
    'SUBSCRIPTION_STATE_ON_HOLD','SUBSCRIPTION_STATE_PAUSED',
    'SUBSCRIPTION_STATE_PENDING','SUBSCRIPTION_STATE_EXPIRED',
    'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED','UNKNOWN'
  ]){
    const result=evaluateMonthly(monthly(state),NOW,bound);
    assert.equal(result.approved,false,state);
    assert.equal(result.reason,'SUBSCRIPTION_NOT_ENTITLED');
  }
  assert.equal(evaluateMonthly(monthly('SUBSCRIPTION_STATE_UNSPECIFIED',otherBound),NOW,bound).reason,'SUBSCRIPTION_NOT_ENTITLED');
  assert.equal(evaluateMonthly(monthly('SUBSCRIPTION_STATE_ACTIVE',otherBound),NOW,bound).reason,'ACCOUNT_MISMATCH');
  assert.equal(evaluateMonthly(monthly('SUBSCRIPTION_STATE_ACTIVE',null),NOW,bound).reason,'ACCOUNT_MISMATCH');
});

test('monthly validation rejects malformed items and acknowledgments',()=>{
  const m=monthly();
  assert.equal(evaluateMonthly({...m,lineItems:[]},NOW,bound).reason,'PRODUCT_MISMATCH');
  assert.equal(evaluateMonthly({...m,lineItems:[{productId:'other',expiryTime:new Date(NOW+10).toISOString()}]},NOW,bound).reason,'PRODUCT_MISMATCH');
  assert.equal(evaluateMonthly({...m,lineItems:[m.lineItems[0],m.lineItems[0]]},NOW,bound).reason,'PRODUCT_MISMATCH');
  assert.equal(evaluateMonthly({...m,lineItems:[{productId:'pro_monthly',expiryTime:'not-a-date'}]},NOW,bound).reason,'SUBSCRIPTION_EXPIRED');
  assert.equal(evaluateMonthly({...m,acknowledgementState:'UNKNOWN'},NOW,bound).reason,'UNKNOWN_ACKNOWLEDGEMENT_STATE');
  assert.equal(evaluateMonthly(m,NaN,bound).reason,'INVALID_RESPONSE');
});

test('nothing client-only can bypass server receipt verification',()=>{
  assert.equal(mayGrantFromGoogleReceipt(null),false);
  assert.equal(mayGrantFromGoogleReceipt({approved:true,kind:'other'}),false);
  assert.equal(mayGrantFromGoogleReceipt({approved:true,kind:'lifetime',productId:'pro_lifetime'}),false);
  assert.equal(mayGrantFromGoogleReceipt({approved:true,kind:'monthly',expiresAtMs:NOW+86400000}),false);
  assert.equal(mayGrantFromGoogleReceipt({approved:false,kind:'monthly'}),false);
  assert.equal(evaluateOneTime({isPurchased:true,productId:'pro_lifetime'},'pro_lifetime',bound).approved,false);
  assert.equal(evaluateMonthly({subscriptionState:'SUBSCRIPTION_STATE_ACTIVE'},NOW,bound).approved,false);
});

test('configured one-time Buy options cannot be replaced by a rental, preorder or wrong offer',()=>{
  for(const [sku,expected] of [
    ['basic_lifetime','basic-buy'],
    ['pro_lifetime','pro-buy']
  ]){
    const ok=lifetime(sku);
    ok.productLineItem[0].productOfferDetails.purchaseOptionId=expected;
    assert.equal(evaluateOneTime(ok,sku,bound).approved,true);
    const wrong=structuredClone(ok);
    wrong.productLineItem[0].productOfferDetails.purchaseOptionId='wrong-buy';
    assert.equal(evaluateOneTime(wrong,sku,bound).reason,'PURCHASE_OPTION_MISMATCH');
    const rental=structuredClone(ok);
    rental.productLineItem[0].productOfferDetails.rentOfferDetails={};
    assert.equal(evaluateOneTime(rental,sku,bound).reason,'NON_LIFETIME_PURCHASE');
    const preorder=structuredClone(ok);
    preorder.productLineItem[0].productOfferDetails.preorderOfferDetails={};
    assert.equal(evaluateOneTime(preorder,sku,bound).reason,'NON_LIFETIME_PURCHASE');
  }
});

test('only regular monthly auto-renewing base plan is eligible',()=>{
  const ok=monthly();
  ok.lineItems[0].offerDetails={basePlanId:'monthly'};
  assert.equal(evaluateMonthly(ok,NOW,bound).approved,true);
  const prepaid=structuredClone(ok);
  prepaid.lineItems[0].prepaidPlan={allowExtendAfterTime:'never'};
  assert.equal(evaluateMonthly(prepaid,NOW,bound).reason,'PREPAID_NOT_SUPPORTED');
  const wrong=structuredClone(ok);
  wrong.lineItems[0].offerDetails.basePlanId='annual';
  assert.equal(evaluateMonthly(wrong,NOW,bound).reason,'BASE_PLAN_MISMATCH');
});
