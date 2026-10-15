# Alpha19 — Play Billing purchase/restore preflight (no money)

**Status:** Code and automated tests ONLY. No production Play checkout, feature
locks, product grants, server purchase-verification activation, or VPS changes.

## What was built

- Android QA plan screen adds **Check Play purchases (QA only)**. It queries
  `BillingClient.queryPurchasesAsync` for *owned* non-consumable in-app items and
  subscriptions, reports recognized/pending items, and **never grants access,
  acknowledges, logs or transmits tokens** based on a local Play response.
- Server adds an isolated, not-connected-to-live-API Google Play Publisher
  receipt reader using Google's authenticated service-account client and
  pinned production package ID `com.costavong.promptoverlay`. It only calls
  `purchases.productsv2.getproductpurchasev2` or
  `purchases.subscriptionsv2.get` over HTTPS.
- A pure decision policy checks a **server-fetched** purchase response:
  **purchased** rather than pending/cancelled; expected product ID and type;
  obfuscated account ID matching an app-specific hash of the internal user
  UUID; one-time non-consumed product; subscription in active/grace/canceled
  with a future expiry, excluding paused/on-hold/expired; acknowledgment
  state known. A purchase is **never trusted merely because Android says
  it is owned**.
- Server regression tests cover wrong user, pending, cancelled, consumed,
  mismatched product, zero/expired subscription, unverified server response,
  malicious token input and enforcing the correct Play package.

## Why the current QA APK cannot run real Google Play purchases

The Android QA package is `com.costavong.promptoverlay.protest` but the app
published on Play is `com.costavong.promptoverlay`. Google Play Billing test
purchases normally require the **same package name** as an app registered in
Play Console and authorized **license testers**. A sideloaded QA build using
a different package ID cannot pretend to be the published paid application.
The Play-catalog and purchase-check results may legitimately be unavailable
or show no products. **This is not a bug in the Play Billing API.**

To test real transactions safely, owner must:
1. First create/activate `basic_lifetime`, `pro_monthly`, `pro_lifetime`
   and the monthly subscription base plan in **Google Play Console** for the
   production package `com.costavong.promptoverlay`.
2. Configure specific **license tester** Google accounts. Install the app
   through an internal test track or use an appropriately Play-licensed
   sideloaded build **with exactly that Play package**.
3. Configure secure Google Play Developer API **service-account permissions**
   on the *isolated server* (not client OAuth Web ID). Do not put JSON keys
   or tokens into the GitHub repository, app, screenshots, or chat.
4. Implement authenticated `POST /v1/purchases/verify` that fetches the
   purchase token directly from Google, checks the *obfuscated account
   binding*, stores a token hash with an exclusive owner, grants only valid
   purchases, acknowledges safely on the server, and reconciles purchases
   using RTDN / periodic checks and voided/refunded records.
5. Implement a real Play checkout using localized `ProductDetails` and
   offer tokens and test purchase failure, pending, approval, restores,
   renewals, cancellation, refunds, account swaps, device replacement,
   promo codes for up to 500 private-feedback awards, and all trial limits.
6. Implement **feature gating in the Android app** with a trustworthy
   server entitlement, short-lived offline cache, and clear trial expiry
   behavior. Do not delete scripts or saved projects when access expires.

**No transaction flow can be safely activated until these steps pass.**
`PlanCatalog.REAL_CHECKOUT_ENABLED=false` remains hardcoded, and on the
currently **deployed pinned VPS** the purchase-verification route still returns
HTTP 503 `BILLING_NOT_ENABLED`. Changes in this branch are NOT deployed.

## QA signing — Alpha19 installs safely beside Alpha18

Alpha18 was built with an ephemeral GitHub runner debug certificate.
Alpha19 normally gets a **different** SHA-1. Android cannot upgrade an
installed app with a different signing certificate.

**Safety fix included in Alpha19:**

- Without a permanent QA signing key, Alpha19 automatically builds as
  `com.costavong.promptoverlay.proalpha19` with the label
  **Prompt Overlay A19 Preview**, so it installs **beside**, not over,
  the existing signed-in Alpha18 app. No uninstall, loss of local projects
  or restart of the server 90-day trial is necessary.
- The side-by-side purchase preview deliberately DISABLES Google Sign-In
  because this temporary `.proalpha19` package is not registered as an
  Android OAuth client. Continue Google account/90-day testing in the
  existing Alpha18 `com.costavong.promptoverlay.protest`.
- If private stable QA signing secrets are later available, this same
  branch builds with `com.costavong.promptoverlay.protest` instead,
  compatible with **future** signed builds using that stable certificate,
  but NOT as an in-place update for the earlier ephemeral-signed Alpha18.
  Preserve/export any Alpha18 local scripts/projects before uninstalling.

The real server's initial Google account trial date stays intact across
reinstalls; local app files/settings are separate.

To stop future signing changes, create a dedicated stable **nonproduction QA
signing key** owned by the business:

- Have a trusted local Codex/terminal session generate a JKS key privately,
  store a protected backup, and set four GitHub Actions secrets:
  `PROMPT_OVERLAY_QA_KEYSTORE_B64`, `PROMPT_OVERLAY_QA_STORE_PASS`,
  `PROMPT_OVERLAY_QA_KEY_ALIAS`, `PROMPT_OVERLAY_QA_KEY_PASS`.
- Register the NEW stable SHA-1 in Google Cloud for the permanent Android QA
  client `com.costavong.promptoverlay.protest`.
- Do not expose any keystore/key/password in PR, chat, GitHub commits, or logs,
  and do not reuse production Play signing identities.

## CI requirements

Run Java unit tests, Gradle lint, APK compilation and signature/16K checks,
Node server policy tests, and inspect exact commit SHA. CI passing is NOT
a real-money purchase test or proof of secure entitlement grants.

## Primary Google guidance

- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/backend
- https://developer.android.com/google/play/billing/test
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2/getproductpurchasev2
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get
