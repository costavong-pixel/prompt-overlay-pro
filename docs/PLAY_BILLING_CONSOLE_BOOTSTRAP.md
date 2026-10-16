# Google Play Console is blocked on "add the BILLING permission to your APK"

This is an **internal testing setup operation**, not an app update for buyers.

The owner saw, under **One-time products**, "Your app doesn't have
any one-time products yet. To add one-time products, you need to add the
BILLING permission to your APK."

## Diagnosis

The Play-uploaded version of `com.costavong.promptoverlay` predates Google
Play Billing integration. Google Play only enables product creation after
the app has a published artifact containing the Billing Library/permission.
A separate side-by-side Alpha21 APK uses
`com.costavong.promptoverlay.proalpha21` and **cannot unlock products for
the published package**, so do not upload it.

## Code change (this branch)

- Real Play package `com.costavong.promptoverlay` in **release** build;
  source based on reviewed Alpha21 but the release variant has
  `proEnabled=false`.
- Explicit manifest `<uses-permission android:name="com.android.vending.BILLING" />`.
- Existing Google Play Billing Library dependency 9.1.0.
- Checkout hard-disabled: `PlanCatalog.REAL_CHECKOUT_ENABLED=false`.
- Backend Google purchase verification is **not deployed**, and the
  current live server remains unchanged.
- Pro alpha test packages, old installs, trial timestamps and existing
  production builds are not touched.
- *Provisional* bundle version code 58. **Verify it is higher than every
  version ever uploaded in Play Console before signing/uploading.**
- GitHub Action builds a release AAB and verifies package, Billing permission,
  and disabled checkout. It deliberately publishes an **UNSIGNED** artifact
  for local signing; **do not attempt to upload the unsigned artifact**.

## Trusted local Codex signing handoff

The app was previously signed with an original production **upload key**,
recovered by local tooling for a v1.22.2 internal build in early Oct 2026.
Do **not** rotate it, replace it with the QA key, or expose its password.

1. On the trusted Windows machine, locate the existing protected upload
   keystore and its backed-up certificate details **without printing private
   key/password values**. If unavailable, STOP.
2. In Play Console → Test and release → Setup → **App integrity**, view
   **Upload key certificate** SHA-1/256. Do not confuse it with the Google
   Play **App signing key certificate**. Confirm original keystore public
   certificate matches the **Upload key certificate**. Never alter signing.
3. In Play Console → Test and release → App bundle explorer and current
   Production/Internal testing release history, confirm highest versionCode
   across all tracks. If >= 58, raise this branch's versionCode, do not
   recycle or upload a duplicate. No key/value secret needs to enter chat.
4. Checkout `play-billing-console-bootstrap` at an immutable reviewed SHA.
   Use the original secure keystore *only through private environment variables*
   `PROMPT_OVERLAY_UPLOAD_KEYSTORE`,
   `PROMPT_OVERLAY_UPLOAD_STORE_PASS`,
   `PROMPT_OVERLAY_UPLOAD_ALIAS` and
   `PROMPT_OVERLAY_UPLOAD_KEY_PASS` for Gradle release signing.
5. Build `gradle --no-daemon :app:testDebugUnitTest :app:lintRelease
   :app:bundleRelease`, verify the resulting **signed**
   `app/build/outputs/bundle/release/app-release.aab` with
   `jarsigner -verify -verbose -certs`, and inspect it with
   `bundletool dump manifest --bundle ...` if available. Verify:
   - Package exactly `com.costavong.promptoverlay`.
   - Release versionCode greater than highest Play version.
   - `com.android.vending.BILLING` is in the final **AAB**.
   - Pro editor remains disabled and paid checkout cannot launch.
   - Signing cert exactly matches **Upload key certificate**.
6. Give owner the signed AAB using protected local transfer; do not store
   keystore/password in GitHub, any public artifact, chat, or VPS logs.
7. Owner uploads **ONLY to Test and release → Testing → Internal testing →
   Create new release**; add self as tester, review track, roll out **to
   internal testers only**. Do not press any "Promote to production".
8. After Google Play processes/publishes the internal test bundle, return to
   Monetize with Play → Products → One-time products to create the SKUs.
   Product creation might remain locked until the internal test is rolled
   out and Google's processing catches up.

## Planned SKU list

- `basic_lifetime` → $5.99 USD, non-consumable one-time.
- `pro_lifetime` → $39.99 USD, non-consumable one-time.
- `pro_monthly` → $4.99 USD/month subscription, standard monthly base plan.

Configure developer-account **Settings → License testing** and register
test accounts to avoid real charges. Internal-test access and license
testing are different: users generally need both when exercising purchases.

## Must NOT happen

- No changes to the paid/public Play listing or irreversible paid→free
  price status.
- No production release, release-track promotion, or public monetization.
- No Alpha21 `.proalpha21` APK uploaded as an update to production.
- No ephemeral/debug/QA key used to sign a real Play internal-track AAB.
- No new key creation if the original one is not available.
- No real test purchase by a Google account not explicitly allowed as a
  license tester.
- No new Play Billing checkout, online payment, or entitlement grants
  enabled by the permission-only bootstrap.
