package com.costavong.promptoverlay.access;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Three planned paid entitlements, plus the no-card 90-day access trial.
 * The price targets are NOT Google Play checkout offers.
 *
 * Until the Google Play purchase verification backend is deployed, this
 * build must NOT start a purchase, acknowledge purchases or unlock paid
 * features in response to a client-only purchase result.
 */
public final class PlanCatalog {
    public enum Tier { BASIC_LIFETIME, PRO_MONTHLY, PRO_LIFETIME }

    public static final class Entry {
        public final Tier tier;
        public final String productId;
        public final String playProductType;
        public final String targetUsd;
        public final boolean renewsAfterPurchase;

        private Entry(Tier tier, String id, String productType,
                String price, boolean renews) {
            this.tier=tier;
            this.productId=id;
            this.playProductType=productType;
            this.targetUsd=price;
            this.renewsAfterPurchase=renews;
        }
    }

    public static final String PRODUCT_TYPE_INAPP="inapp";
    public static final String PRODUCT_TYPE_SUBS="subs";

    private static final List<Entry> OPTIONS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry(Tier.BASIC_LIFETIME, AccessPolicy.BASIC_PRODUCT,
                    PRODUCT_TYPE_INAPP, TrialOffer.BASIC_TARGET_USD, false),
            new Entry(Tier.PRO_MONTHLY, AccessPolicy.PRO_MONTHLY_PRODUCT,
                    PRODUCT_TYPE_SUBS, TrialOffer.PRO_MONTHLY_TARGET_USD, true),
            new Entry(Tier.PRO_LIFETIME, AccessPolicy.PRO_LIFETIME_PRODUCT,
                    PRODUCT_TYPE_INAPP, TrialOffer.PRO_LIFETIME_TARGET_USD, false)
        )
    );

    /** Hard-disabled until the purchase token can be verified on our server. */
    public static final boolean REAL_CHECKOUT_ENABLED=false;

    private PlanCatalog(){}

    public static List<Entry> options(){ return OPTIONS; }

    public static Entry byId(String productId){
        for(Entry option:OPTIONS)if(option.productId.equals(productId))return option;
        return null;
    }

    public static boolean mayStartCheckout(boolean accountVerified,
            boolean purchaseBackendReady, boolean productAvailable){
        // Explicitly deny paid checkout in this stage, regardless of any flags
        // passed by callers or product details reported by Play.
        return REAL_CHECKOUT_ENABLED && accountVerified
                && purchaseBackendReady && productAvailable;
    }
}
