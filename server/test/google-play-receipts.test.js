import {test} from 'node:test';
import assert from 'node:assert/strict';
import {GooglePlayReceiptReader} from '../src/google-play-receipts.js';

const token='abcDEF.123XYZ:v2-example-purchase-token';

test('real Google Publisher read-only endpoints are fixed to production app',async()=>{
  const requests=[];
  const reader=new GooglePlayReceiptReader({googleClient:{
    request:async config=>{requests.push(config);return {data:{kind:'verified test fixture'}};}
  }});
  await reader.getOneTime(token);
  await reader.getSubscription(token);
  assert.equal(requests.length,2);
  assert.equal(requests[0].url,'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.costavong.promptoverlay/purchases/productsv2/tokens/'+encodeURIComponent(token));
  assert.equal(requests[1].url,'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.costavong.promptoverlay/purchases/subscriptionsv2/tokens/'+encodeURIComponent(token));
  assert.ok(requests.every(r=>r.method==='GET'));
  assert.ok(requests.every(r=>r.timeout===12000));
  assert.ok(requests.every(r=>r.url.startsWith('https://androidpublisher.googleapis.com/')));
});

test('QA package and malicious tokens cannot be used as production receipt proof',async()=>{
  assert.throws(()=>new GooglePlayReceiptReader({packageName:'com.costavong.promptoverlay.protest',googleClient:{request(){}}}));
  const requests=[];
  const reader=new GooglePlayReceiptReader({googleClient:{
    request:async params=>{requests.push(params);return {data:{}};}
  }});
  for(const bad of ['', 'a', 'token/slash-exploit?x=1','http://other','token\nAuthorization:abc',undefined]){
    await assert.rejects(()=>reader.getOneTime(bad));
  }
  assert.equal(requests.length,0);
  await assert.rejects(()=>reader.get('orders',token));
});

test('Play error or malformed response never becomes a purchase grant',async()=>{
  const reader=new GooglePlayReceiptReader({googleClient:{request:async()=>{throw new Error('Google API 403');}}});
  await assert.rejects(()=>reader.getOneTime(token),/403/);
  const broken=new GooglePlayReceiptReader({googleClient:{request:async()=>({data:null})}});
  await assert.rejects(()=>broken.getSubscription(token),/Invalid Google Publisher response/);
});
