# Monetization transition: free download, 90-day Pro trial, and private feedback

Status: foundation only. **NOT production-ready. Do not change Play app pricing yet.**
Current shipped Play app is a one-time paid Basic app. Current Pro debug test
has its own package ID and bypasses purchasing. This branch must not grant a
paid entitlement solely from local preferences.

## Approved product direction

- Download: free (permanent Google Play Console change; cannot revert to paid).
- First activation: **90-day full-feature trial** covering all currently built
  local Basic and Pro functionality, without a charge. Default proposal: no
  credit card; trial keyed to a signed-in account and recorded server-side.
  This is NOT Google Play's auto-renewing subscription free-trial offer.
- After trial: users choose Basic Lifetime ($5.99 proposed), Pro Monthly
  ($4.99/mo proposed), or Pro Lifetime ($39.99 proposed).
- Keep feedback and account recovery accessible after expiration. Do not delete
  existing projects or script data when trial expires.
- Future metered cloud AI is outside the free-trial/lifetime entitlement until
  a separate, explicit credit policy is approved. No current cloud AI exists.
- Customers who paid for Basic before the paid-to-free conversion must retain
  **Basic lifetime**. Ensure a genuine migration path *before* conversion.

## Product IDs (to create in Play Console)

| ID | Type | Feature grant |
| --- | --- | --- |
| `basic_lifetime` | One-time, non-consumable | Basic forever |
| `pro_monthly` | Auto-renewing subscription, monthly base plan | All current Pro features until verified expiry |
| `pro_lifetime` | One-time, non-consumable | All current Pro features forever |

Product price strings must come from Google Play product details, not hard-coded
USD values inside a checkout.

## Access priority, highest first

1. Verified `pro_lifetime` purchase: Pro indefinitely.
2. One-time lifetime award from private feedback program: Pro indefinitely.
3. Backend-confirmed active `pro_monthly` subscription: Pro until expiry.
4. Backend-controlled 90-day trial: full Pro until trial deadline.
5. Verified Basic purchase or previously paid Basic customer: Basic forever.
6. Expired/no entitlement: trial-over offer screen; allow reading existing
   project titles, restoring purchases, sending feedback and managing account,
   without silently deleting user media. Final read-only boundaries require
   user confirmation before gating production actions.

Core decision precedence is coded and covered by tests in
`AccessPolicy.java` and `AccessPolicyTest.java`.
**This is not authorization to unlock paid products on the device**: input
booleans must come from authenticated, verified backend records.

## Server: required for a no-card 90-day trial

Trial start is server time at first verified activation, never reset by
uninstalling/reinstalling or changing the clock. A Google sign-in (or another
durable account identifier) is required if trial and reward status must restore
across devices. We must get user consent and update the privacy policy.

Suggested minimum HTTPS API:

- `POST /v1/session`: verify Google identity token; create or retrieve account.
- `GET /v1/entitlements`: return short-lived, authenticated/signed entitlement
  snapshot with `server_time`, `trial_started_at`, `trial_ends_at`,
  `basic_owned`, `monthly_until`, `pro_lifetime_owned`, `award_lifetime`.
- `POST /v1/purchases/verify`: server checks purchase token against Google
  Play Developer API and binds token to one account; acknowledges purchases
  and handles pending/refunded/revoked and expired subscriptions.
- Real-time developer notifications + periodic reconciliation update status.
- `POST /v1/feedback`: accept structured private feedback after authentication,
  rate-limit, store minimally, and send an acknowledgement.
- Admin: read, classify and select participants for lifetime awards, with
  audit trail; never publish participant email addresses.
- Store the trial's initial server timestamp, account linkage and verified
  purchase tokens in a durable database. Token reuse must be rejected.
- Consider limited offline access via previously verified short-lived
  entitlements; no indefinite unsigned local flags.

The beta currently has no billing dependency, account integration, or server
entitlement endpoint. These must be built and tested before free conversion.

## In-app feedback: immediate alpha prototype

