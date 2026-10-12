package com.costavong.promptoverlay.access;

/**
 * Pure entitlement policy for the planned free-download app. This class does
 * not determine who paid: all values must originate in a verified server-side
 * entitlement snapshot. Never construct a paid entitlement from local prefs.
 */
public final class AccessPolicy {
    public static final long TRIAL_MILLIS = 90L * 24 * 60 * 60 * 1000;
    public static final String BASIC_PRODUCT = "basic_lifetime";
    public static final String PRO_MONTHLY_PRODUCT = "pro_monthly";
    public static final String PRO_LIFETIME_PRODUCT = "pro_lifetime";

    public enum Level { NONE, BASIC, PRO }
    public enum Reason {
        NEEDS_VERIFICATION, TRIAL, BASIC_LIFETIME, LEGACY_PAID_BASIC,
        PRO_MONTHLY, PRO_LIFETIME, FEEDBACK_AWARD_PRO_LIFETIME, TRIAL_ENDED
    }

    /**
     * An input contract, NOT proof of a purchase. The caller must verify
     * authenticity and expiry of the server response before passing it here.
     *
     * trialActivatedAtMs: trusted server-side timestamp at first activation.
     * monthlyAccessUntilMs: backend-verified subscription entitlement expiry.
     * All permanent booleans require backend ownership/promo verification.
     */
    public static final class VerifiedSnapshot {
        public final boolean authenticatedAndVerified;
        public final long serverNowMs;
        public final long trialActivatedAtMs;
        public final boolean ownsBasicLifetime;
        public final boolean previouslyPurchasedPaidBasic;
        public final boolean ownsProLifetime;
        public final boolean ownsFeedbackAward;
        public final long monthlyAccessUntilMs;

        public VerifiedSnapshot(boolean authenticatedAndVerified,long serverNowMs,
                long trialActivatedAtMs,boolean ownsBasicLifetime,
                boolean previouslyPurchasedPaidBasic,boolean ownsProLifetime,
                boolean ownsFeedbackAward,long monthlyAccessUntilMs) {
            this.authenticatedAndVerified=authenticatedAndVerified;
            this.serverNowMs=serverNowMs;
            this.trialActivatedAtMs=trialActivatedAtMs;
            this.ownsBasicLifetime=ownsBasicLifetime;
            this.previouslyPurchasedPaidBasic=previouslyPurchasedPaidBasic;
            this.ownsProLifetime=ownsProLifetime;
            this.ownsFeedbackAward=ownsFeedbackAward;
            this.monthlyAccessUntilMs=monthlyAccessUntilMs;
        }
    }

    public static final class Decision {
        public final Level level;
        public final Reason reason;
        public final long trialRemainingMs;
        private Decision(Level level,Reason reason,long trialRemainingMs) {
            this.level=level;this.reason=reason;this.trialRemainingMs=trialRemainingMs;
        }
        public boolean canUseBasic(){return level!=Level.NONE;}
        public boolean canUseAllCurrentProFeatures(){return level==Level.PRO;}
        public boolean canOpenFeedback(){return true;}
    }

    private AccessPolicy(){}

    public static Decision decide(VerifiedSnapshot s) {
        if(s==null||!s.authenticatedAndVerified||s.serverNowMs<=0)
            return new Decision(Level.NONE,Reason.NEEDS_VERIFICATION,0);
        // Lifetime always wins, including selected feedback participants.
        if(s.ownsProLifetime)
            return new Decision(Level.PRO,Reason.PRO_LIFETIME,0);
        if(s.ownsFeedbackAward)
            return new Decision(Level.PRO,Reason.FEEDBACK_AWARD_PRO_LIFETIME,0);
        if(s.monthlyAccessUntilMs>s.serverNowMs)
            return new Decision(Level.PRO,Reason.PRO_MONTHLY,0);

        // Only server-created trial starts count. Reinstall must not restart it.
        if(s.trialActivatedAtMs>0 && s.trialActivatedAtMs<=s.serverNowMs) {
            long elapsed=s.serverNowMs-s.trialActivatedAtMs;
            if(elapsed>=0 && elapsed<TRIAL_MILLIS)
                return new Decision(Level.PRO,Reason.TRIAL,TRIAL_MILLIS-elapsed);
        }
        // Existing Basic purchasers must not lose access after free conversion.
        if(s.previouslyPurchasedPaidBasic)
            return new Decision(Level.BASIC,Reason.LEGACY_PAID_BASIC,0);
        if(s.ownsBasicLifetime)
            return new Decision(Level.BASIC,Reason.BASIC_LIFETIME,0);
        return new Decision(Level.NONE,Reason.TRIAL_ENDED,0);
    }
}
