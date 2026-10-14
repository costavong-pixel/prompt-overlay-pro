# Alpha18 — real HTTPS / Google Identity QA (app-first)

Status: Android QA integration candidate. **Not yet a real phone sign-in pass.**
The owner approved moving forward with Android; the Teleprompter trial server
is reported live at `https://teleprompter-trail.barndai.com` (spelled "trail").
No deployment changes or payment activation are part of this branch.

## Final agreed customer behavior

- Free install (to be switched on Google Play only after the secure app is
  ready); **one 90-day no-card full-feature trial** begins after the first
  Google Sign-In verified by the server, not at installation.
- No 14-day sale, and no automatic charge/renewal for the 90-day trial.
- After 90 days: Basic Lifetime ($5.99 target), Pro Monthly ($4.99/month target),
  Pro Lifetime ($39.99 target). Monthly renews **only after the user actively
  buys** that recurring subscription. Prices displayed by Play take priority
  over target prices.
- 500 customers will be selected for Pro Lifetime from substantive **private**
  app feedback, never paid/rewarded public Play ratings.

## Alpha18 app / package

- **Test application ID, intended to stay fixed for QA releases:**
  `com.costavong.promptoverlay.protest`
- Installed app label: **Prompt Overlay Pro QA**; it installs next to the
  published production app `com.costavong.promptoverlay`.
- Public Web OAuth Client ID (project `prompt-ola`):
  `331034587847-tmnrmmr51la0q4ng5g2bllu50ujr9ufp.apps.googleusercontent.com`
  (this ID is not a secret).
- HTTPS API compiled into the app:
  `https://teleprompter-trail.barndai.com`.
- The sign-in view shows the **actual installed package name and APK signing
  SHA-1** and has a **Copy package name + SHA-1** button. This fingerprint is
  derived locally from the installed APK, not inferred from GitHub source.
- **Check HTTPS server** checks the public `/health` endpoint without
  contacting Google or starting any trial.
- **Sign in with Google** shows an explicit warning: a successful verified
  Google login will start the real server-side 90-day trial for that account
  and reinstalling **will not reset it**. Use a Google test account.
- Google Credential Manager requests an ID token for the exact Web Client ID
  and server-issued, one-time nonce. The server verifies the token/nonce and
  returns a short-lived account session. The Android Keystore encrypts the
  saved session token. The app never needs a client secret.
- Subscription checkout remains **disabled** and all Pro features remain
  available in the isolated alpha for editor testing regardless of server
  entitlement. This is not a sellable production build.

## Owner steps to register Android OAuth (once for stable QA certificate)

1. Google Cloud Console → choose existing **`prompt-ola`** project.
   Open Google Auth Platform → Audience. If status is **Testing**, add the
   actual Google login address you want to use as an approved test user.
2. Google Auth Platform → Clients → **Create client → Android**.
   Suggested name: **Prompt Overlay Pro QA**.
3. **Package name**: `com.costavong.promptoverlay.protest`.
4. **SHA-1 certificate fingerprint**: from this exact Alpha18 release's
   `QA-ANDROID-SHA1.txt` artifact, or by opening the installed app → Trial
   & Plans → Activate/check status → **Copy package name + SHA-1**.
   Enter just the colon-separated SHA-1 value. The certificate is safe to
   share; **never** provide the signing private key.
5. Google may take a short time to apply the new client registration.
   Test the in-app server health check first, then Google sign-in with a
   **dedicated test Google account** to preserve other users' 90-day clocks.
   The server should return an authenticated entitlement, and subsequent login
   should not restart the first-activation timestamp.

**Important debug signing limitation:** GitHub Actions generates a new default
Android debug signing keystore for each fresh runner. If the stable private QA
signing secrets below are *not* configured, the generated SHA-1 is usable for
this exact APK only. The package ID will be stable, but a future APK signed
with a different certificate **cannot update this installation** and requires
another Android OAuth registration. Do not assume the package ID by itself
makes the signing identity stable.

## Stable QA signing (private owner/Codex work; do NOT commit credentials)

The Alpha18 CI optionally uses these GitHub Actions repository secrets:

- `PROMPT_OVERLAY_QA_KEYSTORE_B64`: base64 of a dedicated **nonproduction**
  Android JKS/PKCS12 keystore
- `PROMPT_OVERLAY_QA_STORE_PASS`: keystore password
- `PROMPT_OVERLAY_QA_KEY_ALIAS`: test key alias
- `PROMPT_OVERLAY_QA_KEY_PASS`: key password

A trusted authorized local Codex/terminal session can generate this stable
dedicated QA keystore and use `gh secret set` to save the four secrets to
`costavong-pixel/prompt-overlay-pro`, without exposing them in chat, PR
comments or CI logs. The key should be stored in a private offline backup
under the owner's control. **Do not reuse the production Google Play signing
key or an existing app's key.** Use the same QA key for subsequent Pro QA builds.

After QA secrets are installed, build a new QA-signed APK and register the
**new stable SHA-1**. Verify in CI:
`Signing: stable-private-qa-keystore` and fingerprint match. Existing
ephemeral debug APK must be uninstalled first if its signing cert differs.

## Mandatory acceptance checks

- App shows exact package and signing SHA-1; easy to copy on Samsung.
- Embedded Web OAuth Client ID matches the actual registered Google project.
- HTTPS server health check is 200/ok without starting a trial.
- Cloud Android client package and cert SHA-1 both match this installed APK.
- Sign in with an approved test account: server actually confirms identity,
  app shows active trial with approximately 90 days remaining; do not
  fabricate a successful response.
- Reopen app → Refresh access → original deadline preserved.
- Sign out → same account sign-in → trial still same deadline.
- Reinstall test APK only after safeguarding QA signing/data and confirm the
  trial clock does not restart.
- New login with invalid / unsupported Android OAuth must fail without
  silently granting Pro in a production checkout.
- Basic/Pro editor testing, photo/video feedback, Spanish/French/Arabic
  screen switching and Android 16 bottom navigation should remain usable.
- Separate mobile/Play test tracks from public release. No production API
  billing, free store transition, product activation or automatic charges.

## Release gate

A successful signed APK build and CI tests are **not** proof of working Google
Sign-In on a phone. Only a successful real-account Credential Manager flow
against the deployed HTTPS server completes this phase. Before release,
billing verification, account/privacy disclosure, secure feature gating,
local offline fallback, purchase restoration/refunds and lifetime-award
issuance must be implemented and independently reviewed.
