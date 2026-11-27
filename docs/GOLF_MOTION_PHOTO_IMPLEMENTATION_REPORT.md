# CAMELEO — Golf Camera Motion Photo & UI Implementation Report

**Date**: November 27, 2026  
**Target Preset**: `disp_classic` ("Golf")  
**Hardware Verification Status**: **NOT VERIFIED** (`adb devices -l` reported zero attached devices during this build/test session)

---

## 1. Executive Summary & Strict Preservation Guarantee

CAMELEO's **Golf (`disp_classic`)** camera has been upgraded to support bounded **Motion Photo capture (1s, 2s default, 3s)**, **multi-format Motion Photo / Live Photo / clip import**, **vintage temporal rendering (`Vintage 14 FPS` / `Smooth 24 FPS`)**, a dedicated retro-tactile **Golf Viewfinder UI + Motion Settings Drawer**, unified single-item **Darkroom Gallery playback + cover frame selection**, and multi-format **Export & Share** (Original JPEG, MP4, Extract Still Frame, Animated GIF89a).

### Preservation Audit (Verified Unchanged)
- `PresetCatalog.kt` (`disp_classic`): 0 lines modified (`contrast = 1.10f`, `saturation = 1.15f`, `temperature = 0.15f`, `tint = -0.02f`, `fade = 0.05f`, `highlightRollOff = 0.88f`, `grain = grains/grain_dexp2.jpg @ 0.28f`, `lens.vignetteIntensity = 0.26f`, `lens.softness = 0.10f`, `border = BorderStyle.GOLF_FRAME`, `dateStamp = DateStampStyle.SEVEN_SEGMENT_ORANGE`, `customYearOffset = -31`).
- `CameraProfileCatalog.kt` (`DISPOSABLE_FUNS` for `disp_classic`): `lut_disp_classic.png`, `grains/grain_dfuns.jpg` (`GrainMode.SCANNED`), `GrainStage`, color profiles, and all 32 other camera presets remain completely unmodified.
- Still JPEG capture path (`ImageProcessor.processAndSaveImage`) runs identically when Motion Photo mode is disabled or in parallel with motion sampling during Motion Photo capture.
- No extra creative LUTs, VHS scanlines, scratches, aggressive blur, heavy shake, or permanent warm casts were introduced.
- Aspect ratio (`RATIO_4_3`, `RATIO_1_1`, `RATIO_16_9`, `RATIO_3_2`, landscape & portrait) is strictly preserved via `GolfMotionEncoder.computeEncodedDimensions(...)`; `9:16` cropping is never forced.

---

## 2. Architecture Overview

### 2.1 Motion Capture & Bounded Rolling Memory (`RollingMotionRingBuffer`, `GlMotionFrameExtractor`, `CameraManager`)
- **Two-Stream CameraX Discipline**: CameraX remains strictly on `Preview` + `ImageCapture` (respecting `IS_VIDEO_FEATURE_PAUSED = true` so no third hardware CameraX `VideoCapture` stream competes for ISP bandwidth).
- **Low-Overhead GL Sampler (`app/src/main/java/com/craftcam/app/gl/GlMotionFrameExtractor.kt`)**:
  - Attaches a secondary `EGLSurface` backed by an `ImageReader` (`<= 896px` long edge, `MIN_SAMPLE_INTERVAL_NS = 40_000_000L` / `<= 24 FPS` ceiling) exclusively when Golf Motion Photo is armed (`disp_classic` + `FILM` + `PHOTO` + `motion.enabled` + active lifecycle).
  - Non-blocking backpressure (`AtomicBoolean isCompressing`) ensures a busy JPEG compression thread never stalls `GLViewfinderRenderer.onDrawFrame`.
- **Bounded Compressed Buffers (`app/src/main/java/com/craftcam/app/motion/RollingMotionRingBuffer.kt`)**:
  - `RollingMotionRingBuffer`: Retains only compact JPEG `ByteArray` frames (no uncompressed full-resolution Bitmap arrays in memory), strictly bounded to `DEFAULT_MAX_FRAMES = 45`, `DEFAULT_MAX_BYTES = 6 * 1024 * 1024` (6 MB cap), and `DEFAULT_MAX_WINDOW_NS = 2_000_000_000L`.
  - `RollingAudioRingBuffer`: Optional 44.1 kHz mono PCM circular buffer (~3.5s capacity) gated cleanly by `Manifest.permission.RECORD_AUDIO`.
