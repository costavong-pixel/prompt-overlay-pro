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
const trustedProxy=process.env.TRUST_PROXY||'0';
if(trustedProxy==='internal'){
  // Only use behind a private, local reverse proxy that REPLACES incoming
  // X-Forwarded-For with the actual requester's IP (see Nginx template).
  // Never use app.set('trust proxy',true).
  app.set('trust proxy',['loopback','linklocal','uniquelocal']);
}else if(trustedProxy!=='0'){
  throw new Error('TRUST_PROXY must be 0 or internal; never trust arbitrary forwarding headers');
}
app.listen(port,host,()=>{
  console.log('Trial service listening on '+host+':'+port+' (use HTTPS reverse proxy)');
});
process.on('SIGTERM',()=>{store.close();process.exit(0);});
