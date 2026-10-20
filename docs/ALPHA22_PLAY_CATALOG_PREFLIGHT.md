# Alpha22 — Secure Google Play catalog preflight (read-only)

**Scope:** Google Play product-setup validation for the already configured
Prompt Overlay SKUs, with no production rollout and no VPS mutations.

## Owner-completed prerequisites

- Google Play Console under Barnd AI has the product ID
  `basic_lifetime` with purchase option `basic-buy`, intended $5.99 USD.
- Product `pro_lifetime` has purchase option `pro-buy`, intended $39.99 USD.
- Subscription `pro_monthly` has base plan `monthly`,
  auto-renewing **calendar-month** `P1M`, intended $4.99 USD/month.
- Google Play Developer API was enabled in Google Cloud project
  `prompt-ola`.
- Owner created and invited the service-account identity
  `prompt-overlay-billing@prompt-ola.iam.gserviceaccount.com`
  with Prompt Overlay-only Play Console permissions.
- The 90-day, no-card, non-renewing Google account trial is already working
  in the Alpha18 QA app. Don't uninstall the old working QA app.

These are **owner-reported configuration steps**, not yet confirmed by a
successful API request from the VPS.

## Delivered here

- `PlayCatalogReadOnlyClient` calls only:
  - `GET /androidpublisher/v3/applications/com.costavong.promptoverlay/oneTimeProducts`
  - `GET /androidpublisher/v3/applications/com.costavong.promptoverlay/subscriptions`
- All requests are pinned to one package, use existing Google-authenticated
  client and bounded pagination, and do not modify Play.
- Checks the **exact intended IDs**, `buy` purchase-option type,
  subscription monthly `P1M` and published/draft states, and
  **US regional price targets**. Reports `DRAFT` vs `ACTIVE` without
  attempting to activate products.
- Ignores unrelated catalog items; does not print API responses containing
  unrelated app information or any Google bearer token/credential.
- `server/scripts/check-play-catalog.mjs` obtains **Application Default
  Credentials**, checks the expected service account email, and performs
  read-only calls only with explicit operator flag.
- Unit tests use offline mocked Google API responses, including
  incorrect ID/base-plan/country price, duplicate entries, pagination
  and wrong app package. No real Google Play connection was tested by CI.

## When the operator is back at the desktop

**Do not send service-account keys, key JSON, OAuth credentials, private
signing files or access tokens to the ChatGPT conversation.**

Trusted Codex/VPS operator should:
1. Verify a private, securely stored server credential/ADC setup for the
   dedicated service account. Prefer non-exportable/federated credentials
   if possible. Do not assume the Google Cloud OAuth **Web Client ID**
   is sufficient to call the Publisher API.
2. Confirm the correct Barnd AI Play Console access is granted to this
   exact service-account email and restricted to Prompt Overlay.
3. Run using the existing project branch in an isolated/nonproduction
   test environment (not the deployed systemd service):

   ```bash
   cd server
   PLAY_CATALOG_READ_ONLY_CHECK=YES node scripts/check-play-catalog.mjs
   ```

   The command requires the Google Auth library from a private
   `npm ci` run and properly configured local ADC. It performs **GET
   only** and never logs credentials.
4. Return only safe status fields (API authorization PASS/BLOCKED;
   three expected product IDs, option/base-plan states and correct
   price checks). Do not paste service-account key files, full HTTP
   headers, access tokens or filesystem secrets.
5. If 403 occurs, first verify developer identity and the app-level
   Play Console permission. Do **not** automatically grant admin,
   production publishing or all-app access.
6. Keep the live `prompt-overlay-trial.service`, SQLite DB, nginx, all
   other VPS apps and Google Play production untouched.

## Exit conditions

- This Alpha22 code may be reviewed/merged only after tests and independent
  security review; CI success does not establish API access.
- The API catalog test is **not purchase verification**. Real checkout,
  refund/voided-purchase processing, subscription lifecycle recovery,
  server feature gating and RTDN must pass separate release gates.
- Only internal license testers should make future Google Play test
  purchases, after the owner explicitly authorizes the relevant build.

## Official API documentation

- https://developers.google.com/android-publisher/api-ref/rest/v3/monetization.onetimeproducts/list
- https://developers.google.com/android-publisher/api-ref/rest/v3/monetization.subscriptions/list
- https://cloud.google.com/docs/authentication/application-default-credentials