Alpha 14 updates the Pro home action to **Win a FREE Lifetime License from Us**
and removes the duplicated small Rate app button. A dedicated feedback screen
explicitly states **"We will choose 500 users"** based on the quality of private,
actionable, and specific product feedback; no one is guaranteed selection.
Google Play ratings or reviews are not required and do not affect selection.

The form asks what feature was tried, the observed issue or improvement,
and reproducible steps, with OPTIONAL supporting evidence:
- Attach up to four user-picked **image/video** files, limited to **15 MiB
  in total**, using Android's Storage Access Framework. Only the selected
  media are passed into a system email chooser with read URI grants; no
  automatic video/audio upload or scanning of other user files.
- Paste an optional shared **http(s) link** for large clips or screenshots.
  The user must grant view access to the intended recipient themselves.
- A labeled list displays attached files and lets users remove them.
- The app constructs a reviewable email to **support@barndai.com**. The user
  must actually send it from their email app. Opening the chooser does NOT
  establish successful delivery, submission, eligibility, or award.

The email address is inherited from the prior prototype and its delivery
has **not** been verified. Owner must confirm the support mailbox receives
attachments before public rollout. A formal selection policy, enrollment
deadline and final Pro Lifetime award mechanics are still pending.
For scaled use, replace the email draft with an authenticated HTTPS feedback
endpoint and in-app submission receipt/dashboard.

Feedback program:
- Will select **500** participants for Pro Lifetime when the program is launched, based on useful,
  actionable **private** product feedback (including critical/negative
  feedback), not enthusiasm.
- Public Google Play ratings/reviews must be fully independent. Never
  require, reward, screen for, or preferentially select positive public
  ratings or reviews. No review incentive.
- Give each selected participant a unique one-time redemption code or audited
  account award. If using Google Play `pro_lifetime` promo codes: Google
  currently permits up to **500** one-time-product promo codes per quarter
  *across products in one app*. Reserve quota; note existing or previously
  issued codes reduce availability. An unused quarter's codes don't roll over.
- Clearly state the selection criteria, deadline, number available,
  permitted regions, whether selection is guaranteed (no), and contact
  method before accepting feedback. Don't describe it as a contest unless
  its legal requirements have been checked.
- Don't equate an email being drafted with email being sent or received.
  Selection is a manual operation until backend/admin workflow is live.

## Privacy and Play listing obligations

Today the Play listing declares **no data collected**; the Pro prototype
privacy dialog says **no account**. Both will require review if Google sign-in,
server trial/account data, telemetry, direct in-app feedback, or redemption
selection is added. Update Privacy Policy, Data safety, store description and
support documentation before publishing those features.

Preserve the existing published app, support restoration and avoid an
unintentional loss of rights for paid Basic customers.

## Required milestones (and hard gates)

1. Define feature matrix and exact post-trial lock behavior, resolve legacy
   paid-user migration, choose no-card sign-in vs Play subscription free trial.
2. Build authenticated trial backend with trial-restoration tests.
3. Build Play Billing 9.x, three Play products, verify tokens server-side,
   acknowledgement, restoration, refunds, cancellations, grace periods.
4. Connect feature gates to the production package; remove debug bypasses.
5. Feedback service and selection admin: audit 500 awards and promo code
   issuance, anti-spam, privacy, feedback receipt.
6. Full real-device and Play license-test matrix (including no account, paid
   legacy Basic, no network, reinstall, account switch, trial day 89/90, new
   monthly, canceled monthly, lifetime, award, refund).
7. Test signed Play release, privacy/data safety and published feature copy,
   then **only** switch Play app pricing from paid to free.

## Evidence and policy sources (as of Oct 2026)

- Paid-to-free switch irreversible:
  https://support.google.com/googleplay/android-developer/answer/6334373
- Play promo code quota:
  https://developer.android.com/google/play/billing/promo
- No incentivized public ratings/reviews:
  https://support.google.com/googleplay/android-developer/answer/9898684
- Play backend/purchase verification:
  https://developer.android.com/google/play/billing/backend
- Subscription trials require a valid payment method and automatically bill
  after expiry (unlike the proposed no-card account trial):
  https://developer.android.com/google/play/billing/subscriptions
