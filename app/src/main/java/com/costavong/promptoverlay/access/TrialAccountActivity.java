package com.costavong.promptoverlay.access;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.costavong.promptoverlay.BuildConfig;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Explicit Google sign-in for the server-controlled 90-day trial.
 * This screen is an opt-in prototype only: no paid feature gating here, and
 * checkout is never shown until server-side Play verification is implemented.
 */
public final class TrialAccountActivity extends Activity {
    private static final int BG=0xff131720,CARD=0xff202634,BLUE=0xff4b67ed,WHITE=0xfff3f5fb;
    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private TrialSessionVault vault;
    private TextView status;
    private Button signIn,refresh,signOut;
    private boolean busy;
    private String session;
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private TextView text(String content,int size,boolean bold){
        TextView t=new TextView(this);t.setText(content);t.setTextColor(WHITE);
        t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private Button button(String label,int background,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setBackground(shape(background));b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout parent,View view){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(7));parent.addView(view,lp);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        vault=new TrialSessionVault(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(content);setContentView(scroll);
        add(content,button("Back",CARD,this::finish));
        add(content,text("90 DAYS FREE · ALL FEATURES",24,true));
        add(content,text("One 90-day trial includes every currently available Basic and Pro feature: teleprompter, editing, captions and export.",15,false));
        add(content,text("No credit card required. No automatic subscription or charge. Your trial begins when Google sign-in is verified.",13,false));
        add(content,text("Google sign-in keeps your original 90-day deadline after reinstalling the app.",13,false));
        status=text("",15,true);add(content,status);
        signIn=button("Sign in with Google",BLUE,this::beginGoogleSignIn);add(content,signIn);
        refresh=button("Refresh access",CARD,this::refreshEntitlements);add(content,refresh);
        signOut=button("Sign out",CARD,this::logout);add(content,signOut);
        add(content,text("AFTER YOUR 90 DAYS",17,true));
        add(content,text("Choose only if you want to continue; nothing renews automatically:",13,false));
        add(content,text("Basic Lifetime · "+TrialOffer.BASIC_TARGET_USD+" once",15,true));
        add(content,text("Keep the Basic teleprompter features forever.",12,false));
        add(content,text("Pro Monthly · "+TrialOffer.PRO_MONTHLY_TARGET_USD+" / month",15,true));
        add(content,text("Keep all current Pro features while subscribed.",12,false));
        add(content,text("Pro Lifetime · "+TrialOffer.PRO_LIFETIME_TARGET_USD+" once",15,true));
        add(content,text("Keep all current non-AI Pro features permanently.",12,false));
        add(content,text("We will choose 500 users for free Pro Lifetime licenses based on detailed private product feedback, independently of Google Play reviews.",12,false));
        add(content,text("Target USD prices only; Google Play will show actual local prices when checkout becomes available.",12,false));
        add(content,text("TEST BUILD: this screen cannot activate a real trial until the owner connects Google OAuth and the HTTPS trial service. Purchases and feature locks are not enabled. No payment is collected.",12,false));
        session=vault.restore();
        if(!TrialApi.configured()){
            showStatus("Google sign-in is not configured yet. The owner must connect a verified HTTPS trial server and OAuth Web client.");
        }else if(session!=null)refreshEntitlements();
        else showStatus("No trial activated yet. Sign in to start.");
        controls();
    }
    private void showStatus(String message){status.setText(message);}
    private void controls(){
        boolean configured=TrialApi.configured();
        signIn.setEnabled(configured&&!busy);
        refresh.setEnabled(configured&&!busy&&session!=null);
        signOut.setEnabled(!busy&&session!=null);
    }
    private void runBackground(Work work,Result response){
        if(busy)return;
        busy=true;controls();
        background.execute(()->{
            try{JSONObject result=work.call();runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;busy=false;response.done(result);controls();
            });}
            catch(Exception failure){runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                busy=false;showStatus("Could not connect: "+failure.getMessage());controls();
            });}
        });
    }
    private interface Work{JSONObject call()throws Exception;}
    private interface Result{void done(JSONObject data);}
    private void beginGoogleSignIn(){
        showStatus("Connecting to Google...");
        runBackground(()->new TrialApi().nonce(),nonceResult->{
            String nonce=nonceResult.optString("nonce","");
            if(nonce.length()<20){showStatus("Invalid server challenge");return;}
            try{
                GetSignInWithGoogleOption option=
                    new GetSignInWithGoogleOption.Builder(BuildConfig.TRIAL_WEB_CLIENT_ID)
                        .setNonce(nonce).build();
                GetCredentialRequest request=new GetCredentialRequest.Builder()
                    .addCredentialOption(option).build();
                showStatus("Choose your Google account...");
                CredentialManager.create(this).getCredentialAsync(
                    this,request,new CancellationSignal(),androidx.core.content.ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
                        @Override public void onResult(GetCredentialResponse result){
                            Credential credential=result.getCredential();
                            if(!(credential instanceof CustomCredential)||
                                !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(((CustomCredential)credential).getType())){
                                showStatus("Unexpected credential received");return;
                            }
                            try{
                                String idToken=GoogleIdTokenCredential.createFrom(
                                    ((CustomCredential)credential).getData()).getIdToken();
                                showStatus("Verifying Google sign-in...");
                                runBackground(()->new TrialApi().signIn(idToken,nonce),body->{
                                    String rawToken=body.optString("sessionToken","");
                                    try{
                                        vault.save(rawToken);
                                        session=rawToken;
                                        showEntitlement(body.optJSONObject("entitlement"));
                                    }catch(Exception e){
                                        showStatus("Could not securely save your session");
                                    }
                                });
                            }catch(Exception e){showStatus("Could not read Google sign-in response");}
                        }
                        @Override public void onError(GetCredentialException error){
                            showStatus("Google sign-in canceled or unavailable");
                        }
                    });
            }catch(Exception e){showStatus("Google sign-in unavailable on this device");}
        });
    }
    private void showEntitlement(JSONObject payload){
        AccessPolicy.Decision decision=TrialApi.readEntitlement(payload);
        switch(decision.reason){
            case TRIAL:
                long day=24L*60*60*1000;
                long days=(decision.trialRemainingMs+day-1)/day;
                showStatus("90-day Pro trial ACTIVE. Approximately "+days+" day(s) remaining.");
                break;
            case PRO_LIFETIME:case FEEDBACK_AWARD_PRO_LIFETIME:
                showStatus("Pro Lifetime access recorded on server");break;
            case PRO_MONTHLY:
                showStatus("Pro Monthly currently active on server");break;
            case LEGACY_PAID_BASIC:case BASIC_LIFETIME:
                showStatus("Basic Lifetime access recorded on server");break;
            case TRIAL_ENDED:
                showStatus("Your 90-day free trial has ended. Choose Basic Lifetime, Pro Monthly, or Pro Lifetime when checkout launches. No automatic charges.");break;
            default: showStatus("Access verification required; no entitlement granted");
        }
    }
    private void refreshEntitlements(){
        if(session==null){showStatus("Sign in to check access");return;}
        showStatus("Checking your trial status...");
        runBackground(()->new TrialApi().entitlement(session),this::showEntitlement);
    }
    private void logout(){
        final String previous=session;
        session=null;vault.clear();controls();
        showStatus("Signed out. Your server-side trial will not restart when you sign in again.");
        if(previous==null||!TrialApi.configured())return;
        background.execute(()->{try{new TrialApi().logout(previous);}catch(Exception ignored){}});
    }
    @Override protected void onDestroy(){background.shutdownNow();super.onDestroy();}
}
