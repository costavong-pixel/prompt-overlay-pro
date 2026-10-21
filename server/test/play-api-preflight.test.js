import {test} from 'node:test';
import assert from 'node:assert/strict';
import {GooglePlayReceiptReader} from '../src/google-play-receipts.js';
import {checkPublisherAccess} from '../src/play-api-preflight.js';

const NOW=2_100_000_000_000;
test('manual preflight only reads pinned publisher refunds endpoint and exposes no records',async()=>{
  const requests=[];
  const reader=new GooglePlayReceiptReader({googleClient:{
    request:async opts=>{
      requests.push(opts);
      return {data:{voidedPurchases:[{
        purchaseToken:'sensitive-but-never-output-purchase-token-0001',
        orderId:'GPA.1234-5678-9012-34567',
        voidedTimeMillis:String(NOW)
      }]}};
    }
  }});
  const okay=await checkPublisherAccess(reader,NOW);
  assert.equal(okay,true);
  assert.equal(requests.length,1);
  assert.equal(requests[0].method,'GET');
  const url=new URL(requests[0].url);
  assert.equal(url.host,'androidpublisher.googleapis.com');
  assert.equal(url.searchParams.get('type'),'1');
  assert.equal(url.searchParams.get('startTime'),String(NOW-60*60*1000));
  assert.equal(url.searchParams.get('endTime'),String(NOW));
});

test('unavailable or unauthorized API must not report success',async()=>{
  const reader=new GooglePlayReceiptReader({googleClient:{
    request:async()=>{throw new Error('HTTP 403');}
  }});
  await assert.rejects(()=>checkPublisherAccess(reader,NOW),/HTTP 403/);
  await assert.rejects(()=>checkPublisherAccess(null,NOW),/reader/);
  await assert.rejects(()=>checkPublisherAccess(reader,NaN),/reader/);
});
