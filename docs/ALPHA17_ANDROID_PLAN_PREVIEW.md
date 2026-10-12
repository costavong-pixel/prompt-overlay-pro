# Alpha 17 — Android app first: trial and plan selector (test build)

The owner is away from the VPS and requested **build the Android app before
server deployment**. This branch builds an **installable Samsung ARM64 test APK**
without touching the other VPS apps, Docker, DNS, certificates or live Play
listing. The selected future trial hostname remains
`https://teleprompter-trail.barndai.com` (literal **trail** spelling).

## What this Android app now contains

- A compact **"90-Day Trial & Plans (preview)"** entry on the Pro home screen.
- A modern scrollable **Your access & plans** screen, in English, Spanish,
  French and Arabic, including Arabic right-to-left layout.
- Hero card emphasizing **90 days free of every current Basic and Pro feature,
  no credit card, no automatic trial charge**.
- Sign-in/status entry that launches the existing **TrialAccountActivity**.
  It keeps the verification status truthful if the backend is not connected.
- Three product cards: **Basic Lifetime** ($5.99 target),
  **Pro Monthly** ($4.99/month target, recurring only if actively bought)
  and **Pro Lifetime** ($39.99 target, current non-AI tools).
  Cards open actual in-app feature descriptions; no fake purchase dialogs.
- A separate **"Win a FREE Lifetime License from Us"** feedback invitation
  and entry to the existing photo/video/link feedback screen. The private
  feedback program will select 500 users based on useful details, not ratings.
- **Read-only** Google Play Billing Library 9 product discovery, using correct
  `inapp` product types for permanent grants and `subs` for monthly. If Play
  returns one unambiguous one-time offer or the monthly base-plan recurring
  price, display the localized price. Otherwise keep the US **target price**
  and state that the current test package has no Play offers available.

## Deliberately NOT implemented

- No card charge, in-app purchase initiation, renewal, payment receipt,
  acknowledgement, purchase restoration or lifetime unlock.
- No real 90-day activation without configured Google OAuth and deployed HTTPS
  server. Existing trial/account API code has no new server dependency.
- No enforced feature locking, no time limit in the **test editor APK**, and
  no speculative local unlock by manipulating SharedPreferences.
- No production pricing change or migration. Google Play Basic remains as
  published. There is **NO 14-day promotion**.
- The current published Play app and experimental Alpha17 are different
  package IDs; Alpha17 does not share user files/settings with Basic or
  previous alphas.

## Device acceptance test

1. Install side-by-side `com.costavong.promptoverlay.proalpha17`; open
   `Prompt Overlay Pro Test` and select `90-Day Trial & Plans (preview)`.
2. Verify title, 90-day/no-card wording, all three plan prices and features.
3. Tap each plan: feature descriptions must open; **no checkout** must open.
4. Tap Activate trial / Check status: until OAuth/backend configured, it must
   **report not configured**, never incorrectly start the 90-day countdown.
5. Tap the 500-winner button: private feedback screen must open; optional
   photos/videos/shareable link should remain available, and reviews should
   remain independent.
6. Change language on Pro home to French/Spanish/Arabic and confirm layout.
7. Test Back, portrait scrolling, open teleprompter, edit/import/export from
   existing editor. Existing Pro features must remain accessible for testing.
8. If Play's product catalog cannot be fetched in the debug-signed sideload,
   **this is expected**, not evidence that Play purchases are working or broken.

## Security/payment release gate

`PlanCatalog.REAL_CHECKOUT_ENABLED=false` and
`PlanCatalog.mayStartCheckout(...)` **always returns false**. No
`launchBillingFlow` or `acknowledgePurchase` invocation exists in this
branch's new code. A later billing branch must implement Google Play purchase
token verification on an authenticated server, refund/cancellation handling,
reconciliation/restore, proper test accounts, and user acceptance before it
enables real-money checkout. The current backend deliberately returns HTTP
503 `BILLING_NOT_ENABLED` for purchase verification. **Do not enable real
billing in Alpha17.**

## Build checks

The Alpha17 GitHub Action runs:
`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK signature
verification and 16 KiB ZIP alignment. An installable debug APK is published
as a GitHub prerelease after passing checks. CI cannot replace physical
Samsung UI testing or Google Play license-tester purchase testing.