- **Symmetrical Capture Timing (`CameraManager.captureGolfMotionPhoto`)**:
  - For default `2s`, targets `preShutterMs = 1000L` before and `postShutterMs = 1000L` after shutter (`1s`: `500ms/500ms`; `3s`: `1500ms/1500ms`), firing `ImageCapture.takePicture` in parallel with bounded post-shutter collection and `cancelGolfMotionCapture()` cancellation safety on `setEngineMode`, `setCaptureMode`, `switchCamera`, and `shutdown`.

### 2.2 Temporal Cadence & Hardware GPU Video Pipeline (`GolfTemporalEffectPlanner`, `GolfMotionEncoder`)
- **Temporal Cadence (`GolfTemporalEffectPlanner.planTimeline`)**:
  - Resamples chronological source timestamps to exact target frame counts (`14 FPS` Vintage or `24 FPS` Smooth across `1s / 2s / 3s`), anchoring `shutterOutputIndex` closest to the shutter timestamp.
  - Applies deterministic per-frame `grainSeed` animation, subtle organic celluloid micro-flicker (`<= +/-0.026 EV`), optional brief exponential shutter-synced exposure flash bloom (`flashEffectEnabled` software EV pulse decaying cleanly over ~140ms — never strobing the hardware torch), and smooth `0.16f` crossfade on repeated source steps.
- **Hardware MediaCodec + GL ES 3.0 Pipeline (`GolfMotionEncoder.kt`)**:
  - **Pass A (FBO)**: Aspect-preserving center-crop (`computeCropUvRect`), inter-frame crossfade, and linear temporal EV modulation.
  - **Pass B (`MediaCodec` Input Surface)**: Executes the untouched `ShaderConstants.FRAGMENT_SHADER_STILL` using `CameraProfileCompatibility.resolveLutPlan(...)` and `GrainStage.resolve(...)` so motion output matches Golf still output identically.
  - **Pass C (Overlay)**: Alpha-blends optional static Golf border (`GOLF_FRAME`) and 7-segment orange date stamp (`SEVEN_SEGMENT_ORANGE`).
  - Muxes hardware H.264 (`MIME_AVC`, 7.5 Mbps) + optional AAC-LC (`MIME_AAC`) audio via `MediaMuxer`.

### 2.3 Multi-Format Motion Import (`MotionPhotoImporter.kt`)
Supports single or dual-item selection in Darkroom (`PickMultipleVisualMedia(2)` with `ImageAndVideo`):
1. **Samsung Motion Photos**: `MotionPhoto_Data` marker scanning with `SEFH...SEFT` and minimal `SEFT` trailer trimming.
2. **Google / Android Motion Photos**: XMP `Container:Directory` `Item:Length` and `GCamera:MicroVideoOffset` parsing, plus JPEG EOI (`FF D9`) -> ISO BMFF `ftyp` fallback.
3. **Apple Live Photos**: Still image + companion `.MOV` resource pairing (`apple_live_photo`).
4. **Short Video Clips**: Standalone `.mp4` / `.mov` / `.m4v` validation via `MediaExtractor`.
5. **Safe Still Fallback**: Ordinary static JPEGs return `ImportProbeResult.StillOnly` and route through standard still film development without generating fake motion.

### 2.4 Dedicated Golf UI & Darkroom Gallery Integration
- **Viewfinder (`GolfMotionUi.kt`, `ViewfinderScreen.kt`)**: Only shown when `selectedPreset.id == "disp_classic"`.
  - **Top Toolbar (`GolfTopToolbar`)**: Pixel-bordered `GOLF` badge, `MOTION ON/OFF` toggle pill, Flash cycle button, and existing More/Settings trigger.
  - **Viewfinder Overlay (`GolfViewfinderOverlay`)**: Compact corner status/duration indicator (`MOTION • 2s • 14FPS` / pulsing `REC MOTION`) and 2.5dp hairline bottom recording/developing progress bar.
  - **Bottom Controls (`GolfModeAndSettingsStrip`, expandable `CameraWheelSelector`, `GolfBottomControlRow`)**: `STILL | MOTION` mode selector, Motion Settings drawer trigger, Gallery thumbnail, Central Shutter Button (`captureGolfMotionPhoto` when armed, `captureAndProcessPhoto` when Still), Front/Rear Switch, and Golf preset artwork.
  - **Motion Settings Drawer (`GolfMotionSettingsDrawer`)**: Duration (`1s / 2s / 3s`), Playback (`Vintage / Smooth`), Flash effect (`On / Off`), Loop playback (`On / Off`), Audio (`On / Off` with runtime `RECORD_AUDIO` request).
