package com.costavong.promptoverlay.access;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlanCardCopyTest {
    @Test public void monthlyBillingSubtitleIsReadable(){
        PlanCatalog.Entry monthly=PlanCatalog.byId("pro_monthly");
        assertTrue(PlanCardCopy.cadenceLabel(monthly).contains("monthly"));
        assertTrue(PlanCardCopy.cadenceLabel(monthly).contains("until canceled"));
        assertTrue(monthly.renewsAfterPurchase);
    }
    @Test public void lifetimeBillingSubtitleIsOneTimeOnly(){
        for(String id:new String[]{"basic_lifetime","pro_lifetime"}){
            PlanCatalog.Entry lifetime=PlanCatalog.byId(id);
            assertFalse(lifetime.renewsAfterPurchase);
            assertEquals("One-time purchase · no renewal",PlanCardCopy.cadenceLabel(lifetime));
        }
    }

    @Test public void basicTargetPriceIsClearlyUsDollarPreviewCopy(){
        PlanCatalog.Entry basic=PlanCatalog.byId("basic_lifetime");
        assertEquals("US$5.99",PlanCardCopy.previewPriceLabel(basic));
        assertEquals("Development preview · not an active purchase offer.",
                PlanCardCopy.previewAvailabilityLabel());
        assertEquals("$4.99",PlanCatalog.byId("pro_monthly").targetUsd);
        assertEquals("$39.99",PlanCatalog.byId("pro_lifetime").targetUsd);
    }
    @Test public void ninetyDayTrialHasNoCardOrAutoRenewal(){
        assertEquals(90,TrialOffer.TRIAL_DAYS);
        assertFalse(TrialOffer.CREDIT_CARD_REQUIRED);
        assertFalse(TrialOffer.AUTO_RENEW_TRIAL);
        assertFalse(PlanCatalog.REAL_CHECKOUT_ENABLED);
    }
}
