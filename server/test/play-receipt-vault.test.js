import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,chmod,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {TrialStore,hashValue} from '../src/store.js';
import {PlayClaimLedger} from '../src/play-claim-ledger.js';
import {PlayReceiptVault,loadPlayReceiptKey} from '../src/play-receipt-vault.js';
import {GooglePlayReceiptReader} from '../src/google-play-receipts.js';
import {PlayPurchaseService} from '../src/play-purchase-service.js';
import {expectedObfuscatedAccountId,evaluateOneTime} from '../src/play-purchase-policy.js';

const TIME=2_100_000_000_000;
const TOKEN='server-only-subscriber-receipt-00000001';
const KEY=Buffer.alloc(32,0x5a);
function setup(){
  const store=new TrialStore(':memory:',()=>TIME);
  const alice=store.signIn('user-alice').user;
  const bob=store.signIn('user-bob').user;
  const ledger=new PlayClaimLedger(store);
  const vault=new PlayReceiptVault({store,key:KEY});
  const owner=expectedObfuscatedAccountId(alice.id,hashValue);
  const decision=evaluateOneTime({
    purchaseStateContext:{purchaseState:'PURCHASED'},
    productLineItem:[{productId:'pro_lifetime',productOfferDetails:{
      consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED',
      purchaseOptionId:'pro-buy'
    }}],
    acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
    obfuscatedExternalAccountId:owner
  },'pro_lifetime',owner);
  return {store,alice,bob,ledger,vault,decision,close:()=>{vault.dispose();store.close();}};
}

test('vault refuses to initialize without exact 256-bit protected key',()=>{
  const store=new TrialStore(':memory:',()=>TIME);
  try{
    new PlayClaimLedger(store);
    assert.throws(()=>new PlayReceiptVault({store}),/32-byte/);
    assert.throws(()=>new PlayReceiptVault({store,key:Buffer.alloc(16)}),/32-byte/);
    assert.throws(()=>new PlayReceiptVault({store,key:'password'}),/32-byte/);
  }finally{store.close();}
});

test('AES-GCM token vault stores only ciphertext, binds user and product, decrypts for worker',()=>{
  const f=setup();
  try{
    assert.equal(f.ledger.verifiedClaim({
      user:f.alice,token:TOKEN,serverDecision:f.decision
    }).saved,true);
    const a=f.vault.remember({token:TOKEN,userId:f.alice.id,productId:'pro_lifetime'});
    assert.equal(a.stored,true);
    assert.equal(a.tokenHash,hashValue(TOKEN));
    const stored=f.store.db.prepare('SELECT * FROM play_receipt_tokens').get();
    assert.equal(stored.token_hash,hashValue(TOKEN));
    assert.equal(stored.iv.length,12);
    assert.equal(stored.tag.length,16);
    assert.notEqual(stored.ciphertext.toString('utf8'),TOKEN);
    assert.ok(!JSON.stringify(stored).includes(TOKEN));
    assert.equal(f.vault.unlock({tokenHash:a.tokenHash,
      userId:f.alice.id,productId:'pro_lifetime'}),TOKEN);
    assert.throws(()=>f.vault.unlock({tokenHash:a.tokenHash,
      userId:f.bob.id,productId:'pro_lifetime'}),/No active encrypted receipt/);
    assert.throws(()=>f.vault.unlock({tokenHash:a.tokenHash,
      userId:f.alice.id,productId:'basic_lifetime'}),/No active encrypted receipt/);
    assert.equal(f.vault.candidates().length,1);
  }finally{f.close();}
});

test('tampering with ciphertext or authentication tag cannot reveal any receipt',()=>{
  const f=setup();
  try{
    f.ledger.verifiedClaim({user:f.alice,token:TOKEN,serverDecision:f.decision});
    f.vault.remember({token:TOKEN,userId:f.alice.id,productId:'pro_lifetime'});
    const original=f.store.db.prepare('SELECT * FROM play_receipt_tokens').get();
    const altered=Buffer.from(original.ciphertext);
    altered[0]^=0x02;
    f.store.db.prepare('UPDATE play_receipt_tokens SET ciphertext=?')
      .run(altered);
    assert.throws(()=>f.vault.unlock({tokenHash:hashValue(TOKEN),
      userId:f.alice.id,productId:'pro_lifetime'}));
    f.store.db.prepare('UPDATE play_receipt_tokens SET ciphertext=?,tag=?')
      .run(original.ciphertext,Buffer.alloc(16,0));
    assert.throws(()=>f.vault.unlock({tokenHash:hashValue(TOKEN),
      userId:f.alice.id,productId:'pro_lifetime'}));
  }finally{f.close();}
});

test('refund hold instantly prevents decrypting or enumerating active receipt',()=>{
  const f=setup();
  try{
    f.ledger.verifiedClaim({user:f.alice,token:TOKEN,serverDecision:f.decision});
    f.vault.remember({token:TOKEN,userId:f.alice.id,productId:'pro_lifetime'});
    assert.equal(f.vault.candidates().length,1);
    f.ledger.ingestVoided({
      purchaseToken:TOKEN,orderId:'GPA.1111-2222-3333-44444',
      voidedTimeMillis:String(TIME-1_000)
    });
    assert.equal(f.vault.candidates().length,0);
    assert.throws(()=>f.vault.unlock({tokenHash:hashValue(TOKEN),
      userId:f.alice.id,productId:'pro_lifetime'}),/No active encrypted receipt/);
  }finally{f.close();}
});

test('purchase service fails closed and rolls back verified claim if encryption fails',async()=>{
  const f=setup();
  try{
    const reader=new GooglePlayReceiptReader({googleClient:{
      request:async()=>({data:{
        purchaseStateContext:{purchaseState:'PURCHASED'},
        productLineItem:[{productId:'pro_lifetime',productOfferDetails:{
          purchaseOptionId:'pro-buy',
          consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED'
        }}],
        acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
        obfuscatedExternalAccountId:expectedObfuscatedAccountId(f.alice.id,hashValue)
      }})
    }});
    const service=new PlayPurchaseService({reader,store:f.store,receiptKey:KEY});
    service.vault.remember=()=>{throw new Error('Simulated encrypted-volume failure');};
    const result=await service.verify(f.alice,{productId:'pro_lifetime',purchaseToken:TOKEN});
    assert.equal(result.verified,false);
    assert.equal(result.reason,'PERSISTENCE_UNAVAILABLE');
    assert.equal(f.store.db.prepare('SELECT COUNT(*) AS n FROM play_claims').get().n,0);
    assert.equal(f.store.db.prepare('SELECT COUNT(*) AS n FROM play_receipt_tokens').get().n,0);
  }finally{f.close();}
});

test('protected file loader accepts only private base64 32-byte key',async()=>{
  const directory=await mkdtemp(join(tmpdir(),'play-token-key-'));
  const keyFile=join(directory,'receipt.key');
  try{
    await writeFile(keyFile,KEY.toString('base64')+'\n',{mode:0o600});
    const loaded=await loadPlayReceiptKey(keyFile);
    assert.ok(loaded.equals(KEY));
    if(process.platform!=='win32'){
      await chmod(keyFile,0o644);
      await assert.rejects(()=>loadPlayReceiptKey(keyFile),/Unsafe/);
    }
    await chmod(keyFile,0o600);
    await writeFile(keyFile,'not-a-key');
    await assert.rejects(()=>loadPlayReceiptKey(keyFile),/Invalid/);
  }finally{await rm(directory,{recursive:true,force:true});}
});
