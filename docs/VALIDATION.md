# Alpha validation

Prompt Overlay Pro alpha 6 source update, October 9, 2026

No APK was built and no tests were run for alpha 6. Phone testing remains required for the revised home layout, project-name wrapping in all four languages, successful-save rating prompt, and Play Store link. Alpha 5's tutorial was removed by request.

Historical: Prompt Overlay Pro alpha 4 — build validation, October 8, 2026

Version: 2.0.0-pro-alpha4 (code 40)
Application ID: com.costavong.promptoverlay.protest
Device ABI: arm64-v8a; Android 8.0 / API 26 and newer.

PASSED
- APK compilation and packaging with Android SDK 36 / AGP 8.10.0 / JDK 17.
- Android lint: no Error/Fatal findings. Existing warnings remain.
- Six timeline unit tests: trim/speed mapping, invalid trim rejection, caption
  source-time mapping, bounded crossfades, equal-power audio overlap, and
  shared frame/sample clock calculations.
- Four tests of the actual Media3 SonicAudioProcessor used by export: normal
  speed initialization, 0.5x and 2x PCM duration/pitch, and 44.1-to-48 kHz
  resampling with 1.5x speed. These run on the host JVM, without Android codecs.
- Regression control: temporarily restoring the original no-argument flush
  makes all four audio tests fail with the exact exception captured on the
  phone. The corrected call is restored for the delivered APK.
- Seven clip-player timing tests: trimmed source offsets, faster/slower
  playback, split source offsets, crossfade timeline offsets, bounded seeking
  and fractional-millisecond cuts. These validate position mapping, not native
  player rendering or UI behavior on a phone.
- APK archive integrity and all bundled native ELF load segments aligned to
  at least 16 KB. See signing/ZIP-alignment checks appended below.

PHONE RESULTS — ALPHA 2
- The original five-second export failure was "AudioProcessor must implement
  at least one #flush() overload." Alpha 2 replaced the deprecated no-argument
  SonicAudioProcessor flush with StreamMetadata.DEFAULT.
- User-provided exported MP4: 3.05 seconds, 720 × 1280, H.264 and AAC.
- User-provided exported MP4: 30.06 seconds, 1080 × 1920, H.264 and stereo
  48 kHz AAC. Both files passed full host video/audio decoding.
- The user confirmed editing/trimming worked after export, then confirmed
  captions and music worked. These reports do not establish all speech
  languages, precise synchronization, fades, effects or long-video behavior.

ALPHA 3 PLAYBACK — PHONE CONFIRMED BY USER
- Play clip starts the selected original clip in the editor without a render.
  It respects trim/speed/mute and follows the project timeline position.
- Pause/resume, replay at the clip end, timeline seeking and clip selection.
  Seeking or selecting another clip stops playback; press Play clip again.
- Stops/releases playback when leaving the editor or starting a render job.
- This player plays one source clip, without edits such as crop, grading,
  green screen, captions, graphics, transitions or mixed music. Edited preview
  still renders those changes. Direct playback volume is capped at 100%.
- The user confirmed the Play clip button works on the phone. Details such as
  every speed, trim boundary, pause/resume, seeking and clip switching have not
  each been separately reported as tested.

EMULATOR LIMITATION
An earlier alpha 1 x86_64 API 30 smoke attempt decoded a frame and started
encoding under forced software CPU emulation, but export did not complete.
The attempt was stopped. No emulator smoke-test pass is claimed.

REQUIRED PHONE TESTS
- On the Samsung phone: select a video track clip, Play clip, Pause/resume,
  seek into a trimmed/split clip, switch clips, replay at the end, try speed
  0.5x/2x and mute, and leave/return to the editor. Confirm no render starts.
- Check Edited preview/export after playback and confirm captions/music remain
  present in the saved video.
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
- Test signing identity matches the alpha 1 test build, allowing an update of
  com.costavong.promptoverlay.protest without uninstalling or clearing projects.
