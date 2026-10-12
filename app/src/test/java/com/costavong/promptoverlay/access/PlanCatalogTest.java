package com.costavong.promptoverlay.access;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class PlanCatalogTest {
    @Test public void exactlyThreeDistinctGooglePlayProducts(){
        assertEquals(3,PlanCatalog.options().size());
        Set<String> ids=new HashSet<>();
        for(PlanCatalog.Entry entry:PlanCatalog.options()){
            assertTrue(ids.add(entry.productId));
            assertSame(entry,PlanCatalog.byId(entry.productId));
        }
        assertEquals(3,ids.size());
        assertNull(PlanCatalog.byId("invented_lifetime"));
        assertNull(PlanCatalog.byId(null));
    }

    @Test public void lifetimeProductsAreOneTimeOnly(){
        PlanCatalog.Entry basic=PlanCatalog.byId("basic_lifetime");
        PlanCatalog.Entry pro=PlanCatalog.byId("pro_lifetime");
        assertEquals(PlanCatalog.PRODUCT_TYPE_INAPP,basic.playProductType);
        assertEquals(PlanCatalog.PRODUCT_TYPE_INAPP,pro.playProductType);
        assertFalse(basic.renewsAfterPurchase);
        assertFalse(pro.renewsAfterPurchase);
        assertEquals("$5.99",basic.targetUsd);
        assertEquals("$39.99",pro.targetUsd);
    }

    @Test public void onlyExplicitMonthlyPurchaseWouldRenew(){
        PlanCatalog.Entry monthly=PlanCatalog.byId("pro_monthly");
        assertEquals(PlanCatalog.PRODUCT_TYPE_SUBS,monthly.playProductType);
        assertTrue(monthly.renewsAfterPurchase);
        assertEquals("$4.99",monthly.targetUsd);
        assertFalse(TrialOffer.AUTO_RENEW_TRIAL);
        assertFalse(TrialOffer.CREDIT_CARD_REQUIRED);
    }

    @Test public void previewCannotOpenBillingEvenIfClientClaimsEverythingReady(){
        assertFalse(PlanCatalog.REAL_CHECKOUT_ENABLED);
        for(boolean signedIn:new boolean[]{false,true})
            for(boolean backendReady:new boolean[]{false,true})
                for(boolean playProductLoaded:new boolean[]{false,true})
                    assertFalse(PlanCatalog.mayStartCheckout(
                        signedIn,backendReady,playProductLoaded));
    }

    @Test public void allFeaturesRemainA90DayTrialNotAPromotionalSale(){
        assertEquals(90,TrialOffer.TRIAL_DAYS);
        assertEquals(90L*24*60*60*1000,AccessPolicy.TRIAL_MILLIS);
        assertEquals(500,TrialOffer.FEEDBACK_LIFETIME_AWARDS);
    }
}
