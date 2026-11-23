# Prompt Overlay Basic

Prompt Overlay is one Android teleprompter for business presentations and sales, teachers and trainers, and content creators. All three audiences use the same script, scrolling, overlay, and remote-control engine.

## Basic features

- Floating teleprompter overlay that can stay above the camera app you choose.
- Automatic scrolling with adjustable speed, start delay, and repeat.
- Script library for saving, searching, tagging, archiving, and removing scripts.
- Import from text documents, PDFs, photos, and Google Drive selections.
- Font, text size, text colour, background transparency, overlay size and position, margins, line spacing, alignment, and text formatting controls.
- Mirrored full-screen reader.
- Bluetooth remote control and a local Wi-Fi browser remote.
- English, Spanish, French, and Arabic interface options.

The app does not record video or audio. Camera recording remains in the camera app you select.

## Scope of this branch

This development branch restores the Basic teleprompter. Video editing, video export, timelines, captions, effects, and rendering have been removed. The source is preserved at the archive/video-editor-pre-basic-restore-20261122 branch.

Billing-related code and configuration remain for review, but production checkout is disabled. PlanCatalog.REAL_CHECKOUT_ENABLED remains false, and release builds keep the existing proEnabled=false manifest setting. A plan screen is available only in test builds as a no-payment preview. This branch does not change Google Cloud, Play Console, or VPS configuration.

## Build and local checks

Requirements: JDK 17 and Android SDK platform 36 with build-tools 36.0.0. The repository includes the Gradle wrapper.

~~~sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~~~

On Windows, run gradlew from Git Bash or another POSIX shell.

## Source and validation notes

The Git history starts with the source import at a918350. That commit contains the Basic engine and video-editor code together; an exact source snapshot of the published Prompt Overlay v1.22.2 APK was not present in the inspected history. This branch preserves the Basic source from that import and does not claim byte-for-byte equivalence with the published APK.

See [approved scope](docs/APPROVED_SCOPE.md) and [validation notes](docs/VALIDATION.md) for the retained feature boundary and this branch's checks.
