package com.costavong.promptoverlay.access;

import android.app.Activity;
import android.app.AlertDialog;
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
import com.costavong.promptoverlay.pro.ProFeedbackActivity;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android-first, no-payment preview of trial and three paid plans.
 *
 * All current editing actions remain available in the separate alpha test
 * APK for usability testing. This screen NEVER starts Google Play checkout,
 * overrides an entitlement or claims that a trial was activated without the
 * authenticated HTTPS backend.
 */
public final class PlansActivity extends Activity {
    private static final int BG=0xff11151e;
    private static final int CARD=0xff212938;
    private static final int BLUE=0xff4969ed;
    private static final int TEAL=0xff84dfcf;
    private static final int TEXT=0xfff6f8ff;
    private static final int MUTED=0xffc4d0de;

    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private final Map<String,TextView> priceLabels=new HashMap<>();
    private PlayPriceCatalog playCatalog;
    private TextView accessLabel, catalogLabel;
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
        add(hero,label("Try every current feature for 90 days — teleprompter, video editor, captions and export.",
            15,TEXT,false),12);
        add(hero,label("The 90 days start after your first verified Google sign-in, not when you install.",
            12,MUTED,false),12);

        accessLabel=label("",13,TEAL,true);
        add(hero,accessLabel,12);
        add(hero,action("Activate trial / Check status",BLUE,
            ()->startActivity(new Intent(this,TrialAccountActivity.class))),1);
        add(root,hero,18);

        add(root,label("AFTER YOUR FREE TRIAL",18,TEXT,true),7);
        add(root,label("Choose a plan only if you want to continue. Nothing is charged when the free trial ends.",
            13,MUTED,false),14);

        for(PlanCatalog.Entry entry:PlanCatalog.options()){
            add(root,planCard(entry),11);
        }

        TextView pricesHelp=label(
            "US target prices shown until live Google Play offers become available. Checkout is disabled.",
            12,MUTED,false);
        add(root,pricesHelp,8);
        catalogLabel=label("Google Play pricing is not available for this test package.",
            12,TEAL,false);
        add(root,catalogLabel,18);

        LinearLayout feedback=column();
        feedback.setPadding(dp(16),dp(16),dp(16),dp(16));
        feedback.setBackground(bg(CARD,18));
        add(feedback,label("Win a FREE Lifetime License from Us",17,TEXT,true),8);
        add(feedback,label("We will choose 500 users based on specific, actionable private feedback. Photos, videos and links are welcome.",
            13,MUTED,false),9);
        add(feedback,label("Google Play reviews are independent and do not affect selection.",
            12,TEAL,false),12);
        add(feedback,action("Win a FREE Lifetime License from Us",BLUE,
            ()->startActivity(new Intent(this,ProFeedbackActivity.class))),0);
        add(root,feedback,19);

        add(root,label("Development preview · no payments",12,TEAL,true),4);
        add(root,label("Plans are not for sale in this test build.",12,MUTED,false),0);

        updateAccountStatus();
        playCatalog=new PlayPriceCatalog(this);
        try{
            playCatalog.fetch((prices,missing,connected)->{
                for(Map.Entry<String,String> price:prices.entrySet()){
                    TextView view=priceLabels.get(price.getKey());
                    if(view!=null)view.setText(price.getValue());
                }
                catalogLabel.setText(
                    prices.isEmpty()
                        ?t("Google Play pricing is not available for this test package.")
                        :t("Price from Google Play")+" · "+prices.size()+"/"+PlanCatalog.options().size());
            });
        }catch(RuntimeException e){
            catalogLabel.setText(t("Google Play pricing is not available for this test package."));
        }
    }

    private LinearLayout planCard(PlanCatalog.Entry entry){
        LinearLayout card=column();
        card.setBackground(bg(CARD,18));
        card.setPadding(dp(16),dp(16),dp(16),dp(15));
        card.setContentDescription(t(planTitle(entry))+" · "+entry.targetUsd);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v->showDetails(entry));

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(planTitle(entry),18,TEXT,true);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout cost=column();
        TextView price=label(entry.targetUsd,20,TEAL,true);
        price.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        priceLabels.put(entry.productId,price);
        cost.addView(price,new LinearLayout.LayoutParams(-1,-2));
        TextView cadence=label(entry.renewsAfterPurchase
            ?"per month · renews after purchase":"one-time",11,MUTED,false);
        cadence.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        cost.addView(cadence,new LinearLayout.LayoutParams(-1,-2));
        row.addView(cost,new LinearLayout.LayoutParams(-2,-2));
        add(card,row,9);
        add(card,label(planSummary(entry),13,MUTED,false),10);
        add(card,label("See included features",12,TEAL,true),0);
        return card;
    }
    private String planTitle(PlanCatalog.Entry entry){
        switch(entry.tier){
            case BASIC_LIFETIME:return "Basic Lifetime";
            case PRO_MONTHLY:return "Pro Monthly";
            default:return "Pro Lifetime";
        }
    }
    private String planSummary(PlanCatalog.Entry entry){
        switch(entry.tier){
            case BASIC_LIFETIME:return "All Basic teleprompter features forever.";
            case PRO_MONTHLY:return "All current Pro tools while subscribed.";
            default:return "All current non-AI Pro tools permanently.";
        }
    }
    private void showDetails(PlanCatalog.Entry entry){
        String included=entry.tier==PlanCatalog.Tier.BASIC_LIFETIME
            ?"Basic includes scripts, formatting, imports, overlay controls and local remote features."
            :"Pro includes Basic plus the local four-track editor, trim/split, captions, graphics, music and up to 1080p export.";
        StringBuilder message=new StringBuilder(t(included));
        if(entry.tier==PlanCatalog.Tier.PRO_LIFETIME)
            message.append("\n\n").append(t(
                "Lifetime excludes future metered cloud AI services and credits."));
        message.append("\n\n").append(t("Plans are not for sale in this test build."));
        new AlertDialog.Builder(this)
            .setTitle(t(planTitle(entry)))
            .setMessage(message.toString())
            .setPositiveButton(t("Close"),null).show();
    }

    @Override protected void onResume(){
        super.onResume();
        if(accessLabel!=null)updateAccountStatus();
    }
    private void updateAccountStatus(){
        if(accessLabel==null)return;
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
        if(playCatalog!=null)playCatalog.close();
        background.shutdownNow();
        super.onDestroy();
    }
}
