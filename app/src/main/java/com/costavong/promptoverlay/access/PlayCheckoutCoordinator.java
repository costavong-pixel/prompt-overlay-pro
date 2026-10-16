package com.costavong.promptoverlay.access;

import android.app.Activity;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.costavong.promptoverlay.BuildConfig;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Play Billing 9 checkout integration, deliberately DISABLED in Alpha21.
 *
 * No screen calls requestPurchase in this test APK. Product IDs must exist
 * in Play Console, the app package must be the same package registered on
 * Play, and the backend must be able to verify & acknowledge purchase tokens.
 *
 * Future production activation requires a security review removing all
 * test-package shortcuts; never grant access from a local purchase callback.
 */
public final class PlayCheckoutCoordinator {
    public enum Result {
        NOT_ENABLED, ACCOUNT_REQUIRED, UNAVAILABLE, USER_CANCELED,
        PAYMENT_PENDING, SERVER_VERIFICATION_REQUIRED, VERIFIED
    }
    public interface Listener {
        void onResult(Result result, String message);
    }

    private final Activity activity;
    private final ExecutorService requests=Executors.newSingleThreadExecutor();
    private final BillingClient play;
    private Listener activeListener;
    private String activeSession,activeProduct;
    private boolean closed;

    public PlayCheckoutCoordinator(Activity activity) {
        this.activity=activity;
        play=BillingClient.newBuilder(activity.getApplicationContext())
            .setListener(this::onPurchasesUpdated)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    private void tell(Result result,String message){
        activity.runOnUiThread(()->{
            if(!closed&&!activity.isDestroyed()&&!activity.isFinishing()&&activeListener!=null)
                activeListener.onResult(result,message);
        });
    }

    /**
     * This method is fail-closed even when called directly by an activity.
     * A production package with an explicitly reviewed release flag and a
     * verified backend is required before any purchase UI can be launched.
     */
    public void requestPurchase(PlanCatalog.Entry entry,Listener listener){
        if(listener==null)return;
        activeListener=listener;
        if(!PlanCatalog.REAL_CHECKOUT_ENABLED || BuildConfig.PRO_TEST
           ||!"com.costavong.promptoverlay".equals(BuildConfig.APPLICATION_ID)){
            tell(Result.NOT_ENABLED,"Payments are not enabled in this test build.");
            return;
        }
        if(entry==null||PlanCatalog.byId(entry.productId)!=entry){
            tell(Result.UNAVAILABLE,"Unknown product");return;
        }
        final String session=new TrialSessionVault(activity).restore();
        if(session==null){
            tell(Result.ACCOUNT_REQUIRED,"Sign in to connect purchases to your account.");
            return;
        }
        activeSession=session;
        activeProduct=entry.productId;

        // Account ID comes from authenticated server, not a guessed phone
        // identifier or a Google e-mail address. No Client Secret is needed.
        requests.execute(()->{
            try{
                JSONObject identity=new TrialApi().billingIdentity(session);
                String userBinding=identity.optString("obfuscatedAccountId","");
                if(!userBinding.matches("[a-f0-9]{64}")){
                    tell(Result.ACCOUNT_REQUIRED,"Cannot verify billing account.");return;
                }
                activity.runOnUiThread(()->queryOffer(entry,userBinding));
            }catch(Exception e){
                tell(Result.ACCOUNT_REQUIRED,"Billing sign-in verification unavailable.");
            }
        });
    }

    private void queryOffer(PlanCatalog.Entry entry,String accountHash){
        if(closed)return;
        play.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult status){
                if(closed)return;
                if(status.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                    tell(Result.UNAVAILABLE,"Google Play is not available.");return;
                }
                QueryProductDetailsParams.Product product=
                    QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(entry.productId)
                    .setProductType(entry.playProductType).build();
                QueryProductDetailsParams request=QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(product)).build();
                play.queryProductDetailsAsync(request,(result,response)->{
                    if(closed)return;
                    if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK
                        ||response.getProductDetailsList().size()!=1){
                        tell(Result.UNAVAILABLE,"This Play product is not available.");
                        return;
                    }
                    ProductDetails details=response.getProductDetailsList().get(0);
                    if(!entry.productId.equals(details.getProductId())
                       ||!entry.playProductType.equals(details.getProductType())){
                        tell(Result.UNAVAILABLE,"Product mismatch.");return;
                    }
                    String offerToken=regularOfferToken(details,entry);
                    if(offerToken==null){
                        tell(Result.UNAVAILABLE,"Cannot identify a standard purchase offer.");
                        return;
                    }
                    BillingFlowParams.ProductDetailsParams line=
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details).setOfferToken(offerToken).build();
                    BillingFlowParams params=BillingFlowParams.newBuilder()
                        .setObfuscatedAccountId(accountHash)
                        .setProductDetailsParamsList(Collections.singletonList(line))
                        .build();
                    // The actual launch happens on the main UI thread.
                    activity.runOnUiThread(()->{
                        if(closed)return;
                        BillingResult started=play.launchBillingFlow(activity,params);
                        if(started.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                            tell(Result.UNAVAILABLE,"Google Play did not start checkout.");
                        }
                    });
                });
            }
            @Override public void onBillingServiceDisconnected(){
                tell(Result.UNAVAILABLE,"Google Play connection lost.");
            }
        });
    }

    private static String regularOfferToken(ProductDetails details,PlanCatalog.Entry entry){
        if("inapp".equals(entry.playProductType)){
            List<ProductDetails.OneTimePurchaseOfferDetails> offers=
                details.getOneTimePurchaseOfferDetailsList();
            if(offers==null||offers.size()!=1)return null;
            ProductDetails.OneTimePurchaseOfferDetails offer=offers.get(0);
            if(offer.getRentalDetails()!=null||offer.getPreorderDetails()!=null
                ||offer.getOfferId()!=null)return null;
            return offer.getOfferToken();
        }
        List<ProductDetails.SubscriptionOfferDetails> offers=
            details.getSubscriptionOfferDetails();
        if(offers==null)return null;
        String token=null;
        for(ProductDetails.SubscriptionOfferDetails offer:offers){
            if(offer.getOfferId()!=null)continue; // no promotional trial
            List<ProductDetails.PricingPhase> phases=
                offer.getPricingPhases().getPricingPhaseList();
            if(phases==null||phases.size()!=1)continue;
            ProductDetails.PricingPhase recurring=phases.get(0);
            if(!"P1M".equals(recurring.getBillingPeriod()))continue;
            if(token!=null)return null; // ambiguous base plan, no guess
            token=offer.getOfferToken();
        }
        return token;
    }

    private void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){
        if(closed||activeListener==null||activeSession==null||activeProduct==null)
            return;
        if(result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED){
            tell(Result.USER_CANCELED,"Purchase canceled.");return;
        }
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||purchases==null){
            tell(Result.UNAVAILABLE,"Google Play purchase was not completed.");
            return;
        }
        for(Purchase purchase:purchases){
            if(!purchase.getProducts().contains(activeProduct))continue;
            if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING){
                tell(Result.PAYMENT_PENDING,"Pending payment: no features unlocked.");
                continue;
            }
            if(purchase.getPurchaseState()!=Purchase.PurchaseState.PURCHASED)continue;
            final String token=purchase.getPurchaseToken();
            final String productId=activeProduct;
            final String session=activeSession;
            // No Android-side acknowledgement or local entitlement flags.
            requests.execute(()->{
                try{
                    if(!session.equals(new TrialSessionVault(activity).restore())){
                        tell(Result.ACCOUNT_REQUIRED,"Account changed; sign in again.");
                        return;
                    }
                    JSONObject verified=new TrialApi().verifyPurchase(session,productId,token);
                    if(verified.optBoolean("verified",false)){
                        // The caller will refresh signed-in server entitlements.
                        tell(Result.VERIFIED,"Purchase verified by server.");
                    }else{
                        tell(Result.SERVER_VERIFICATION_REQUIRED,
                             "Payment needs server verification; no access granted yet.");
                    }
                }catch(Exception error){
                    tell(Result.SERVER_VERIFICATION_REQUIRED,
                         "Payment needs server verification; no access granted yet.");
                }
            });
        }
    }

    public void close(){
        closed=true;
        activeListener=null;
        requests.shutdownNow();
        play.endConnection();
    }
}
