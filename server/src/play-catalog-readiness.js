import {GOOGLE_PLAY_PACKAGE} from './play-purchase-policy.js';

const ORIGIN='https://androidpublisher.googleapis.com/androidpublisher/v3/applications/';
const EXPECTED=Object.freeze({
  basic_lifetime:Object.freeze({purchaseOptionId:'basic-buy',usdPrice:5.99}),
  pro_lifetime:Object.freeze({purchaseOptionId:'pro-buy',usdPrice:39.99}),
  pro_monthly:Object.freeze({basePlanId:'monthly',usdPrice:4.99})
});

function findUSPrice(configs){
  if(!Array.isArray(configs))return null;
  const row=configs.find(x=>x?.regionCode==='US');
  const price=row?.price;
  if(!price||price.currencyCode!=='USD')return null;
  const units=Number(price.units||0),nanos=Number(price.nanos||0);
  if(!Number.isSafeInteger(units)||!Number.isSafeInteger(nanos)
    ||nanos<0||nanos>=1000000000)return null;
  return Math.round((units+nanos/1e9)*100)/100;
}

function summarizeInapp(items,id){
  const cfg=EXPECTED[id];
  const found=items.filter(x=>x?.productId===id);
  if(found.length!==1)return {id,present:false,optionFound:false,
    usPriceMatches:false,state:null,reason:found.length===0?'NOT_FOUND':'DUPLICATE_PRODUCT'};
  const product=found[0];
  const matching=(product.purchaseOptions||[]).filter(
    opt=>opt?.purchaseOptionId===cfg.purchaseOptionId&&
      Object.prototype.hasOwnProperty.call(opt,'buyOption')
  );
  if(product.packageName&&product.packageName!==GOOGLE_PLAY_PACKAGE)
    return {id,present:false,optionFound:false,usPriceMatches:false,
      state:null,reason:'PACKAGE_MISMATCH'};
  const opt=matching.length===1?matching[0]:null;
  return {id,present:true,optionFound:Boolean(opt),
    usPriceMatches:Boolean(opt)&&findUSPrice(
      opt.regionalPricingAndAvailabilityConfigs)===cfg.usdPrice,
    state:opt?.state||null,reason:opt?'FOUND':'BUY_OPTION_MISSING'};
}

function summarizeMonthly(items){
  const id='pro_monthly',cfg=EXPECTED[id];
  const found=items.filter(x=>x?.productId===id);
  if(found.length!==1)return {id,present:false,basePlanFound:false,
    renewsMonthly:false,usPriceMatches:false,state:null,
    reason:found.length===0?'NOT_FOUND':'DUPLICATE_PRODUCT'};
  const product=found[0];
  if(product.packageName&&product.packageName!==GOOGLE_PLAY_PACKAGE)
    return {id,present:false,basePlanFound:false,renewsMonthly:false,
      usPriceMatches:false,state:null,reason:'PACKAGE_MISMATCH'};
  const matching=(product.basePlans||[]).filter(
    x=>x?.basePlanId===cfg.basePlanId);
  const plan=matching.length===1?matching[0]:null;
  return {id,present:true,basePlanFound:Boolean(plan),
    renewsMonthly:plan?.autoRenewingBasePlanType?.billingPeriodDuration==='P1M',
    usPriceMatches:Boolean(plan)&&findUSPrice(plan.regionalConfigs)===cfg.usdPrice,
    state:plan?.state||null,reason:plan?'FOUND':'MONTHLY_BASE_PLAN_MISSING'};
}

/**
 * Summarize the trusted READ-only catalog responses. Output contains no
 * credentials, customer identities, transactions or arbitrary product data.
 * A complete catalog does NOT certify that the app is safe to sell yet.
 */
export function auditPlayCatalog(oneTimeProducts,subscriptions){
  if(!Array.isArray(oneTimeProducts)||!Array.isArray(subscriptions))
    throw new TypeError('Verified Google Play catalog lists required');
  const basic=summarizeInapp(oneTimeProducts,'basic_lifetime');
  const lifetime=summarizeInapp(oneTimeProducts,'pro_lifetime');
  const monthly=summarizeMonthly(subscriptions);
  const products=[basic,lifetime,monthly];
  const matchesConfiguration=basic.optionFound&&basic.usPriceMatches&&
    lifetime.optionFound&&lifetime.usPriceMatches&&
    monthly.basePlanFound&&monthly.renewsMonthly&&monthly.usPriceMatches;
  // Explicitly do NOT mislabel draft products as live or activate anything.
  return Object.freeze({
    packageName:GOOGLE_PLAY_PACKAGE,
    checkedAgainstExpectedIds:Object.keys(EXPECTED),
    products,
    matchesConfiguration:Boolean(matchesConfiguration),
    allProductsDraft:products.every(x=>x.state==='DRAFT'),
    anyProductActive:products.some(x=>x.state==='ACTIVE'),
    paymentsEnabledByThisTool:false,
    checkoutEnabledByThisTool:false
  });
}

/**
 * Uses the existing authenticated Google Play service account via GoogleAuth.
 * GET-only, never publishes, activates, acknowledges or changes anything.
 * The caller is responsible for credentials; none are created/downloaded.
 */
export class PlayCatalogReadOnlyClient {
  constructor(googleClient){
    if(typeof googleClient?.request!=='function')
      throw new TypeError('Authenticated Google Publisher client required');
    this.googleClient=googleClient;
  }

  async readAll(collection,itemsField){
    if(!['oneTimeProducts','subscriptions'].includes(collection))
      throw new Error('Catalog endpoint not allowlisted');
    const collected=[];
    const usedPageTokens=new Set();
    let pageToken='';
    for(let page=0;page<20;page++){
      let url=ORIGIN+GOOGLE_PLAY_PACKAGE+'/'+collection+'?pageSize=50';
      if(pageToken)url+='&pageToken='+encodeURIComponent(pageToken);
      const result=await this.googleClient.request({
        url,method:'GET',timeout:12000,headers:{Accept:'application/json'}
      });
      if(!result?.data||typeof result.data!=='object'||Array.isArray(result.data))
        throw new Error('Google Publisher catalog response invalid');
      // Google's JSON responses may omit an empty collection altogether.
      const rows=result.data[itemsField]??[];
      if(!Array.isArray(rows))
        throw new Error('Google Publisher catalog response invalid');
      collected.push(...rows);
      if(collected.length>1000)throw new Error('Unexpectedly large catalog');
      const next=result.data.nextPageToken;
      if(next==null||next==='')return collected;
      if(typeof next!=='string'||next.length>2048||usedPageTokens.has(next))
        throw new Error('Invalid or repeated pagination token');
      usedPageTokens.add(next);
      pageToken=next;
    }
    throw new Error('Google Publisher catalog pagination limit exceeded');
  }

  async audit(){
    const [oneTimeProducts,subscriptions]=await Promise.all([
      this.readAll('oneTimeProducts','oneTimeProducts'),
      this.readAll('subscriptions','subscriptions')
    ]);
    return auditPlayCatalog(oneTimeProducts,subscriptions);
  }
}
