package com.costavong.promptoverlay.access;

/**
 * Launch offer approved for the free-download app.
 * This is informational UI metadata only, not a payment entitlement.
 * Real checkout amounts must always be obtained from Google Play Billing.
 */
public final class TrialOffer {
    public static final int TRIAL_DAYS=90;
    public static final boolean CREDIT_CARD_REQUIRED=false;
    public static final boolean AUTO_RENEW_TRIAL=false;
    public static final int FEEDBACK_LIFETIME_AWARDS=500;
    public static final String BASIC_TARGET_USD="$5.99";
    public static final String PRO_MONTHLY_TARGET_USD="$4.99";
    public static final String PRO_LIFETIME_TARGET_USD="$39.99";

    private TrialOffer(){}

    public static boolean hasFullTrial(long activatedAtMs,long serverNowMs){
        if(activatedAtMs<=0||serverNowMs<activatedAtMs)return false;
        return serverNowMs-activatedAtMs<AccessPolicy.TRIAL_MILLIS;
    }
}
