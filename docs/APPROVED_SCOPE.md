# Prompt Overlay Basic scope

This branch keeps one teleprompter engine for three audiences:

1. Business presentations and sales
2. Teachers and trainers
3. Content creators

These are use cases for the same app, not separate products.

## Retained Basic functionality

- Floating overlay reader with automatic scrolling and adjustable speed.
- Prompt delay and repeat controls.
- Script library, including saved scripts, search, tags, archive, and delete.
- Script import from supported text, document, photo/OCR, and selected Google Drive sources.
- Font family, size, colour, background transparency, overlay size and position, margins, line spacing, alignment, and formatting controls.
- Mirrored full-screen reader.
- Bluetooth remote controls and local Wi-Fi browser remote.
- Existing English, Spanish, French, and Arabic interface support.

The user's selected camera app records video. Prompt Overlay does not add camera or microphone capture.

## Excluded from this branch

Video editing, export, timelines, captions, effects, rendering, speech packs, and editor-specific dependencies are removed. The editor source is retained on the separate archive branch archive/video-editor-pre-basic-restore-20261122.

## Billing boundary

Billing and trial source/configuration remain available for review. Checkout remains disabled (PlanCatalog.REAL_CHECKOUT_ENABLED=false), release builds retain proEnabled=false, and the plan screen is reachable only in test builds. No production billing, Google Cloud, Play Console, or VPS configuration is changed.
