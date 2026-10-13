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

## Docker-free isolated Linux deployment

Use a dedicated system account, working directory, data directory, service
name, and loopback-only port. This is the preferred path for a host that
already uses systemd and Nginx. Do not install or replace the host's global
Node.js package: install a verified Node 22 runtime under the dedicated
application directory instead.

Before creating anything, capture the active services, Nginx config test, and
all listening ports. Select a port only after a live check; `18765` is the
example below and is never a guarantee that the port is free.

```bash
ss -ltnp | grep ':18765' || true
git clone --no-checkout --branch feature/trial90-deployment-bridge \
  https://github.com/costavong-pixel/prompt-overlay-pro.git /opt/prompt-overlay-trial/app
cd /opt/prompt-overlay-trial/app
git checkout --detach <owner-approved-immutable-commit>
test "$(git rev-parse HEAD)" = "<owner-approved-immutable-commit>"
```

Provision an unprivileged `promptoverlay` account with writable data only at
`/var/lib/prompt-overlay-trial`. Keep source and the private Node 22 runtime
under `/opt/prompt-overlay-trial` root-owned and not writable by the service
account. Copy `deploy/prompt-overlay-trial.service.example` into the systemd
unit directory only after reviewing its exact paths and creating a private,
root-owned `/etc/prompt-overlay-trial/trial.env` from `.env.example`.

The private environment must provide a valid public `GOOGLE_WEB_CLIENT_ID`,
set `PORT=18765` only if the current port check is clear, and use the persistent
`/var/lib/prompt-overlay-trial/entitlements.sqlite` path. The service must bind
to `127.0.0.1` only. A missing or invalid Web OAuth ID is intentionally a
startup failure; do not bypass it with a fake public login configuration.

Run the service's loopback checks before creating any public route:

```bash
curl --fail --silent http://127.0.0.1:18765/health
curl --fail --silent -o /dev/null http://127.0.0.1:18765/v1/auth/nonce
test "$(curl --silent -o /dev/null -w '%{http_code}' http://127.0.0.1:18765/v1/entitlements)" = 401
```

`deploy/backup-trial-db.mjs` makes an online SQLite backup through the same
Node dependency used by the service. The paired systemd backup service and
timer templates keep backups under `/var/lib/prompt-overlay-trial/backups`.
Enable either unit only after reviewing retention, encrypted off-host storage,
and access control for customer account data.

### Dedicated HTTPS route

Deploy `deploy/nginx-trial-location.conf.example` only inside a **new**, host
specific TLS virtual host for `teleprompter-trail.barndai.com`. It proxies to
the selected loopback port and replaces `X-Forwarded-For`; never append an
attacker-supplied header, expose the Node port publicly, or use
`trust proxy=true`.

Creating a site file, enabling it, reloading Nginx, issuing a certificate,
changing firewall rules, or changing DNS touches shared infrastructure. First
show the exact vhost diff, certificate method, rollback command, immutable
commit, port check, and baseline health of neighboring services; obtain owner
approval before applying those changes.

### Optional future Docker path

Docker is not required for the systemd deployment above. If it is later used,
the Compose file has the explicit `prompt-overlay-trial` project name and uses
`TRIAL_HOST_PORT` for a loopback-only host mapping. Do not install Docker or
use the optional path without separate owner approval.

Run the public health check after DNS and TLS are actually ready:

```bash
bash deploy/check.sh https://teleprompter-trail.barndai.com
```

It checks `/health` returns 200, nonce endpoint returns 200, and that
`/v1/entitlements` requires authentication (401). It does **not** submit a
real Google token, buy a product, or activate the 90-day trial.

## Security and continuity

- `TRIAL_DB_PATH=/var/lib/prompt-overlay-trial/entitlements.sqlite` belongs to
  the dedicated service account and is not ephemeral. Use the provided online
  SQLite backup helper or an equivalent atomic backup process; never copy a
  live SQLite main file without its WAL state. Never use `:memory:` in
  production.
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
