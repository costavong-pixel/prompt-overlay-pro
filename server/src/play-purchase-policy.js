import {timingSafeEqual} from 'node:crypto';

/**
 * Purchase decision engine for responses fetched BY THE SERVER from Google's
 * authenticated Android Publisher API. Never evaluate client-supplied JSON.
 *
 * This is deliberately not wired into the live API. Until refund / voided
 * purchase notification handling, purchase binding, and acknowledgment are
 * reviewed, the existing /v1/purchases/verify endpoint stays HTTP 503.
 */
const SERVER_CHECKED_DECISION=Symbol('server-verified-google-play-decision');
const approve=(properties)=>Object.freeze({
  ...properties,approved:true,[SERVER_CHECKED_DECISION]:true
});

const PRODUCTS=Object.freeze({
  basic_lifetime:'inapp',
  pro_lifetime:'inapp',
  pro_monthly:'subs'
});

export const GOOGLE_PLAY_PACKAGE='com.costavong.promptoverlay';
export const KNOWN_PRODUCTS=PRODUCTS;

export function expectedObfuscatedAccountId(accountId,hashFn){
  if(typeof accountId!=='string'||!/^[0-9a-f-]{36}$/i.test(accountId))
    throw new TypeError('Verified internal account ID required');
  if(typeof hashFn!=='function')throw new TypeError('Server hash function required');
  // App-specific and private random database UUID; never use e-mail, raw
  // Google user IDs, or customer-entered information as a billing identifier.
  return hashFn('prompt-overlay-v1:'+accountId);
}

function reject(reason){return {approved:false,reason};}
function isExpectedBoundAccount(raw,expected){
  // Both values are fixed-width hex SHA-256 hashes. Avoid shortcuts based on
  // the first mismatching byte in an authenticated purchase-claim comparison.
  if(typeof expected!=='string'||typeof raw!=='string'||
      !/^[a-f0-9]{64}$/.test(expected)||!/^[a-f0-9]{64}$/.test(raw))
    return false;
  return timingSafeEqual(Buffer.from(raw,'hex'),Buffer.from(expected,'hex'));
}

/**
 * @param response JSON from publisher purchases.productsv2.getproductpurchasev2
 * @param productId strictly the product ID requested by the app
 * @param obfuscatedAccountId derived from the server account UUID
 */
export function evaluateOneTime(response,productId,obfuscatedAccountId){
  if(!response||typeof response!=='object'||PRODUCTS[productId]!=='inapp')
    return reject('INVALID_PRODUCT');
  if(response.purchaseStateContext?.purchaseState!=='PURCHASED')
    return reject('NOT_PURCHASED');
  const rows=response.productLineItem;
  if(!Array.isArray(rows)||rows.length!==1||rows[0]?.productId!==productId)
    return reject('PRODUCT_MISMATCH');
  if(!isExpectedBoundAccount(response.obfuscatedExternalAccountId,obfuscatedAccountId))
    return reject('ACCOUNT_MISMATCH');
  const details=rows[0]?.productOfferDetails;
  if(!details||typeof details!=='object')
    return reject('INVALID_PURCHASE_OPTION');
  // The actual purchase option is part of Google's signed purchase state,
  // not an arbitrary label sent by Android.
  const expectedOption=productId==='basic_lifetime'?'basic-buy':'pro-buy';
  if(details.purchaseOptionId!=null&&
      details.purchaseOptionId!==expectedOption)
    return reject('PURCHASE_OPTION_MISMATCH');
  // A permanent license cannot be provided by a rental or preorder.
  if(details.rentOfferDetails!=null||details.preorderOfferDetails!=null)
    return reject('NON_LIFETIME_PURCHASE');
  if(details.consumptionState!=='CONSUMPTION_STATE_YET_TO_BE_CONSUMED')
    return reject('CONSUMED_PURCHASE');
  const ack=response.acknowledgementState;
  if(!['ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED','ACKNOWLEDGEMENT_STATE_PENDING'].includes(ack))
    return reject('UNKNOWN_ACKNOWLEDGEMENT_STATE');
  return approve({
    productId,kind:'lifetime',
    needsAcknowledgement:ack==='ACKNOWLEDGEMENT_STATE_PENDING'
  });
}

const ENTITLED_SUBSCRIPTION_STATES=new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  // CANCELED means auto-renewal has been stopped, but the user retains the
  // prepaid subscription until the verified item expiration time.
  'SUBSCRIPTION_STATE_CANCELED'
]);

/**
 * @param response JSON from publisher purchases.subscriptionsv2.get
 * @param nowMs server time (never phone time)
 * @param obfuscatedAccountId derived from server account UUID
 */
export function evaluateMonthly(response,nowMs,obfuscatedAccountId){
  if(!Number.isSafeInteger(nowMs)||nowMs<=0||!response||typeof response!=='object')
    return reject('INVALID_RESPONSE');
  if(!ENTITLED_SUBSCRIPTION_STATES.has(response.subscriptionState))
    return reject('SUBSCRIPTION_NOT_ENTITLED');
  if(!isExpectedBoundAccount(response.externalAccountIdentifiers?.obfuscatedExternalAccountId,
       obfuscatedAccountId))
    return reject('ACCOUNT_MISMATCH');
  const items=response.lineItems;
  if(!Array.isArray(items)||items.length!==1||items[0]?.productId!=='pro_monthly')
    return reject('PRODUCT_MISMATCH');
  // Only this app's standard monthly auto-renewing base plan is supported.
  // Never silently convert a prepaid plan or an unrelated offer to Pro.
  if(items[0]?.prepaidPlan!=null)return reject('PREPAID_NOT_SUPPORTED');
  if(!items[0]?.autoRenewingPlan||
      typeof items[0].autoRenewingPlan!=='object')
    return reject('AUTO_RENEWING_PLAN_REQUIRED');
  if(items[0]?.offerDetails?.basePlanId!=null&&
      items[0].offerDetails.basePlanId!=='monthly')
    return reject('BASE_PLAN_MISMATCH');
  const expires=Date.parse(items[0]?.expiryTime||'');
  if(!Number.isFinite(expires)||!Number.isSafeInteger(expires)||expires<=nowMs)
    return reject('SUBSCRIPTION_EXPIRED');
  const ack=response.acknowledgementState;
  if(!['ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED','ACKNOWLEDGEMENT_STATE_PENDING'].includes(ack))
    return reject('UNKNOWN_ACKNOWLEDGEMENT_STATE');
  return approve({
    productId:'pro_monthly',kind:'monthly',
    expiresAtMs:expires,
    needsAcknowledgement:ack==='ACKNOWLEDGEMENT_STATE_PENDING'
  });
}

/**
 * This is the only acceptable transition for trial/paid access. A Google Play
 * purchase queried locally on the phone never suffices to grant access.
 */
export function mayGrantFromGoogleReceipt(verifiedServerDecision){
  return verifiedServerDecision!=null&&
    verifiedServerDecision[SERVER_CHECKED_DECISION]===true&&
    verifiedServerDecision.approved===true&&
    ['lifetime','monthly'].includes(verifiedServerDecision.kind);
}
