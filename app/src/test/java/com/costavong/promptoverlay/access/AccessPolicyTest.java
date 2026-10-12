package com.costavong.promptoverlay.access;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AccessPolicyTest {
    private static final long DAY=24L*60*60*1000;
    private static final long START=2_000_000_000_000L;

    private AccessPolicy.VerifiedSnapshot snapshot(long now,long trial,
            boolean basic,boolean legacy,boolean pro,boolean award,long monthly) {
        return new AccessPolicy.VerifiedSnapshot(true,now,trial,basic,legacy,pro,award,monthly);
    }

    @Test public void unverifiedResponseCannotGrantAccess(){
        AccessPolicy.VerifiedSnapshot fake=new AccessPolicy.VerifiedSnapshot(
                false,START,START,true,true,true,true,START+100*DAY);
        AccessPolicy.Decision access=AccessPolicy.decide(fake);
        assertEquals(AccessPolicy.Level.NONE,access.level);
        assertEquals(AccessPolicy.Reason.NEEDS_VERIFICATION,access.reason);
        assertFalse(access.canUseAllCurrentProFeatures());
    }

    @Test public void allFeaturesUnlockedForExactlyNinetyDays(){
        AccessPolicy.Decision first=AccessPolicy.decide(snapshot(START,START,false,false,false,false,0));
        assertEquals(AccessPolicy.Reason.TRIAL,first.reason);
        assertEquals(AccessPolicy.TRIAL_MILLIS,first.trialRemainingMs);
        assertTrue(first.canUseAllCurrentProFeatures());
        AccessPolicy.Decision last=AccessPolicy.decide(snapshot(START+90*DAY-1,START,false,false,false,false,0));
        assertEquals(AccessPolicy.Reason.TRIAL,last.reason);
        assertEquals(1L,last.trialRemainingMs);
        AccessPolicy.Decision expired=AccessPolicy.decide(snapshot(START+90*DAY,START,false,false,false,false,0));
        assertEquals(AccessPolicy.Level.NONE,expired.level);
        assertEquals(AccessPolicy.Reason.TRIAL_ENDED,expired.reason);
    }

    @Test public void withoutServerActivatedTrialThereIsNoFreePro(){
        assertEquals(AccessPolicy.Level.NONE,
            AccessPolicy.decide(snapshot(START,0,false,false,false,false,0)).level);
        assertEquals(AccessPolicy.Level.NONE,
            AccessPolicy.decide(snapshot(START,START+DAY,false,false,false,false,0)).level);
    }

    @Test public void basicPurchaseSurvivesTrialExpiration(){
        AccessPolicy.Decision result=AccessPolicy.decide(
                snapshot(START+95*DAY,START,true,false,false,false,0));
        assertEquals(AccessPolicy.Level.BASIC,result.level);
        assertTrue(result.canUseBasic());
        assertFalse(result.canUseAllCurrentProFeatures());
    }

    @Test public void grandfatherPreviouslyPaidDownload(){
        AccessPolicy.Decision result=AccessPolicy.decide(
                snapshot(START+95*DAY,START,false,true,false,false,0));
        assertEquals(AccessPolicy.Reason.LEGACY_PAID_BASIC,result.reason);
        assertTrue(result.canUseBasic());
        assertFalse(result.canUseAllCurrentProFeatures());
    }

    @Test public void monthlyActiveUntilVerifiedExpiryThenRevertsToBasic(){
        AccessPolicy.Decision active=AccessPolicy.decide(
                snapshot(START+100*DAY,START,true,false,false,false,START+101*DAY));
        assertEquals(AccessPolicy.Reason.PRO_MONTHLY,active.reason);
        assertTrue(active.canUseAllCurrentProFeatures());
        AccessPolicy.Decision after=AccessPolicy.decide(
                snapshot(START+101*DAY,START,true,false,false,false,START+101*DAY));
        assertEquals(AccessPolicy.Reason.BASIC_LIFETIME,after.reason);
    }

    @Test public void grantedLifetimeAndPurchasedLifetimeNeverExpire(){
        assertEquals(AccessPolicy.Reason.PRO_LIFETIME,
            AccessPolicy.decide(snapshot(START+900*DAY,START,false,false,true,false,0)).reason);
        assertEquals(AccessPolicy.Reason.FEEDBACK_AWARD_PRO_LIFETIME,
            AccessPolicy.decide(snapshot(START+900*DAY,START,false,false,false,true,0)).reason);
    }

    @Test public void lifetimeBeatsSubscriptionAndTrial(){
        assertEquals(AccessPolicy.Reason.PRO_LIFETIME,
            AccessPolicy.decide(snapshot(START+DAY,START,true,true,true,true,START+200*DAY)).reason);
    }

    @Test public void privacyFeedbackAlwaysAccessibleEvenAfterTrial(){
        assertTrue(AccessPolicy.decide(snapshot(START+100*DAY,START,false,false,false,false,0))
            .canOpenFeedback());
    }
}
