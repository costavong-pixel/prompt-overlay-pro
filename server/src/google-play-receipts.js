import {GoogleAuth} from 'google-auth-library';
import {GOOGLE_PLAY_PACKAGE} from './play-purchase-policy.js';

const PUBLISHER_SCOPE='https://www.googleapis.com/auth/androidpublisher';
const API_ORIGIN='https://androidpublisher.googleapis.com';
const API_PREFIX='/androidpublisher/v3/applications/';

/**
 * Authenticated Google Publisher purchase reader and optional server-side
 * acknowledgement. It does NOT grant access, refund, consume purchases, or
 * mutate the database. Only a validated purchase-service may call
 * acknowledge(). Neither Android nor anonymous HTTP can call this directly.
 *
 * Production activation requires a separately authorized service identity,
 * product configuration, token ownership controls, RTDN/voided-purchase
 * reconciliation, and audited rollback.
 */
export class GooglePlayReceiptReader {
  constructor({googleClient,packageName=GOOGLE_PLAY_PACKAGE}={}){
    if(packageName!==GOOGLE_PLAY_PACKAGE)
      throw new Error('Only the production Google Play package is supported');
    if(!googleClient||typeof googleClient.request!=='function')
      throw new TypeError('Authenticated Google Play API client required');
    this.googleClient=googleClient;
    this.packageName=packageName;
  }

  static async fromServerApplicationDefaultCredentials(){
    // Caller must explicitly supply secure ambient credentials; GoogleAuth
    // selects a host-provided service identity. No credential bytes or keys
    // belong in a checkout request, repo, or Android app.
    const auth=new GoogleAuth({scopes:[PUBLISHER_SCOPE]});
    const client=await auth.getClient();
    return new GooglePlayReceiptReader({googleClient:client});
  }

  async getOneTime(token){
    return this.get('productsv2',token);
  }

  async getSubscription(token){
    return this.get('subscriptionsv2',token);
  }

  /**
   * Query both in-app and subscription voids from Google's authenticated
   * Publisher API. Pagination must use pageSelection.token; Google ignores
   * the start/end filters on subsequent pages. No tokens stored or logged.
   */
  async getVoided({fromMs,toMs,pageToken=null,nowMs=Date.now()}={}){
    if(!Number.isSafeInteger(nowMs)||!Number.isSafeInteger(fromMs)||
        !Number.isSafeInteger(toMs)||fromMs<=0||fromMs>toMs||
        toMs>nowMs||fromMs<nowMs-30*24*60*60*1000)
      throw new TypeError('Voided purchase query must be within last 30 days');
    if(pageToken!==null&&(typeof pageToken!=='string'||
        pageToken.length<1||pageToken.length>2048||
        !/^[A-Za-z0-9_+=/.-]+$/.test(pageToken)))
      throw new TypeError('Invalid Google pagination token');

    const query=new URLSearchParams();
    query.set('type','1'); // include subscriptions AND one-time purchases
    query.set('pageSelection.maxResults','100');
    query.set('includeQuantityBasedPartialRefund','true');
    if(pageToken){
      query.set('pageSelection.token',pageToken);
    }else{
      query.set('startTime',String(fromMs));
      query.set('endTime',String(toMs));
    }
    const url=API_ORIGIN+API_PREFIX+GOOGLE_PLAY_PACKAGE+
      '/purchases/voidedpurchases?'+query.toString();
    const result=await this.googleClient.request({
      url,method:'GET',timeout:12000,headers:{Accept:'application/json'}
    });
    const data=result?.data;
    if(!data||typeof data!=='object'||Array.isArray(data)||
        (data.voidedPurchases!==undefined&&!Array.isArray(data.voidedPurchases)))
      throw new Error('Invalid Google voided-purchases response');
    const next=data.tokenPagination?.nextPageToken??null;
    if(next!==null&&(typeof next!=='string'||next.length<1||next.length>2048))
      throw new Error('Invalid Google voided-purchases pagination');
    return {items:data.voidedPurchases??[],nextPageToken:next};
  }

  static requireValidToken(token){
    if(typeof token!=='string'||token.length<20||token.length>4096||
        !/^[a-zA-Z0-9._:-]+$/.test(token))
      throw new Error('Invalid Play purchase token');
  }

  /** An authenticated acknowledgement never grants entitlements itself. */
  async acknowledge({type,productId,token}){
    if(type!=='inapp'&&type!=='subs')
      throw new Error('Unsupported product type for acknowledgement');
    if(!['basic_lifetime','pro_lifetime','pro_monthly'].includes(productId))
      throw new Error('Unknown Play product');
    if((type==='subs')!==(productId==='pro_monthly'))
      throw new Error('Product type does not match ID');
    GooglePlayReceiptReader.requireValidToken(token);
    const base=API_ORIGIN+API_PREFIX+GOOGLE_PLAY_PACKAGE+'/purchases/';
    const path=type==='inapp'
      ?'products/'+encodeURIComponent(productId)+'/tokens/'+encodeURIComponent(token)
      :'subscriptions/'+encodeURIComponent(productId)+'/tokens/'+encodeURIComponent(token);
    await this.googleClient.request({
      url:base+path+':acknowledge',method:'POST',data:{},
      timeout:12000,headers:{'Content-Type':'application/json'}
    });
  }

  async get(type,token){
    if(type!=='productsv2'&&type!=='subscriptionsv2')
      throw new Error('Unsupported Play purchase resource');
    GooglePlayReceiptReader.requireValidToken(token);
    // Never interpolate a caller-controlled hostname or package ID.
    const url=API_ORIGIN+API_PREFIX+GOOGLE_PLAY_PACKAGE+
      '/purchases/'+type+'/tokens/'+encodeURIComponent(token);
    const response=await this.googleClient.request({
      url,method:'GET',timeout:12000,headers:{Accept:'application/json'}
    });
    if(!response||!response.data||typeof response.data!=='object')
      throw new Error('Invalid Google Publisher response');
    return response.data;
  }
}
