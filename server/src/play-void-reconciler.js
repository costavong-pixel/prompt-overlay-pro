import {GooglePlayReceiptReader} from './google-play-receipts.js';
import {PlayClaimLedger} from './play-claim-ledger.js';

const DAY=24*60*60*1000;
const FIRST_RUN_LOOKBACK=29*DAY;
const OVERLAP=24*60*60*1000;
const MAX_DOWNTIME=28*DAY;
const MAX_PAGES=100;

/**
 * FUTURE background-worker component ONLY. This is not an HTTP endpoint,
 * a timer, or a live VPS deployment. Only authenticated Google Publisher
 * responses are permitted as inputs. Run under a tightly scoped service
 * account once financial/voided-purchase API permissions are approved.
 *
 * Repeated 24h overlapping windows deliberately trade extra Google API
 * calls for avoiding lost refund events on transient failures.
 */
export class PlayVoidReconciler {
  constructor({store,reader,ledger}){
    if(!(reader instanceof GooglePlayReceiptReader)||
        !(ledger instanceof PlayClaimLedger)||ledger.store!==store||
        !store||typeof store.now!=='function')
      throw new TypeError('Google Publisher reader + same-store ledger required');
    this.store=store;
    this.reader=reader;
    this.ledger=ledger;
  }

  async run(){
    const now=this.store.now();
    if(!Number.isSafeInteger(now)||now<=0)
      throw new Error('Invalid server clock');
    const previous=this.ledger.lastVoidSync();
    if(previous!==null&&(previous>now||now-previous>MAX_DOWNTIME))
      throw new Error('VOID_SYNC_GAP_REQUIRES_REVIEW');
    const fromMs=previous===null
      ?now-FIRST_RUN_LOOKBACK
      :Math.max(now-FIRST_RUN_LOOKBACK,previous-OVERLAP);
    const visited=new Set();
    let pageToken=null,pages=0,seen=0,created=0;
    do {
      // Never advance the checkpoint if a page fails or has an invalid event.
      if(pages>=MAX_PAGES)throw new Error('VOID_SYNC_PAGE_LIMIT');
      const response=await this.reader.getVoided({
        fromMs,toMs:now,pageToken,nowMs:now
      });
      pages++;
      if(!Array.isArray(response.items))
        throw new Error('INVALID_GOOGLE_VOID_RESPONSE');
      for(const row of response.items){
        if(!row||typeof row!=='object'||Array.isArray(row))
          throw new Error('INVALID_GOOGLE_VOID_EVENT');
        // All event IDs are hashes; do not write purchaseToken or orderId
        // to SQLite, CI logs or application logs.
        const result=this.ledger.ingestVoided(row);
        seen++;
        if(result.recorded)created++;
      }
      const next=response.nextPageToken;
      if(next!==null&&next!==undefined){
        if(typeof next!=='string'||!next||
            visited.has(next))throw new Error('INVALID_GOOGLE_VOID_PAGINATION');
        visited.add(next);
        pageToken=next;
      }else pageToken=null;
    }while(pageToken!==null);
    // Complete only after the full requested time window has been consumed.
    this.ledger.completeVoidSync(now);
    return Object.freeze({complete:true,pages,seen,newEvents:created,
      fromMs,toMs:now});
  }
}
