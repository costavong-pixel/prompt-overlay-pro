package com.costavong.promptoverlay.access;

/** Pure display-label policy. No purchase or entitlement is granted here. */
public final class PlanCardCopy {
    private PlanCardCopy(){}

    public static String cadenceLabel(PlanCatalog.Entry entry){
        if(entry==null)throw new IllegalArgumentException("Plan is required");
        return entry.renewsAfterPurchase
            ?"Billed monthly after purchase · renews until canceled"
            :"One-time purchase · no renewal";
    }

    /** A USD target label for preview only; never sourced from Play checkout. */
    public static String previewPriceLabel(PlanCatalog.Entry entry){
        if(entry==null)throw new IllegalArgumentException("Plan is required");
        return "US"+entry.targetUsd;
    }

    public static String previewAvailabilityLabel(){
        return "Development preview · not an active purchase offer.";
    }
}
