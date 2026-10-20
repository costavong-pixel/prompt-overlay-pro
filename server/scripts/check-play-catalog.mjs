#!/usr/bin/env node
/**
 * Run ONLY on a trusted machine with locally provisioned Google service
 * account Application Default Credentials. No signing key or service-account
 * secret is included, read, printed, copied or checked into source code.
 *
 * This is GET-only: it does not process payments, activate SKUs, connect the
 * live billing server, acknowledge purchases, or change Google Play settings.
 */
import {GoogleAuth} from 'google-auth-library';
import {PlayCatalogReadOnlyClient} from '../src/play-catalog-readiness.js';

const EXPECTED='prompt-overlay-billing@prompt-ola.iam.gserviceaccount.com';

async function main(){
  // Explicit opt-in required, so accidental CI/VPS launches do not make
  // external Google API calls without operator attention.
  if(process.env.PLAY_CATALOG_READ_ONLY_CHECK!=='YES')
    throw new Error('SET_PLAY_CATALOG_READ_ONLY_CHECK_YES_TO_RUN');
  const auth=new GoogleAuth({
    scopes:['https://www.googleapis.com/auth/androidpublisher']
  });
  const credentials=await auth.getCredentials();
  if(credentials?.client_email!==EXPECTED)
    throw new Error('WRONG_OR_UNVERIFIED_SERVICE_ACCOUNT_IDENTITY');
  const client=await auth.getClient();
  const report=await new PlayCatalogReadOnlyClient(client).audit();
  const safe={
    serviceAccountIdentityChecked:true,
    ...report
  };
  process.stdout.write(JSON.stringify(safe,null,2)+'\n');
  if(!report.matchesConfiguration){
    process.stderr.write('CATALOG_MISMATCH: review draft SKU and base plan configuration.\n');
    process.exitCode=2;
  }else{
    process.stdout.write('CATALOG_CONFIGURATION_MATCHED: drafts are not for sale.\n');
  }
}

main().catch(error=>{
  // Do not print error.message/stack from Google Auth: those may include
  // credential filenames, OAuth claims or proxy details. Only safe
  // categories/status values are shown.
  const message=error?.message||'';
  const code=error?.code;
  let reason='GOOGLE_AUTH_OR_PLAY_API_UNAVAILABLE';
  if(message==='SET_PLAY_CATALOG_READ_ONLY_CHECK_YES_TO_RUN')
    reason='EXPLICIT_READ_ONLY_OPT_IN_REQUIRED';
  else if(message==='WRONG_OR_UNVERIFIED_SERVICE_ACCOUNT_IDENTITY')
    reason='WRONG_OR_UNVERIFIED_SERVICE_ACCOUNT_IDENTITY';
  else if(code===401||code===403)
    reason='PLAY_API_ACCESS_DENIED';
  process.stderr.write('PLAY_CATALOG_CHECK_FAILED: '+reason+'\n');
  process.exitCode=1;
});
