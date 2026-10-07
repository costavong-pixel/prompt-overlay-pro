# Alpha validation

Prompt Overlay Pro alpha 1 — build validation, October 7, 2026

Version: 2.0.0-pro-alpha1 (code 37)
Application ID: com.costavong.promptoverlay.protest
Device ABI: arm64-v8a; Android 8.0 / API 26 and newer.

PASSED
- APK compilation and packaging with Android SDK 36 / AGP 8.10.0 / JDK 17.
- Android lint: no Error/Fatal findings. Existing warnings remain.
- Six timeline unit tests: trim/speed mapping, invalid trim rejection, caption
  source-time mapping, bounded crossfades, equal-power audio overlap, and
  shared frame/sample clock calculations.
- APK archive integrity and all bundled native ELF load segments aligned to
  at least 16 KB. See signing/ZIP-alignment checks appended below.

INCOMPLETE — NOT A PRODUCTION RELEASE
The real Android media smoke test ran on an x86_64 API 30 emulator using forced
software CPU emulation because this environment has no /dev/kvm. It decoded
one frame, started the video encoder, and did not complete the 720p export.
The stalled attempt was stopped. This does not establish whether the cause
is the emulator or application; no successful runtime export is claimed.
The later smoke checks (audio synchronization/pitch, 1080p, local speech and
UI launch) consequently have no passing result.

REQUIRED PHONE TESTS
- On the Samsung phone: import a short camera video, trim/split it, preview,
  export 720p and 1080p, then play the saved videos outside the editor.
- Confirm audio/video synchronization at cuts, crossfades and each speed.
- Verify captions with each included speech language and edit words/timing.
- Check Arabic RTL UI/manual captions, logos/images, music mixing/fades,
  portrait/landscape crop, colour grading and source-video rotation.
- Trial green screen with real footage; defer it if quality is inadequate.
- Test several saved projects, longer media, interrupted exports, low storage,
  cancel/restart, sharing, and save to Android's document picker.

COMMERCIAL RELEASE GATES
This alpha unlocks Pro for testing and has no checkout. Implement and test
same-listing Basic/Pro entitlement, purchase and restore before sales.
Update the public privacy
policy and Play disclosures for Pro. Cloud AI/subscriptions and 4K are future
work. Release Pro entry points are deliberately disabled until entitlement
is implemented; the debug APK must not be submitted to Google Play.

FINAL PACKAGE CHECKS
- apksigner verify: success, APK signature v2.
- zipalign -c -P 16 4: success.
- Bundled ARM64 native libraries have 16 KB ELF segment alignment.
