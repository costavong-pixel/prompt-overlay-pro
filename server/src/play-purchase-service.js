import {hashValue} from './store.js';
import {GOOGLE_PLAY_PACKAGE, KNOWN_PRODUCTS, expectedObfuscatedAccountId,
  evaluateOneTime,evaluateMonthly,mayGrantFromGoogleReceipt} from './play-purchase-policy.js';
import {GooglePlayReceiptReader} from './google-play-receipts.js';
import {PlayClaimLedger} from './play-claim-ledger.js';

/**
 * Staged secure purchase processor. It will only be exposed by createApp
 * after a future explicitly approved deployment supplies this dependency.
 * Live server remains pinned to the pre-billing revision and HTTP 503.
 */
export class PlayPurchaseService {
  constructor({reader,store}){
    if(!(reader instanceof GooglePlayReceiptReader)||!store?.db)
      throw new TypeError('Verified Google Publisher reader and TrialStore required');
    this.reader=reader;
    this.store=store;
    this.ledger=new PlayClaimLedger(store);
  }

  billingIdentity(user){
    if(!user?.id)throw new Error('Authenticated account required');
    return expectedObfuscatedAccountId(user.id,hashValue);
  }

  access(user){
    if(!user?.id)throw new Error('Authenticated account required');
    return this.ledger.status(user.id);
  }

  async verify(user,request){
    if(!user?.id||!this.store.db.prepare('SELECT 1 FROM users WHERE id=?').get(user.id))
      return {verified:false,reason:'AUTH_REQUIRED'};
    const {productId,purchaseToken}=request||{};
    const kind=KNOWN_PRODUCTS[productId];
    if(!kind||typeof purchaseToken!=='string'){
      return {verified:false,reason:'INVALID_PRODUCT'};
    }
    // A token must first pass syntax validation. Only Google can attest its
    // actual identity and current purchase state. No client-provided receipt.
    try{GooglePlayReceiptReader.requireValidToken(purchaseToken);}
    catch{return {verified:false,reason:'INVALID_TOKEN'};}
    const owner=this.billingIdentity(user);
    let decision;
    try{
      const response=kind==='inapp'
        ?await this.reader.getOneTime(purchaseToken)
        :await this.reader.getSubscription(purchaseToken);
      decision=kind==='inapp'
        ?evaluateOneTime(response,productId,owner)
        :evaluateMonthly(response,this.store.now(),owner);
    }catch{
      return {verified:false,reason:'GOOGLE_PLAY_UNAVAILABLE'};
    }
    if(!mayGrantFromGoogleReceipt(decision))
      return {verified:false,reason:decision?.reason||'REJECTED'};

    // Acknowledge from the authenticated server, not Android. A failed
    // acknowledgement returns a retriable response but never silently grants.
    if(decision.needsAcknowledgement){
      try{
        await this.reader.acknowledge({type:kind,productId,token:purchaseToken});
      }catch{
        return {verified:false,reason:'ACKNOWLEDGEMENT_RETRY_REQUIRED'};
      }
    }
    const saved=this.ledger.verifiedClaim({
      user,token:purchaseToken,serverDecision:decision
    });
    if(!saved.saved)return {verified:false,reason:saved.reason};
    return {
      verified:true,productId,
      kind:decision.kind,
      expiresAtMs:decision.expiresAtMs||0
    };
  }
}
