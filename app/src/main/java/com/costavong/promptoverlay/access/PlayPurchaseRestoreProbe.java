package com.costavong.promptoverlay.access;

import android.app.Activity;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Read-only Google Play ownership probe for the QA package.
 *
 * Local purchases are NOT server-verified receipts, and this class never
 * unlocks anything. It neither logs, transmits, nor persists purchase tokens.
 * Production restoration requires authenticated backend verification,
 * purchase-token ownership checks and Google Developer API reconciliation.
 */
public final class PlayPurchaseRestoreProbe {
    public interface Listener {
        void onFinished(Result result);
    }
    public enum State { QUERIED, PLAY_UNAVAILABLE, DISCONNECTED }

    public static final class Result {
        public final State state;
        public final int purchased;
        public final int pending;
        public final int unknownProducts;
        public final boolean accessGranted;
        private Result(State state,int purchased,int pending,int unknownProducts){
            this.state=state;
            this.purchased=purchased;
            this.pending=pending;
            this.unknownProducts=unknownProducts;
            this.accessGranted=false;
        }
    }

    private final Activity activity;
    private final BillingClient billing;
    private boolean closed,started;

    public PlayPurchaseRestoreProbe(Activity activity){
        this.activity=activity;
        billing=BillingClient.newBuilder(activity.getApplicationContext())
            .setListener((billingResult,purchases)->{
                // This is only a read-only query. No checkout is launched.
            })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    public void check(Listener callback){
        if(closed||started)return;
        started=true;
        billing.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result){
                if(closed)return;
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                    finish(callback,new Result(State.PLAY_UNAVAILABLE,0,0,0));
                    return;
                }
                query(BillingClient.ProductType.INAPP,callback,new ArrayList<>());
            }
            @Override public void onBillingServiceDisconnected(){
                // Service reconnect is enabled. A fresh view can run again.
            }
        });
    }

    private void query(String type,Listener callback,List<Purchase> combined){
        if(closed)return;
        QueryPurchasesParams params=QueryPurchasesParams.newBuilder()
            .setProductType(type).build();
        billing.queryPurchasesAsync(params,(result,purchases)->{
            if(closed)return;
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                finish(callback,new Result(State.PLAY_UNAVAILABLE,0,0,0));
                return;
            }
            if(purchases!=null)combined.addAll(purchases);
            if(BillingClient.ProductType.INAPP.equals(type)){
                query(BillingClient.ProductType.SUBS,callback,combined);
                return;
            }
            finish(callback,count(combined));
        });
    }

    private static Result count(List<Purchase> purchases){
        int purchased=0,pending=0,unknown=0;
        Set<String> found=new HashSet<>();
        // No token is transferred or stored, even in a hash.
        for(Purchase purchase:purchases){
            for(String productId:purchase.getProducts()){
                String unique=productId+":"+purchase.getPurchaseState();
                if(!found.add(unique))continue;
                if(PlanCatalog.byId(productId)==null){
                    unknown++;continue;
                }
                if(purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED)
                    purchased++;
                else if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING)
                    pending++;
            }
        }
        return new Result(State.QUERIED,purchased,pending,unknown);
    }

    private void finish(Listener callback,Result result){
        activity.runOnUiThread(()->{
            if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())
                callback.onFinished(result);
        });
    }

    public void close(){
        closed=true;
        billing.endConnection();
    }
}
