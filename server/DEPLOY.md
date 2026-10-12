# Prompt Overlay — 90-day trial server: staged deployment

**Stage: integration preparation; not a production release.**

This is the account-backed 90-day trial service, NOT Google Play Billing.
Until your Android OAuth clients and HTTPS endpoint are configured, Alpha 16's
"Sign in" button cannot activate the trial. Even after it works, the
**actual Android feature gates and purchases have not been wired**, and the
published Google Play app must remain unchanged.

The owner approved **only one free offer**: 90 days of Basic + current Pro
features from the original verified server sign-in. There is NO 14-day sale,
no credit card, and no automatic charge. Later options, if purchased, are
Basic Lifetime / Pro Monthly / Pro Lifetime. A paid monthly subscription
*would* auto-renew until canceled, but the initial 90-day trial does not.

## What the owner needs to do once, in a grouped desktop session

1. The owner selected **`teleprompter-trail.barndai.com`** as the dedicated
   trial-service hostname (spelled **trail**, as requested). DNS and HTTPS
   have **not** been configured or verified. Point its DNS record to the
   chosen VPS only after confirming the machine and its existing services.
   Set an A/AAAA record, and ensure the VPS can serve HTTPS on port 443.
   We cannot assume the existing Barnd AI / SocialOlla VPS is Linux, has
   Docker, or is available; confirm the actual host and operating system.
2. Google Cloud Console → Google Auth Platform → Branding/Audience/Clients:
   configure your app identity and a **Web OAuth client**. Copy only its
   public ID (`...apps.googleusercontent.com`), not a secret.
   Create an **Android OAuth client** with the correct app package and the
   SHA-1 of the certificate that signs the exact APK used for the test.
   Android test package and production package differ:
   - Alpha 16 test APK: `com.costavong.promptoverlay.proalpha16`.
   - Published package: `com.costavong.promptoverlay`.
   - Each GitHub Actions *default debug-signed* build may have a different
     keystore and SHA-1. For repeatable Google sign-in testing, configure
     a stable **private QA signing key** via GitHub Actions secrets or use
     an internally tested Play-signed build. Never commit or paste the key.
   Google also requires you to set your consent/testing audience appropriately.
3. Set `GOOGLE_WEB_CLIENT_ID` privately in the server's `.env`, deploy
   the service on a Linux VPS, configure Nginx/Caddy HTTPS, and run health and
   authentication checks below. No Google API credentials or service-account
   keys are needed merely for Google ID-token signature verification.
4. Build an APK with the two **public identifiers**:
   - `trialGoogleWebClientId=<the WEB OAuth client ID>`
   - `trialApiBaseUrl=https://teleprompter-trail.barndai.com`
   Both values are compile-time Gradle properties. Rebuilding *without*
   them shows a deliberate “not configured” status and will not start a trial.
   Record the APK's actual signing certificate SHA-1 in the Android OAuth
   client configuration before device testing.
5. Test one new Google account, a return visit, an app reinstall, signing out,
   wrong/expired nonce, and trial day 89→90 with accelerated backend fixtures.
   This does NOT authorize a production free-download conversion.

Official Google Android OAuth instructions:
https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

## Isolated Linux deployment with Docker

Choose a **dedicated** folder / subdomain so no currently operating service
is replaced. The commands below assume Docker Compose v2, DNS and a working
TLS proxy on the VPS; do not run them against a machine you haven't inspected.

```bash
# Example only — do not overwrite other application directories.
git clone -b feature/trial90-deployment-bridge \
  https://github.com/costavong-pixel/prompt-overlay-pro.git /opt/prompt-overlay
cd /opt/prompt-overlay/server
cp .env.example .env
# Edit GOOGLE_WEB_CLIENT_ID=<Web OAuth client ID>
nano .env

# Ensure nothing else already uses the local port.
ss -ltnp | grep ':8765' || true

# Starts a non-root Node 22 container and a durable SQLite volume. The VPS
# exposes it ONLY to localhost; not directly to the public internet.
docker compose up -d --build
docker compose ps
curl --fail --silent http://127.0.0.1:8765/health
```

Deploy the `deploy/nginx-trial-location.conf.example` rules *inside* an
existing valid TLS Nginx server block for your chosen subdomain. The sample
overwrites `X-Forwarded-For` so that users cannot forge the address the
rate limiter sees. Never expose the backend's port 8765 publicly or configure
`trust proxy=true`. Protect port 443 with a trusted TLS certificate. Keep your
other Nginx sites unchanged.

Run the public health check after DNS and TLS are actually ready:

```bash
bash deploy/check.sh https://teleprompter-trail.barndai.com
```

It checks `/health` returns 200, nonce endpoint returns 200, and that
`/v1/entitlements` requires authentication (401). It does **not** submit a
real Google token, buy a product, or activate the 90-day trial.

## Security and continuity

- `TRIAL_DB_PATH=/var/lib/prompt-overlay/entitlements.sqlite` is backed by
  Docker's named `trial-data` volume, not ephemeral container storage.
  Preserve and back up this database and its SQLite WAL files together.
  Never use `:memory:` as the production trial database.
- The trial begins at the **first verified Google sign-in** (server clock),
  never on app install. Every later sign-in uses the stored initial start.
- Login requires a verified Google ID token, the correct Web OAuth audience,
  matching one-use nonce, and an unexpired account session. Public requests
  cannot set trial times or Basic/Pro grants.
- Customer account data (Google subject ID, session token hash, timestamps)
  is collected. **Update the Play privacy policy and Data safety form before
  a public release**; the published app currently describes no account.
- Keep the reverse proxy/TLS updated, restrict admin access, and secure the
  database backup location. Never paste credentials, ID tokens, or customer
  records into ChatGPT or GitHub issues.
- Protect existing paid customers *if any*; owner reported zero purchases,
  but verify the Play Console count before switching paid→free.
- Do not activate Pro feature locks, grant Lifetime awards, or charge anyone
  until Play Billing verification, restore flows, refunds and UI security
  reviews have passed.
- A real subscription **renews after purchase** until canceled. The 90-day
  no-card free trial is *not* an auto-renewing subscription offer.
