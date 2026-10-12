package com.costavong.promptoverlay.access;

import android.app.Activity;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * READ-ONLY Google Play Billing 9 catalog discovery for the alpha plan screen.
 *
 * Never launchBillingFlow, acknowledge, consume, infer entitlements, store a
 * purchase token, or display a purchase button from this class. User purchase
 * verification and ownership restoration need a separate, reviewed backend.
 */
public final class PlayPriceCatalog {
    public interface Listener {
        void onResult(Map<String,String> localizedPrices, int missingProducts,
                boolean playConnected);
    }

    private final Activity activity;
    private final BillingClient client;
    private boolean closed;

    public PlayPriceCatalog(Activity activity){
        this.activity=activity;
        client=BillingClient.newBuilder(activity.getApplicationContext())
            .setListener((billingResult,purchases)->{
                // Read-only stage; purchases are intentionally NEVER trusted
                // or granted by this callback.
            })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    public void fetch(Listener callback){
        if(closed)return;
        client.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result){
                if(closed)return;
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                    report(callback,new HashMap<>(),PlanCatalog.options().size(),false);
                    return;
                }
                queryProducts(callback);
            }
            @Override public void onBillingServiceDisconnected(){
                // Automatic service reconnection is enabled; a later screen
                // visit starts a new explicit product query if needed.
            }
        });
    }

    private void queryProducts(Listener callback){
        List<QueryProductDetailsParams.Product> products=new ArrayList<>();
        for(PlanCatalog.Entry item:PlanCatalog.options()){
            products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(item.productId)
                .setProductType(item.playProductType)
                .build());
        }
        QueryProductDetailsParams request=QueryProductDetailsParams.newBuilder()
            .setProductList(products).build();
        client.queryProductDetailsAsync(request,(result,response)->{
            if(closed)return;
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                report(callback,new HashMap<>(),products.size(),false);
                return;
            }
            Map<String,String> prices=new HashMap<>();
            for(ProductDetails details:response.getProductDetailsList()){
                PlanCatalog.Entry known=PlanCatalog.byId(details.getProductId());
                if(known==null||!known.playProductType.equals(details.getProductType()))
                    continue;
                String price=displayPrice(details,known.playProductType);
                if(price!=null&&!price.isEmpty())prices.put(known.productId,price);
            }
            int missing=PlanCatalog.options().size()-prices.size();
            report(callback,prices,missing,true);
        });
    }

    /**
     * For subscriptions, show a matching monthly BASE plan's recurring price,
     * not an introductory/discounted billing phase.
     * For one-time products, display a price only if Play returns one
     * unambiguous user-eligible offer.
     */
    private String displayPrice(ProductDetails details,String type){
        if(PlanCatalog.PRODUCT_TYPE_INAPP.equals(type)){
            List<ProductDetails.OneTimePurchaseOfferDetails> offers=
                details.getOneTimePurchaseOfferDetailsList();
            if(offers!=null&&offers.size()==1)
                return offers.get(0).getFormattedPrice();
            return null;
        }
        List<ProductDetails.SubscriptionOfferDetails> offers=
            details.getSubscriptionOfferDetails();
        if(offers==null)return null;
        for(ProductDetails.SubscriptionOfferDetails offer:offers){
            if(offer.getOfferId()!=null)continue; // regular base plan only
            List<ProductDetails.PricingPhase> phases=
                offer.getPricingPhases().getPricingPhaseList();
            if(phases==null||phases.isEmpty())continue;
            ProductDetails.PricingPhase recurring=phases.get(phases.size()-1);
            if("P1M".equals(recurring.getBillingPeriod()))
                return recurring.getFormattedPrice();
        }
        return null;
    }

    private void report(Listener callback,Map<String,String> prices,int missing,boolean ok){
        activity.runOnUiThread(()->{
            if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())
                callback.onResult(new HashMap<>(prices),missing,ok);
        });
    }

    public void close(){
        closed=true;
        client.endConnection();
    }
}
