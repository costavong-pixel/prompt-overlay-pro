# Alpha22 — Google Play refund reconciliation and API preflight

Status: **DRAFT / SERVER TESTS ONLY. DO NOT DEPLOY.**

The owner has created/authorized a Google Cloud service identity for
Prompt Overlay, and three Google Play plans:
`basic_lifetime` (`basic-buy`), `pro_lifetime` (`pro-buy`), and
`pro_monthly` (monthly base plan). The live VPS has **not** been given
Google Play Developer API credentials and we have **not** activated any real
checkout or paid entitlement grants.

## What is new

- Authenticated Google Publisher `purchases.voidedpurchases.list` reader
  pinned to `com.costavong.promptoverlay`. Requests include **type=1** to
  include one-time products and subscription renewals. Fetches each page
  using Google's `pageSelection.token`, with 30-day time-window guard.
- A **manual/worker-only** `PlayVoidReconciler` processes Google returned
  results. There is NO web callback endpoint, no public route, no timer
  and no automatic startup. This source must be separately reviewed before
  it is invoked on the VPS.
- Refunds are **hashed, idempotent and durable**. SQLite stores SHA-256 of
  purchase token/order ID and safe timestamps, never raw purchase tokens,
  order IDs, or Google account IDs. One-time purchases are immediately
  marked revoked. Unknown tokens seen *before* a user receipt are held so
  an old receipt cannot resurrect paid access.
- **Subscription renewal distinction:** Google reuses a purchase token across
  monthly renewals. A refund event for one specific order **holds** the token
  pending authenticated **latest order** review; it never permanently kills
  the subscription based on the shared token alone.
- Purchase verification rejects both earlier-refunded tokens and revocation
  holds before Google calls and again after receipt retrieval. The ledger
  refuses to record the claim if a refund was observed concurrently.
- Periodic polling uses overlapping 24-hour windows and advances its
  SQLite checkpoint only after processing **every page**. If offline longer
  than 28 days, polling stops with a clear gap error instead of silently
  ignoring missing refund history. Real production requires on-call
  monitoring/alerts and a refund catch-up strategy.
- Tightened one-time Buy-offer validation to distinguish `basic-buy` and
  `pro-buy` from an accidental rental or preorder, and reject prepaid or
  wrong-base-plan monthly subscriptions. Missing optional Google offer IDs
  are allowed for compatibility, but a mismatched supplied ID fails closed.
- `npm run preflight:play` provides an **explicit** read-only access
  test using Google application default credentials. It sends one API
  request to the Play financial/voided-purchases endpoint, and returns
  PASS/FAIL only. It prints no purchase data or identity details. If an
  explicit Linux JSON credential file is set, its file permissions must
  be private (0600 or stricter).

## Important limitations — not release ready

- No RTDN Pub/Sub verification or secure OIDC-authenticated webhook. RTDN
  must later be set up to detect renewals, on-hold transitions, refunds and
  cancellations promptly; periodic refunds alone are not enough.
- No safe automatic **release** of a held subscription after a refunded
  renewal. This requires latest-order / `latestSuccessfulOrderId` checks,
  order-scoped void matching, and replacement-token checks. Holds currently
  protect money but may temporarily block customers. Do not activate
  billing until such recovery is proven and user-safe.
- No production-safe grace policy for Google Play API outages. The earlier
  provisional ledger accepts recently checked one-time/monthly receipts
  for at most 24 h / 15 min. **This is intentionally inadequate for
  commercial use**, especially for legitimate active subscribers.
- No scheduled worker, durable automated backups, failure alarms,
  financial reconciliation reports, retries, or customer support workflow.
- No real Android app purchase grants, editor feature gates, or tested
  restoration following reinstall and Google account changes.
- Branch `play-billing-console-bootstrap` is an internal-track setup
  artifact only. Checkout remains
  `PlanCatalog.REAL_CHECKOUT_ENABLED=false`, and the deployed trial
  service uses the earlier pinned commit with
  `POST /v1/purchases/verify` returning 503.
- **Do not alter the existing production paid/free listing status** or
  migrate paid Basic buyers until legacy purchase entitlement and restore
  are explicitly reviewed.

## Owner's next desktop session — safe read-only preflight

1. Confirm the correct **Barnd AI** Google Cloud `prompt-ola` project and
   the invited **Prompt Overlay-only service account** in Play Console.
2. On the trusted VPS, configure a dedicated service identity **only after
   separately approving its secure credential-delivery method**. Do not
   paste account secrets, JSON key bodies or passwords into chat, GitHub,
   Codex commands/logs or ticket comments.
3. If using a protected JSON key as a last resort: store outside the git
   checkout under an isolated service account owner with Linux mode 0600,
   set `GOOGLE_APPLICATION_CREDENTIALS` only in a protected systemd
   environment for the future worker. Prefer short-lived credentials if
   an appropriate secure federation option is available. Do not reuse the
   Google Web OAuth client secret or Android release upload JKS.
4. From a locally checked-out reviewed branch, *only when credentials have
   been configured and authorized*, run the non-mutating command:
   `cd server && npm ci && npm run preflight:play`.
5. A PASS confirms **only** authentication and read-only voided API access;
   it does not establish purchase verification, update the live service,
   prove subscriptions work, or grant any user access.
6. Stop and review any 401/403, credential-file mode problem, or unauthorized
   project identity. Never broaden permissions to all apps merely to pass.

## Why order IDs matter

Google's voided-purchase API explicitly warns that the same **purchaseToken**
may appear in multiple subscription renewal orders. For subscriptions,
**orderId** is the unit of cancellation/refund, not purchaseToken.

Google source references:
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.voidedpurchases/list
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.voidedpurchases
- https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2
- https://developer.android.com/google/play/billing/rtdn-reference
- https://developer.android.com/google/play/billing/security

## Verification requirements

Tests cover refund-before-claim, retroactive permanent Basic/Pro revocation,
subscription token-hold by individual renewal order, duplicate/paginated
publisher reports, network failure with checkpoint unchanged, out-of-window
errors, encrypted transport origin pinning, invalid SKU/offer safeguards,
and no raw purchase tokens stored in the SQLite refund ledger.

The next step after tests is **independent code/security review**, not
deployment or Google Play activation.
