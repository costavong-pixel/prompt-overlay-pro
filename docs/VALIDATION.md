# Prompt Overlay Basic restore validation

## Source baseline and archive

- The Git history begins at import commit a918350. It contains both Basic teleprompter code and the Pro editor; no exact source snapshot of the published v1.22.2 APK was present in the inspected history.
- The Basic engine and existing language support were retained from that source import. This branch does not claim byte-for-byte identity with the published APK.
- Before removal, the full pre-cleanup tree at bfb7143feecffe5bdda53394d018585042638580 was preserved on archive branch archive/video-editor-pre-basic-restore-20261122.

## Static source checks

- PASS: retained Basic source files are present for the floating overlay, script library, document/photo import, Bluetooth and Wi-Fi remotes, mirror reader, and language selection.
- PASS: AndroidManifest.xml parses and retains the Basic activities/service and Google Play Billing permission.
- PASS: video editor activity/export service, FileProvider, and video-export-only foreground-service permissions are absent.
- PASS: ML Kit OCR, AndroidX Core, Billing Library 9.1.0, package com.costavong.promptoverlay, and version code 58 remain configured.
- PASS: Media3, Vosk, and JNA dependencies are absent.
- PASS: PlanCatalog.REAL_CHECKOUT_ENABLED remains false; release builds retain proEnabled=false. Billing and trial files remain in source.
- PASS: the new feedback-only translation helper compiles with JDK 17 and minimal Android/API stubs; this is not a substitute for compiling the Android app.
- PASS: four editor-specific unit test files, the editor instrumentation runner, and its speech/video fixtures were removed. Six access/feedback unit test files remain.

## Build, tests, and lint

Attempted from Git Bash on Windows:

~~~sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~~~

BLOCKED before compilation: Gradle 8.14.3 could not locate an Android SDK. ANDROID_HOME and ANDROID_SDK_ROOT were unset, the default SDK directory was absent, and no Android SDK command-line tools were found. The build, unit tests, and lint tasks did not run. No pass is claimed.

## Device testing

No device or emulator testing was performed.
