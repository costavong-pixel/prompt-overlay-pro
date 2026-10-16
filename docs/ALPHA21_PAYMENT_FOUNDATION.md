# Alpha21 — Billing foundation (not a commercial release)

**Owner approval:** proceed with development while away; no production
deployment, billing charges, Google Play product setup, or Play listing changes.

## Agreed plan

| Product | Store ID | Play type | Target USD |
| --- | --- | --- | ---: |
| Basic Lifetime | `basic_lifetime` | One-time in-app | $5.99 |
| Pro Monthly | `pro_monthly` | Subscription | $4.99/month |
| Pro Lifetime | `pro_lifetime` | One-time in-app | $39.99 |

One 90-day **no-card**, **non-renewing** full Basic + Pro trial begins on
first verified Google sign-in and never resets from an app reinstall. No
14-day launch sale. The 500 Pro Lifetime winners are selected for detailed
**private** feedback, not Google Play ratings. Future metered cloud AI is not
included in current lifetime entitlements.

## Delivered in this branch

1. Android `PlayCheckoutCoordinator`: full Billing Library 9 class with
   product details query, **one unambiguous normal one-time offer or monthly
   base plan only**, server-derived `setObfuscatedAccountId` purchase identity,
   Play checkout request and purchase-update callback.
2. **Real checkout is hard-disabled**, by
   `PlanCatalog.REAL_CHECKOUT_ENABLED=false`, `BuildConfig.PRO_TEST`,
   and a production package-ID condition. The Alpha21 UI does not invoke
   the coordinator. **No Play checkout can be launched from this APK**.
3. Android `TrialApi` supports the future authenticated billing identity
   and claim-verification requests. It never grants Pro locally.
4. Server `GET /v1/billing/account-id`: after successful Google session,
   gives a SHA-256 account binding of an internal random UUID (never email).
   This identifier gets copied to Google Play purchase flow.
5. Server `POST /v1/purchases/verify`: accepts a claim **only when** a
   separately created `PlayPurchaseService` has been explicitly wired into
   the app; with no service the response is still **HTTP 503
   BILLING_NOT_ENABLED**. The **currently deployed** service is pinned to an
   earlier approved commit and has not been touched.
6. `GooglePlayReceiptReader`: Google-authenticated Publisher API
   `purchases.productsv2.getproductpurchasev2` and
   `purchases.subscriptionsv2.get`. Acknowledge through the correct
   server-only product/subscription route if the receipt was purchased and
   server-authorized. No mobile acknowledgement or credential storage.
7. `PlayPurchaseService`: validates exact app/product, pending/canceled/
   expired states, obfuscated account binding; acknowledges server-side
   and commits a token-hash-only record to isolated SQLite.
8. `PlayClaimLedger`: duplicate tokens cannot be assigned to a new account
   or a different SKU; revoked/voided records are permanent tombstones. No
   raw purchase token saved to SQLite. Recently checked, valid receipts only
   are accepted into a provisional access decision. Stale receipts fail
   closed pending RTDN and reconciliation.
9. Node tests cover API authentication, token reuse, expiry, pending, other
   accounts, acknowledgement failure, Google API errors and forged approved
   JSON; Android CI compiles the isolated future flow.
10. **No live payment** and no production purchase entitlement unlock is
    enabled. Changes are isolated in a draft PR.

## Required BEFORE activating payment processing

These are genuine blockers, not cosmetic tasks:

- Owner must create **two non-consumable one-time products** and **one monthly
  subscription** under the *production* Google Play package
  `com.costavong.promptoverlay`; set prices and activate them. They are not
  sold until the owner configures real product availability.
- Owner must configure authorized Google Play **license tester accounts**.
  A test APK under `com.costavong.promptoverlay.proalpha21` (or
  `.protest`) cannot purchase products belonging to the
  `com.costavong.promptoverlay` app.
- A Play-registered internal-test build for the **production package name**
  and stable upload/signing certificate is necessary for actual Play Billing
  test purchases. Use a separate test track; never touch the published stable
  binary without staged approval.
- Set Google Play Developer API permissions for a dedicated **server-only
  service identity**. The previously created Google OAuth WEB CLIENT ID is
  **not** a Play Developer API service account and cannot verify receipts.
  Do not ask for secrets, service-account JSON, signing keys or passwords in
  chat or commit them.
- Implement and independently review **RTDN Google Cloud Pub/Sub**,
  `purchases.voidedpurchases.list` reconciliation, secure pending
  acknowledgement retries, renewal/linked-token replacement, refunds and
  chargeback revocation, encrypted/durable recovery mechanisms, account
  switching, transaction idempotency, logging with sensitive-token redaction,
  durable server backups and disaster recovery.
- Replace the temporary/stale receipt acceptance interval with a reviewed
  production policy; monthly renewals should not unpredictably lose access
  when RTDN or the Play API is temporarily unavailable. A server notification
  and scheduled reconciliation strategy must be defined.
- Connect the verified server entitlements to actual Basic/Pro editor feature
  gates. The current QA app intentionally leaves editing unlocked for
  development; production must not trust local booleans.
- Complete subscription legal disclosures, privacy policy, Play Data safety
  update, first-use trial messaging, store listing, and refund support before
  selling to customers.

## QA APK safety

Until a protected **permanent QA keystore** is set up, this branch builds as
`com.costavong.promptoverlay.proalpha21` in debug. It installs **alongside**
Alpha18, Alpha19, and Alpha20, without overwriting their installed data. Google
Sign-In remains disabled for the temporary QA package; **use Alpha18 for your
already verified trial**. App plan cards remain read-only, prices are merely
proposed targets until Play returns published local prices, and the
**Check Play Purchases** readout never grants any entitlement.

## Evidence and references

- Google Play Billing — https://developer.android.com/google/play/billing/integrate
- Server verification — https://developer.android.com/google/play/billing/backend
- Product purchase status — https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2
- Subscription lifecycle — https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2
- Pending purchases, acknowledgment and refund — https://developer.android.com/google/play/billing/security

This is a **staged development milestone**. A CI-green build is NOT evidence
of real-money purchase, renewal, refunds, or protected Pro access.
