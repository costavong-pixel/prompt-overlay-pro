package com.costavong.promptoverlay.access;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TrialOfferTest {
    @Test public void singleFullFeatureTrialLastsNinetyDays(){
        assertEquals(90,TrialOffer.TRIAL_DAYS);
        assertEquals(90L*24*60*60*1000,AccessPolicy.TRIAL_MILLIS);
        long first=1_900_000_000_000L;
        assertTrue(TrialOffer.hasFullTrial(first,first));
        assertTrue(TrialOffer.hasFullTrial(first,first+AccessPolicy.TRIAL_MILLIS-1));
        assertFalse(TrialOffer.hasFullTrial(first,first+AccessPolicy.TRIAL_MILLIS));
        assertFalse(TrialOffer.hasFullTrial(0,first));
    }
    @Test public void noPaymentMethodAndNoAutoRenewForTrial(){
        assertFalse(TrialOffer.CREDIT_CARD_REQUIRED);
        assertFalse(TrialOffer.AUTO_RENEW_TRIAL);
    }
    @Test public void exactlyThreePlannedUpgradeTypesAndFiveHundredAwards(){
        assertEquals("basic_lifetime",AccessPolicy.BASIC_PRODUCT);
        assertEquals("pro_monthly",AccessPolicy.PRO_MONTHLY_PRODUCT);
        assertEquals("pro_lifetime",AccessPolicy.PRO_LIFETIME_PRODUCT);
        assertEquals("$5.99",TrialOffer.BASIC_TARGET_USD);
        assertEquals("$4.99",TrialOffer.PRO_MONTHLY_TARGET_USD);
        assertEquals("$39.99",TrialOffer.PRO_LIFETIME_TARGET_USD);
        assertEquals(500,TrialOffer.FEEDBACK_LIFETIME_AWARDS);
    }
}
