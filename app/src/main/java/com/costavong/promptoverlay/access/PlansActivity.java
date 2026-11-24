package com.costavong.promptoverlay.access;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.costavong.promptoverlay.AppLanguage;
import com.costavong.promptoverlay.BuildConfig;
import com.costavong.promptoverlay.pro.ProFeedbackActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android-first, no-payment preview of the trial and approved Basic plan.
 *
 * This Basic branch keeps plan metadata and a QA-only preview. It never
 * starts Google Play checkout, overrides an entitlement, or claims a trial
 * was activated without the authenticated HTTPS backend.
 */
public final class PlansActivity extends Activity {
    private static final int BG=0xff11151e;
    private static final int CARD=0xff212938;
    private static final int BLUE=0xff4969ed;
    private static final int TEAL=0xff84dfcf;
    private static final int TEXT=0xfff6f8ff;
    private static final int MUTED=0xffc4d0de;

    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private PlayPurchaseRestoreProbe restoreProbe;
    private TextView accessLabel, restoreStatus;
    private boolean stopped;

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String t(String english){return PlanUiText.t(this,english);}

    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private GradientDrawable heroBackground(){
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xff253f86,0xff202b50,0xff232536});
        d.setCornerRadius(dp(22));
        return d;
    }
    private LinearLayout column(){
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(t(value));
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(null,Typeface.BOLD);
        v.setIncludeFontPadding(true);
        return v;
    }
    private Button action(String text,int color,Runnable handler){
        Button button=new Button(this);
        button.setText(t(text));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setBackground(bg(color,13));
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(10),dp(7),dp(10),dp(7));
        button.setOnClickListener(v->handler.run());
        return button;
    }
    private void add(LinearLayout parent,View view,int spaceAfter){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(spaceAfter));
        parent.addView(view,lp);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(true);
        if(Build.VERSION.SDK_INT>=35){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                    WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
        }
        LinearLayout root=column();
        root.setPadding(dp(18),dp(17),dp(18),dp(26));
        root.setLayoutDirection(AppLanguage.layoutDirection(this));
        scroll.addView(root);
        setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35)scroll.requestApplyInsets();

        add(root,action("Back",CARD,this::finish),14);
        add(root,label("Your access & plans",23,TEXT,true),14);

        LinearLayout hero=column();
        hero.setPadding(dp(16),dp(19),dp(16),dp(17));
        hero.setBackground(heroBackground());
        add(hero,label("NO CARD · NO AUTOMATIC CHARGE",11,TEAL,true),9);
        add(hero,label("90 DAYS FREE",27,TEXT,true),8);
        add(hero,label("Try the current Prompt Overlay teleprompter features for 90 days.",
            15,TEXT,false),12);
        add(hero,label("This development preview does not enable paid features or purchases.",
            12,MUTED,false),12);
        add(hero,label("The 90 days start after your first verified Google sign-in, not when you install.",
            12,MUTED,false),12);

        accessLabel=label("",13,TEAL,true);
        add(hero,accessLabel,12);
        add(hero,action("Activate trial / Check status",BLUE,
            ()->startActivity(new Intent(this,TrialAccountActivity.class))),1);
        add(root,hero,18);

        add(root,label("AFTER YOUR FREE TRIAL",18,TEXT,true),7);
        add(root,label("The trial ends with no charge. Basic Lifetime is shown below as a development preview only; no offer is available to buy.",
            13,MUTED,false),14);

        for(PlanCatalog.Entry entry:PlanCatalog.previewDisplayOptions()){
            add(root,planCard(entry),11);
        }

        add(root,action("Check Play purchases (QA only)",CARD,
            this::checkPlayPurchases),7);
        restoreStatus=label("No purchases have been checked. Real purchase restoration is not enabled.",
            12,TEAL,false);
        add(root,restoreStatus,13);

        LinearLayout feedback=column();
        feedback.setPadding(dp(16),dp(16),dp(16),dp(16));
        feedback.setBackground(bg(CARD,18));
        add(feedback,label("Win a FREE Lifetime License from Us",17,TEXT,true),8);
        add(feedback,label("We will choose 500 users based on specific, actionable private feedback. Photos, videos and links are welcome.",
            13,MUTED,false),9);
        add(feedback,label("Google Play reviews are independent and do not affect selection.",
            12,TEAL,false),12);
        add(feedback,action("Send detailed feedback",BLUE,
            ()->startActivity(new Intent(this,ProFeedbackActivity.class))),0);
        add(root,feedback,19);

        add(root,label("Development preview · no payments",12,TEAL,true),4);
        add(root,label("No checkout or payment is enabled.",12,MUTED,false),0);

        updateAccountStatus();
    }

    private void checkPlayPurchases(){
        if(restoreProbe!=null){
            restoreProbe.close();
            restoreProbe=null;
        }
        restoreStatus.setText(t("Checking Google Play purchases for this test app…"));
        restoreProbe=new PlayPurchaseRestoreProbe(this);
        try{
            restoreProbe.check(result->{
                if(result.state!=PlayPurchaseRestoreProbe.State.QUERIED){
                    restoreStatus.setText(t(
                        "Google Play purchases are unavailable for this QA package."));
                }else if(result.purchased>0||result.pending>0){
                    restoreStatus.setText(
                        t("Google Play reported unverified purchases: ")+
                        result.purchased+" "+t("purchased, ")+result.pending+
                        " "+t("pending. No paid access was unlocked."));
                }else{
                    restoreStatus.setText(t(
                        "No Play purchases found for this test package. No paid access was unlocked."));
                }
            });
        }catch(RuntimeException failure){
            restoreStatus.setText(t(
                "Google Play purchases are unavailable for this QA package."));
        }
    }

    private LinearLayout planCard(PlanCatalog.Entry entry){
        LinearLayout card=column();
        card.setBackground(bg(CARD,18));
        card.setPadding(dp(16),dp(16),dp(16),dp(15));

        // The US target price is static preview copy and never comes from Play.
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Basic Lifetime",18,TEXT,true);
        LinearLayout.LayoutParams titleLayout=
            new LinearLayout.LayoutParams(0,-2,1);
        titleLayout.setMarginEnd(dp(12));
        row.addView(title,titleLayout);

        TextView price=label(PlanCardCopy.previewPriceLabel(entry),20,TEAL,true);
        price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        price.setMaxLines(1);
        row.addView(price,new LinearLayout.LayoutParams(-2,-2));
        add(card,row,4);

        TextView cadence=label(PlanCardCopy.cadenceLabel(entry),
            12,MUTED,false);
        cadence.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        cadence.setMaxLines(3);
        add(card,cadence,10);
        add(card,label(PlanCardCopy.previewAvailabilityLabel(),
            12,TEAL,true),8);
        add(card,label("All Basic teleprompter features forever.",13,MUTED,false),0);
        return card;
    }

    @Override protected void onResume(){
        super.onResume();
        if(accessLabel!=null)updateAccountStatus();
    }
    private void updateAccountStatus(){
        if(accessLabel==null)return;
        if(BuildConfig.DEBUG&&BuildConfig.APPLICATION_ID.endsWith(".proalpha21")){
            accessLabel.setText(t(
                "Side-by-side preview only. Your active 90-day trial remains in Alpha18."));
            return;
        }
        if(!TrialApi.configured()){
            accessLabel.setText(t(
                "Account service not connected yet. Trial activation is unavailable in this test build."));
            return;
        }
        String saved=new TrialSessionVault(this).restore();
        if(saved==null){
            accessLabel.setText(t("Sign in with Google to activate your trial."));
            return;
        }
        accessLabel.setText(t("Checking account status…"));
        background.execute(()->{
            String labelText;
            try{
                AccessPolicy.Decision d=TrialApi.readEntitlement(new TrialApi().entitlement(saved));
                switch(d.reason){
                    case TRIAL:
                        long day=86400000L;
                        long days=(d.trialRemainingMs+day-1)/day;
                        labelText=t("Your trial is active.")+" "+days+" "+t("days left");
                        break;
                    case BASIC_LIFETIME:case LEGACY_PAID_BASIC:
                        labelText=t("Basic Lifetime is active.");break;
                    case PRO_MONTHLY:
                        labelText=t("Pro Monthly is active.");break;
                    case PRO_LIFETIME:case FEEDBACK_AWARD_PRO_LIFETIME:
                        labelText=t("Pro Lifetime is active.");break;
                    case TRIAL_ENDED:
                        labelText=t("Trial completed. Your projects remain on your phone.");break;
                    default:labelText=t("Account status unavailable. Try again when the service is connected.");
                }
            }catch(Exception e){
                labelText=t("Account status unavailable. Try again when the service is connected.");
            }
            final String display=labelText;
            runOnUiThread(()->{
                if(!stopped&&!isFinishing()&&!isDestroyed()&&accessLabel!=null)
                    accessLabel.setText(display);
            });
        });
    }

    @Override protected void onDestroy(){
        stopped=true;
        if(restoreProbe!=null)restoreProbe.close();
        background.shutdownNow();
        super.onDestroy();
    }
}
