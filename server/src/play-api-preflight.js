import {stat} from 'node:fs/promises';
import {GooglePlayReceiptReader} from './google-play-receipts.js';

/**
 * Read-only Google Play service-account permission check. Invoke manually
 * after approved configuration; NOT part of production server startup.
 * Return only a boolean; NEVER expose Google purchase tokens, credentials,
 * application customer data, response body, or an auth exception to logs.
 */
export async function checkPublisherAccess(reader,nowMs=Date.now()){
  if(!(reader instanceof GooglePlayReceiptReader)||
      !Number.isSafeInteger(nowMs)||nowMs<=60*60*1000)
    throw new TypeError('Authenticated Publisher reader and valid time required');
  await reader.getVoided({fromMs:nowMs-60*60*1000,
    toMs:nowMs,nowMs});
  return true;
}

async function main(){
  // This command does not create credentials. If an on-disk JSON key is
  // explicitly configured, prevent the common group-readable permissions
  // mistake. Managed identities without a JSON file are also supported.
  const file=process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if(file){
    try{
      const info=await stat(file);
      if(!info.isFile()||(process.platform!=='win32'&&(info.mode&0o077)!==0)){
        process.stderr.write('FAIL: Google credential file permissions are unsafe.\n');
        process.exitCode=2;return;
      }
    }catch{
      process.stderr.write('FAIL: Google credential file is unavailable.\n');
      process.exitCode=2;return;
    }
  }
  try{
    const reader=await GooglePlayReceiptReader.fromServerApplicationDefaultCredentials();
    await checkPublisherAccess(reader);
    process.stdout.write(
      'PASS: Authorized read-only Google Play Publisher voided-purchase API check.\n');
  }catch{
    // Deliberately redact all provider failure payloads and identity details.
    process.stderr.write(
      'FAIL: Publisher access not confirmed; check Play app permissions and service account.\n');
    process.exitCode=1;
  }
}

// Only run when directly invoked via npm script; imports must be side-effect
// free so unit tests can inject a fake Google Publisher transport.
if(process.argv[1]&&import.meta.url===new URL(
  'file://'+process.argv[1]).href){
  await main();
}
