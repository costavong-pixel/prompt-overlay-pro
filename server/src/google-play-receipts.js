import {GoogleAuth} from 'google-auth-library';
import {GOOGLE_PLAY_PACKAGE} from './play-purchase-policy.js';

const PUBLISHER_SCOPE='https://www.googleapis.com/auth/androidpublisher';
const API_ORIGIN='https://androidpublisher.googleapis.com';
const API_PREFIX='/androidpublisher/v3/applications/';

/**
 * Read-only client for the authenticated Google Play Developer API. This
 * class does NOT grant purchases, acknowledge, refund or mutate the database.
 *
 * Production activation requires service-account configuration inside the
 * isolated host (not a client OAuth secret), Play Console API permissions,
 * server-only purchase-token binding and RTDN/refund reconciliation.
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

  async get(type,token){
    if(type!=='productsv2'&&type!=='subscriptionsv2')
      throw new Error('Unsupported Play purchase resource');
    if(typeof token!=='string'||token.length<20||token.length>4096||
        !/^[a-zA-Z0-9._:-]+$/.test(token))
      throw new Error('Invalid Play purchase token');
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
