import express from 'express';
import { rateLimit } from 'express-rate-limit';
import { OAuth2Client } from 'google-auth-library';
import { TrialStore, hashValue } from './store.js';
import { expectedObfuscatedAccountId } from './play-purchase-policy.js';

export function makeGoogleVerifier(clientId) {
  const client=new OAuth2Client(clientId);
  return async(idToken)=>{
    const ticket=await client.verifyIdToken({idToken,audience:clientId});
    const payload=ticket.getPayload();
    if(!payload||!payload.sub||!['accounts.google.com','https://accounts.google.com'].includes(payload.iss))
      throw new Error('Unverified Google identity');
    return payload;
  };
}

function bearer(req){
  const header=req.get('authorization')||'';
  if(!header.startsWith('Bearer '))return '';
  return header.substring(7).trim();
}

export function createApp({store,verifyGoogleToken,purchaseService=null}) {
  if(!(store instanceof TrialStore)||typeof verifyGoogleToken!=='function')
    throw new Error('TrialStore and token verifier required');
  const app=express();
  app.disable('x-powered-by');
  // HTTPS is required in front of this service. A reverse proxy sets TLS.
  app.use((req,res,next)=>{
    res.set('Cache-Control','no-store');
    res.set('X-Content-Type-Options','nosniff');
    res.set('Referrer-Policy','no-referrer');
    next();
  });
  app.use(express.json({limit:'12kb'}));
  const authLimit=rateLimit({
    windowMs:15*60*1000,limit:30,standardHeaders:'draft-7',
    legacyHeaders:false,message:{error:'RATE_LIMITED'}
  });

  app.get('/health',(_req,res)=>res.json({ok:true}));
  app.get('/v1/auth/nonce',authLimit,(_req,res)=>{
    res.json({nonce:store.newNonce(),expiresInSeconds:300});
  });

  app.post('/v1/auth/google',authLimit,async(req,res)=>{
    const idToken=req.body?.idToken,nonce=req.body?.nonce;
    if(typeof idToken!=='string'||idToken.length<100||idToken.length>10000
        ||typeof nonce!=='string'||nonce.length>200)
      return res.status(400).json({error:'INVALID_SIGN_IN'});
    try{
      const payload=await verifyGoogleToken(idToken);
      // Verify nonce signature inside Google's ID token AND consume it once.
      if(payload.nonce!==nonce || !store.consumeNonce(nonce))
        return res.status(401).json({error:'NONCE_INVALID'});
      const signed=store.signIn(payload.sub);
      return res.json({
        sessionToken:signed.token,
        sessionExpiresMs:signed.sessionExpiresMs,
        entitlement:store.entitlement(signed.user)
      });
    }catch(_e){
      return res.status(401).json({error:'GOOGLE_SIGN_IN_FAILED'});
    }
  });

  app.use('/v1',(req,res,next)=>{
    // Nonce/auth endpoints are registered above. Every subsequent route needs
    // authenticated session, never accept a user_id or email from client.
    const token=bearer(req);
    const user=store.userFromToken(token);
    if(!user)return res.status(401).json({error:'AUTH_REQUIRED'});
    req.authenticatedUser=user;
    req.sessionToken=token;
    next();
  });
  app.get('/v1/entitlements',(req,res)=>{
    const current=store.entitlement(req.authenticatedUser);
    if(purchaseService){
      const paid=purchaseService.access(req.authenticatedUser);
      current.ownsBasicLifetime ||= paid.ownsBasicLifetime;
      current.ownsProLifetime ||= paid.ownsProLifetime;
      current.monthlyAccessUntilMs=Math.max(
        current.monthlyAccessUntilMs,paid.monthlyAccessUntilMs);
    }
    res.json(current);
  });

  // The phone must send this stable, authenticated and hashed account binding
  // to Google Play BillingFlowParams.setObfuscatedAccountId. It does NOT grant
  // any right on its own and cannot be supplied by an unauthenticated caller.
  app.get('/v1/billing/account-id',(req,res)=>{
    res.json({obfuscatedAccountId:expectedObfuscatedAccountId(
      req.authenticatedUser.id,hashValue)});
  });
  app.post('/v1/logout',(req,res)=>{
    store.revokeToken(req.sessionToken);
    res.status(204).end();
  });
  // The deployed server passes no purchaseService. It remains HTTP 503 until
  // a separately reviewed production payment/RTDN/voided-purchases rollout.
  app.post('/v1/purchases/verify',authLimit,async(req,res)=>{
    if(!purchaseService)
      return res.status(503).json({error:'BILLING_NOT_ENABLED'});
    try{
      const result=await purchaseService.verify(req.authenticatedUser,req.body);
      return res.status(result.verified?200:409).json(result);
    }catch{
      // Never expose receipt tokens, Google account identifiers, or service
      // credentials in errors or HTTP responses.
      return res.status(503).json({error:'PURCHASE_VERIFICATION_UNAVAILABLE'});
    }
  });
  app.use((err,_req,res,_next)=>{
    if(err?.type==='entity.too.large')return res.status(413).json({error:'REQUEST_TOO_LARGE'});
    if(err instanceof SyntaxError&&'body' in err)return res.status(400).json({error:'INVALID_JSON'});
    return res.status(500).json({error:'SERVER_ERROR'});
  });
  return app;
}
