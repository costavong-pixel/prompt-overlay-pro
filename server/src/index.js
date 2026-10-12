import { TrialStore } from './store.js';
import { createApp, makeGoogleVerifier } from './app.js';

const clientId=process.env.GOOGLE_WEB_CLIENT_ID||'';
const databasePath=process.env.TRIAL_DB_PATH||'';
if(!clientId.endsWith('.apps.googleusercontent.com')||!databasePath){
  throw new Error('Set GOOGLE_WEB_CLIENT_ID and TRIAL_DB_PATH; server not started');
}
const store=new TrialStore(databasePath);
const app=createApp({store,verifyGoogleToken:makeGoogleVerifier(clientId)});
const host=process.env.HOST||'127.0.0.1';
const port=Number(process.env.PORT||8765);
if(process.env.TRUST_PROXY&&process.env.TRUST_PROXY!=='0'){
  // Never trust arbitrary client-supplied forwarding headers.
  throw new Error('TRUST_PROXY must remain 0 unless audited for the deployment');
}
app.listen(port,host,()=>{
  console.log('Trial service listening on '+host+':'+port+' (use HTTPS reverse proxy)');
});
process.on('SIGTERM',()=>{store.close();process.exit(0);});
