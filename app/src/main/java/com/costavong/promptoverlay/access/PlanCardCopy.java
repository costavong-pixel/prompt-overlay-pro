package com.costavong.promptoverlay.access;

/** Pure display-label policy. No purchase or entitlement is granted here. */
public final class PlanCardCopy {
    private PlanCardCopy(){}

    public static String cadenceLabel(PlanCatalog.Entry entry){
        if(entry==null)throw new IllegalArgumentException("Plan is required");
        return entry.renewsAfterPurchase
            ?"Billed monthly after purchase · renews until canceled"
            :"One-time payment · no renewal";
    }
}