- **Single-Item Storage & Darkroom Playback (`GolfMotionStore.kt`, `GolfMotionGalleryPlayer.kt`, `GalleryCache.kt`, `GalleryScreen.kt`, `GolfGifExporter.kt`)**:
  - Companion MP4 (`filesDir/motion_clips/motion_<mediaId>.mp4`), JSON metadata (`motion_<mediaId>.json`), and optional cover override (`cover_<mediaId>.jpg`) keep each Golf Motion Photo as a single MediaStore image item (`CAMELEO_Golf_*.jpg`) cleaned up automatically on delete/batch-delete.
  - Gallery grid renders a compact `MOTION` badge; fullscreen viewer defaults to the preserved still photograph, starts looping MP4 playback on tap/long-press or `STILL | MOTION` toggle with a hairline progress bar, loop/mute pills, and a `COVER` frame scrubber (`extractFrameAt`).
  - Share button opens `GolfMotionExportDialog` offering: Original JPEG share, `FileProvider` MP4 share, Save MP4 to `Movies/Cameleo`, Extract Still Frame to Gallery, and pure-Kotlin GIF89a export (`Pictures/Cameleo`).

---

## 3. Verification Results

| Check | Command / Target | Result |
| :--- | :--- | :--- |
| **Offline Unit Tests** | `.\gradlew.bat :app:testDebugUnitTest --rerun --offline` | **188 tests passed, 0 failed, 0 errors, 0 skipped** (includes 7 new tests in `GolfMotionPhotoTest.kt`) |
| **Debug APK Build** | `.\gradlew.bat assembleDebug --offline` | **BUILD SUCCESSFUL** (`app/build/outputs/apk/debug/app-debug.apk`, `172,224,315` bytes) |
| **Release APK & Lint** | `.\gradlew.bat assembleRelease --offline` | **BUILD SUCCESSFUL**, `lintVitalRelease` passed (`app/build/outputs/apk/release/app-release.apk`, `161,154,322` bytes) |
| **Physical Hardware (Galaxy S21)** | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe devices -l` | **NOT VERIFIED** (no ADB device connected) |

### Files Created & Modified
- **Created**:
  - [`app/src/main/java/com/craftcam/app/motion/GolfMotionConfig.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionConfig.kt)
  - [`app/src/main/java/com/craftcam/app/motion/GolfMotionStore.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionStore.kt)
  - [`app/src/main/java/com/craftcam/app/motion/RollingMotionRingBuffer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/RollingMotionRingBuffer.kt)
  - [`app/src/main/java/com/craftcam/app/motion/GolfMotionEncoder.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionEncoder.kt)
  - [`app/src/main/java/com/craftcam/app/motion/GolfMotionProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionProcessor.kt)
  - [`app/src/main/java/com/craftcam/app/motion/GolfGifExporter.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfGifExporter.kt)
  - [`app/src/main/java/com/craftcam/app/motion/MotionPhotoImporter.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/MotionPhotoImporter.kt)
  - [`app/src/main/java/com/craftcam/app/gl/GlMotionFrameExtractor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GlMotionFrameExtractor.kt)
  - [`app/src/main/java/com/craftcam/app/ui/viewfinder/GolfMotionUi.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/GolfMotionUi.kt)
  - [`app/src/main/java/com/craftcam/app/ui/gallery/GolfMotionGalleryPlayer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GolfMotionGalleryPlayer.kt)
  - [`app/src/main/res/xml/file_paths.xml`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/xml/file_paths.xml)
  - [`app/src/test/java/com/craftcam/app/motion/GolfMotionPhotoTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/motion/GolfMotionPhotoTest.kt)
- **Modified**:
  - [`AndroidManifest.xml`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/AndroidManifest.xml) (`FileProvider`)
  - [`AppSettings.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/config/AppSettings.kt), [`CameraState.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraState.kt), [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt)
  - [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt)
  - [`GalleryCache.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryCache.kt), [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt), [`ViewfinderScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ViewfinderScreen.kt)
