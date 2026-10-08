# Prompt Overlay Pro

Android teleprompter and local video-editor prototype. Current version:
**2.0.0-pro-alpha3** (version code 39).

This is an alpha. Alpha 3 adds immediate playback of the selected source clip
inside the editor, without rendering, and a moving timeline playhead. The new
phone playback controls still need device testing. Alpha 2 phone testing
confirmed short 720p and approximately 30-second 1080p exports, trim/edit
exports, captions and music; this does not validate every language, effect or
long-video case. Purchases and restore are not implemented.

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

The default test APK targets ARM64 and installs as **Prompt Overlay Pro Test**
(`com.costavong.promptoverlay.protest`) beside the published Basic app. It uses
the local Android debug signing identity; no keystore is included here.

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
