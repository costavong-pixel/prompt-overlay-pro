import {test} from 'node:test';
import assert from 'node:assert/strict';
import {auditPlayCatalog,PlayCatalogReadOnlyClient} from '../src/play-catalog-readiness.js';

const us=price=>({
  regionCode:'US',
  price:{currencyCode:'USD',units:String(Math.floor(price)),
    nanos:Math.round((price-Math.floor(price))*1e9)}
});
const product=(productId,optionId,usdPrice,state='DRAFT')=>({
  packageName:'com.costavong.promptoverlay',
  productId,
  purchaseOptions:[{purchaseOptionId:optionId,state,buyOption:{},
    regionalPricingAndAvailabilityConfigs:[us(usdPrice)]}]
});
const subscription=(billingPeriodDuration='P1M',state='DRAFT')=>({
  packageName:'com.costavong.promptoverlay',productId:'pro_monthly',
  basePlans:[{basePlanId:'monthly',state,
    autoRenewingBasePlanType:{billingPeriodDuration},
    regionalConfigs:[us(4.99)]}]
});
const catalog=()=>[
  [product('basic_lifetime','basic-buy',5.99),
   product('pro_lifetime','pro-buy',39.99)],
  [subscription()]
];

test('all three configured draft products are recognized without enabling payments',()=>{
  const [once,subs]=catalog();
  const report=auditPlayCatalog(once,subs);
  assert.equal(report.packageName,'com.costavong.promptoverlay');
  assert.deepEqual(report.checkedAgainstExpectedIds,
    ['basic_lifetime','pro_lifetime','pro_monthly']);
  assert.equal(report.matchesConfiguration,true);
  assert.equal(report.allProductsDraft,true);
  assert.equal(report.anyProductActive,false);
  assert.equal(report.paymentsEnabledByThisTool,false);
  assert.equal(report.checkoutEnabledByThisTool,false);
});

test('incorrect purchase option or regional price is detected',()=>{
  const [once,subs]=catalog();
  once[0].purchaseOptions[0].purchaseOptionId='basic-lifetime-buy';
  let report=auditPlayCatalog(once,subs);
  assert.equal(report.matchesConfiguration,false);
  assert.equal(report.products[0].optionFound,false);
  once[0].purchaseOptions[0].purchaseOptionId='basic-buy';
  once[1].purchaseOptions[0].regionalPricingAndAvailabilityConfigs=[us(49.99)];
  report=auditPlayCatalog(once,subs);
  assert.equal(report.matchesConfiguration,false);
  assert.equal(report.products[1].usPriceMatches,false);
});

test('monthly base plan must renew once per calendar month',()=>{
  const [once,subs]=catalog();
  subs[0].basePlans[0].autoRenewingBasePlanType.billingPeriodDuration='P4W';
  const report=auditPlayCatalog(once,subs);
  assert.equal(report.matchesConfiguration,false);
  assert.equal(report.products[2].renewsMonthly,false);
});

test('wrong app, rental option, duplicate SKU and missing record fail closed',()=>{
  const [once,subs]=catalog();
  once[0].packageName='com.someone.else';
  assert.equal(auditPlayCatalog(once,subs).matchesConfiguration,false);
  once[0].packageName='com.costavong.promptoverlay';
  delete once[0].purchaseOptions[0].buyOption;
  once[0].purchaseOptions[0].rentOption={};
  assert.equal(auditPlayCatalog(once,subs).products[0].optionFound,false);
  once.splice(0,1);
  assert.equal(auditPlayCatalog(once,subs).products[0].reason,'NOT_FOUND');
  once.push({...once[0]});
  assert.equal(auditPlayCatalog(once,subs).products[1].reason,'DUPLICATE_PRODUCT');
});

test('active product status is reported, never auto-activated',()=>{
  const [once,subs]=catalog();
  once[0].purchaseOptions[0].state='ACTIVE';
  const report=auditPlayCatalog(once,subs);
  assert.equal(report.anyProductActive,true);
  assert.equal(report.allProductsDraft,false);
  assert.equal(report.paymentsEnabledByThisTool,false);
  assert.equal(once[0].purchaseOptions[0].state,'ACTIVE');
});

test('read-only requests pin product and subscription catalog URLs',async()=>{
  const calls=[];
  const [once,subs]=catalog();
  const api=new PlayCatalogReadOnlyClient({request:async options=>{
    calls.push(options);
    return {data:options.url.includes('oneTimeProducts')
      ?{oneTimeProducts:once}:{subscriptions:subs}};
  }});
  const report=await api.audit();
  assert.equal(report.matchesConfiguration,true);
  assert.equal(calls.length,2);
  assert.ok(calls.every(x=>x.method==='GET'&&x.timeout===12000));
  assert.deepEqual(calls.map(x=>x.url).sort(),[
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.costavong.promptoverlay/oneTimeProducts?pageSize=50',
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.costavong.promptoverlay/subscriptions?pageSize=50'
  ]);
});

test('pagination includes later catalog entries safely',async()=>{
  const [once,subs]=catalog();
  const calls=[];
  const api=new PlayCatalogReadOnlyClient({request:async options=>{
    calls.push(options.url);
    if(options.url.includes('oneTimeProducts')){
      if(options.url.includes('pageToken=next'))return {data:{oneTimeProducts:[once[1]]}};
      return {data:{oneTimeProducts:[once[0]],nextPageToken:'next'}};
    }
    return {data:{subscriptions:subs}};
  }});
  assert.equal((await api.audit()).matchesConfiguration,true);
  assert.ok(calls.some(x=>x.endsWith('pageToken=next')));
});

test('invalid catalog responses and repeated page token are never successful',async()=>{
  const repeated=new PlayCatalogReadOnlyClient({request:async()=>({
    data:{oneTimeProducts:[],nextPageToken:'same'}})});
  await assert.rejects(()=>repeated.audit(),/repeated pagination token/);
  const bad=new PlayCatalogReadOnlyClient({request:async()=>({data:'invalid'})});
  await assert.rejects(()=>bad.audit(),/response invalid/);
  assert.throws(()=>new PlayCatalogReadOnlyClient({}),/authenticated/);
  assert.throws(()=>auditPlayCatalog(null,[]),/required/);
});
