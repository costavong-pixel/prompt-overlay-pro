# Prompt Overlay Pro

Android floating teleprompter and local four-track video-editor prototype.
Current Android QA candidate: **2.0.0-pro-alpha19 (version code 55)**.

**Pricing and access decision:** One 90-day no-card, non-renewing free trial
unlocks all Basic and current Pro features. Afterwards the customer can choose
Basic Lifetime ($5.99 USD target), Pro Monthly ($4.99/month target), or Pro
Lifetime ($39.99 USD target). The 14-day promotional sale is **cancelled**.
We will choose 500 people for Pro Lifetime based on substantive private
feedback; public Google Play ratings are not rewarded.

**Development status:** Alpha18 connects the Android QA app to the
owner-reported live HTTPS trial API and correct public Google Web OAuth audience.
The owner must still register the **exact installed test APK package and
SHA-1** as an Android OAuth client and test a real account. A successful
sign-in will start the account's real 90-day clock. The Android app's QA
package is now fixed, but future installs cannot upgrade each other unless
a stable private QA signing key is configured. Google Play purchases,
subscription validation, production feature locks, refunds and restoration
are **not implemented**. The published paid Basic app is unchanged.

See `docs/ALPHA18_GOOGLE_SIGNIN_QA.md` for Google sign-in and OAuth; see
`docs/ALPHA19_PLAY_BILLING_PREFLIGHT.md` for the new read-only Play ownership
check and server-side receipt validation policy, and for the important
safe side-by-side Alpha19 package when a stable QA signer is unavailable.

## Features

- Existing floating teleprompter, script library, formatting, document/photo
  imports, Bluetooth/USB controls and local Wi-Fi browser remote.
- Import/share videos made with the user's preferred camera.
- Four logical tracks: video, logos/stills, text/captions, and sound.
- Trim, split, reorder and join clips; cuts/crossfades; 0.5x–2x speed.
- Logo/title placement and timing; one music track, volume and fades.
- Crop/output formats, brightness/contrast/saturation/temperature.
- Immediate selected-clip playback, pause and timeline seeking, respecting
  trim, speed and mute. Plays the source clip only; effects, captions, graphics,
  crossfades and mixed music are included in the rendered edited preview.
  Direct playback volume is capped at 100%; export supports amplification.
- Shared rendering path for 720p edited preview and 720p/1080p MP4 export.
- Experimental green screen with a replacement still image.
- Local English/French/Spanish speech packs for editable automatic captions.
- English, French, Spanish and Arabic UI; manual Arabic captions.

See [approved scope](docs/APPROVED_SCOPE.md) and
[validation status](docs/VALIDATION.md).

## Build

Install JDK 17 and Android SDK platform 36 / build-tools 36.0.0. Set
`ANDROID_HOME` or an untracked `local.properties` with `sdk.dir`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The default ARM64 Alpha19 build without a stable QA keystore installs
as **Prompt Overlay A19 Preview**
(`com.costavong.promptoverlay.proalpha19`), beside BOTH the published Basic
app and your signed-in Alpha18 Pro QA test. This prevents Android from forcing
an uninstall when ephemeral debug signing keys differ. The temporary Alpha19
package does not have a Google Android OAuth registration, so its sign-in
button is disabled; use Alpha18 for your existing live 90-day trial.
When protected permanent QA signing secrets are set, the build instead uses
`com.costavong.promptoverlay.protest` for subsequent QA releases.
No keystore is stored in this repository.

For an x86_64 emulator:

```sh
./gradlew -PproTestAbi=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.costavong.promptoverlay.protest.test/com.costavong.promptoverlay.pro.ProSmokeRunner
```

The media smoke runner's caption check additionally requires the English Vosk
mobile pack to be present in the test app's `files/pro/speech/` directory.
The application can download the optional pack after an explicit user choice.

## Try it

New project → import a short video → select a clip → Play clip / Pause.
Use the seek slider or tap the video track to choose a position or clip.
Use Edited preview to render the full composition, then Export → Save video /
Share. Selecting another clip or seeking stops current source playback; press
Play clip to start from the new position.
Clip, caption, graphics and music controls autosave edits locally.

## Release status

This repository does not provide a production Pro checkout. The release package
retains `com.costavong.promptoverlay`; Pro entry points are disabled in release
until entitlement, purchase and restore handling is implemented. Release signing
is configured through environment variables, with no key material in source.
Do not submit the debug APK to Google Play.

## Data and dependencies

Project media and caption processing stay on the device. Optional speech-pack
downloads contact the public model host; Android sharing sends media only to
the destination the user chooses. Update the public privacy policy and Play
declarations before a Pro release.

Dependencies include AndroidX Media3 (Apache 2.0), Vosk (Apache 2.0), JNA
(Apache 2.0/LGPL 2.1 choice), and Google ML Kit under Google's SDK terms.
Speech-pack licences are listed at <https://alphacephei.com/vosk/models>.
The test speech sample comes from the Vosk API example; the synchronization
fixture is synthetic test media. Dependency licences remain applicable.
