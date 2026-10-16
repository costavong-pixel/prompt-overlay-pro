package com.costavong.promptoverlay.access;

import com.costavong.promptoverlay.BuildConfig;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Android client of the HTTPS trial service. Run all methods off the UI
 * thread. No fallback to unverified device-local trial or purchase flags.
 */
public final class TrialApi {
    private static final int MAX_RESPONSE=64*1024;
    private final String host;

    public TrialApi(){
        host=BuildConfig.TRIAL_API_URL;
        if(!configured())throw new IllegalStateException("Trial service is not configured");
    }
    public static boolean configured(){
        return BuildConfig.TRIAL_API_URL!=null
            && BuildConfig.TRIAL_API_URL.startsWith("https://")
            && BuildConfig.TRIAL_WEB_CLIENT_ID!=null
            && BuildConfig.TRIAL_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com");
    }

    /** Public connectivity check; never authenticates or activates a trial. */
    public JSONObject health()throws IOException{return exchange("GET","/health",null,null);}
    public JSONObject nonce()throws IOException{return exchange("GET","/v1/auth/nonce",null,null);}
    public JSONObject signIn(String idToken,String nonce)throws IOException{
        JSONObject request=new JSONObject();
        try{request.put("idToken",idToken);request.put("nonce",nonce);}
        catch(Exception e){throw new IOException("Cannot prepare sign-in",e);}
        return exchange("POST","/v1/auth/google",null,request);
    }
    public JSONObject entitlement(String bearer)throws IOException{
        return exchange("GET","/v1/entitlements",bearer,null);
    }

    /**
     * Retrieve an account-specific SHA-256 identifier for Play checkout.
     * This cannot be invented on the phone and is not itself an entitlement.
     */
    public JSONObject billingIdentity(String bearer)throws IOException{
        return exchange("GET","/v1/billing/account-id",bearer,null);
    }

    /** The server verifies Google receipt + account ownership before granting. */
    public JSONObject verifyPurchase(String bearer,String productId,String token)throws IOException{
        JSONObject request=new JSONObject();
        try{
            request.put("productId",productId);
            request.put("purchaseToken",token);
        }catch(Exception e){throw new IOException("Cannot prepare purchase verification",e);}
        return exchange("POST","/v1/purchases/verify",bearer,request);
    }
    public void logout(String bearer)throws IOException{
        exchange("POST","/v1/logout",bearer,new JSONObject());
    }

    private JSONObject exchange(String method,String path,String bearer,JSONObject payload)throws IOException{
        URL endpoint=new URL(host+path);
        if(!"https".equalsIgnoreCase(endpoint.getProtocol()))
            throw new IOException("HTTPS required");
        HttpURLConnection connection=(HttpURLConnection)endpoint.openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        if(bearer!=null)connection.setRequestProperty("Authorization","Bearer "+bearer);
        if(payload!=null){
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            byte[] encoded=payload.toString().getBytes(StandardCharsets.UTF_8);
            if(encoded.length>12000)throw new IOException("Request too large");
            connection.setFixedLengthStreamingMode(encoded.length);
            try(OutputStream out=connection.getOutputStream()){out.write(encoded);}
        }
        try{
            int code=connection.getResponseCode();
            if(code==204)return new JSONObject();
            if(code<200||code>=300)throw new IOException(
                code==401?"Sign-in expired or invalid":"Trial service error ("+code+")");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] block=new byte[4096];int size;
                while((size=in.read(block))!=-1){
                    if(buffer.size()+size>MAX_RESPONSE)throw new IOException("Unexpected response size");
                    buffer.write(block,0,size);
                }
            }
            try{return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));}
            catch(Exception e){throw new IOException("Cannot decode trial service response",e);}
        }finally{connection.disconnect();}
    }

    public static AccessPolicy.Decision readEntitlement(JSONObject o){
        if(o==null||!o.optBoolean("serverVerified",false))
            return AccessPolicy.decide(null);
        AccessPolicy.VerifiedSnapshot s=new AccessPolicy.VerifiedSnapshot(
            true,o.optLong("serverNowMs",0),o.optLong("trialActivatedAtMs",0),
            o.optBoolean("ownsBasicLifetime",false),
            o.optBoolean("previouslyPurchasedPaidBasic",false),
            o.optBoolean("ownsProLifetime",false),
            o.optBoolean("ownsFeedbackAward",false),
            o.optLong("monthlyAccessUntilMs",0));
        return AccessPolicy.decide(s);
    }
}
