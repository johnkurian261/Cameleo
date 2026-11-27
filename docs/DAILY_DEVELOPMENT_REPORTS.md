# CAMELEO — Daily Development Progress & Architectural Reports

This document tracks daily engineering milestones, architectural audits, test suite health, physical device deployments, and feature additions across the CAMELEO development lifecycle.

---

## Daily Log: November 26, 2026

### Focus Areas
1. **Color Profile Architecture Phase 2 (Dedicated 3D LUTs & Sensitometry)**
2. **Camera Icon Pixel Art Extraction & Replacement (D Funs, Zero, DQS, D3D, CPM35)**
3. **Hardware-Aware White Balance & Single-Application Invariant**
4. **Camera Autofocus Intelligence & Focus Stability**
5. **App Stability & Capture Freezes Elimination**
6. **Ricoh GR Digital (2005) CCD Profile Calibration**
7. **Skin Colour Science Calibration Integrity & Authenticity Audit**
8. **D3D Stereoscopic Lens Discipline & Fisheye Exclusion**

---

### Detailed Daily Milestones

#### 1. Color Profile Architecture Phase 2 (`65f884d`)
- **Audit & Decoupling**: Audited `Localizable.strings` and `DZImageFilter.framework`, confirming that Dazz Cam backs each color profile with dedicated 3D lookup tables (`lookup_*`). Strictly decoupled the **Physical Camera Body** (optics, distortion, softness, vignette, chromatic aberration, halation, grain, frame, shutter, flash) from the **Color Profile** (film emulsion, dye density curves, non-linear S-curves, tone reproduction).
- **Dedicated 64×64×64 3D LUTs**: Generated 10 calibrated $512 \times 512$ Hald-8 PNGs for all profiles:
  - `03G`: Warm daylight movie film, lifted toe, gentle shoulder, golden amber midtone bias (`lut_profile_03g.png`).
  - `C400`: Signature balanced ISO 400 consumer color print film, authentic skin tones, rich olive greens (`lut_profile_c400.png`).
  - `07FF`: Vision3 50D/250D scanner look, vibrant cyan/teal sky separation, clean highlights (`lut_profile_07ff.png`).
  - `19P`: Tungsten movie stock on cinema print tones, deep violet/magenta shadow density (`lut_profile_19p.png`).
  - `19P2`: Generic print tones, softer midtone gradation, delicate orchid shadows (`lut_profile_19p2.png`).
  - `19M`: Monitor CRT calibrated tones, warm amber midtones, neutral blacks (`lut_profile_19m.png`).
  - `22M`: Double-X 5222 panchromatic monochrome, spectral weighting $\text{Luma} = 0.35R + 0.45G + 0.20B$, zero skin hue drift (`lut_profile_22m.png`).
  - `EG200`: Consumer everyday daylight film, subtle green shadow tint, vibrant dynamic range (`lut_profile_eg200.png`).
  - `FX33`: Pocket compact CCD digital grade, cool neutrals, punchy consumer saturation (`lut_profile_fx33.png`).
  - `FXN2`: Second-generation refined negative film, terracotta/apricot skin tones, gentle contrast (`lut_profile_fxn2.png`).
- **Compatibility Matrix ([`CameraProfileCompatibility.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/CameraProfileCompatibility.kt))**: Defined per-camera supported profiles, defaults (e.g. `C400` for 35mm/disposables, `19P` for D3D/CT2F, `FX33` for CCDs), disabled profile swapping for Zero (modern ISP) and camcorders.
- **Single-Application Invariant & White Balance**: Guarded against double color grading; zeroed software temperature offset when hardware WB is active.
- **Achromatic Monochrome Guarantee**: Added `isMonochrome` bypass to `SkinToneIntelligenceEngine` to eliminate any skin tone steering on black-and-white output.
- **Diagnostic Inspector ([`ColorProfileInspector.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/ColorProfileInspector.kt))**: Created developer inspector producing markdown diagnostics for active body, LUT, color spaces, and invariant status.
- **Real Preview Thumbnails**: Generated 10 photographic thumbnails (`thumb_profile_*.png`) showing actual photographic output in the bottom sheet.
- **Automated Tests**: Added 7 new tests in `ColorProfilePhase2Test.kt` (test suite increased to **167 passing tests**).

#### 2. Camera Pixel Art Icon Replacements (`3418edc`, `d03609c`, `b061832`)
- Extracted and centered high-contrast pixel art camera silhouettes onto transparent $198 \times 198$ RGBA canvases using border-connected BFS flood-fill (`threshold = 12`) to eliminate black halos while preserving dark interior viewfinders and lenses:
  - **D Funs** (`cam_dfuns.png`, `cam_d_funs.png`): Yellow disposable body with red diagonal stripe and "Dazz" brand.
  - **Zero** (`cam_zero.png`): Vertical slate smartphone silhouette with triple-lens module.
  - **DQS** (`cam_dqs.png`): Green top band, red divider stripe, tan lower body, flash window.
  - **D3D** (`cam_d3d.png`): Triple-lens stereoscopic camera body with orange bezel and dual sensor viewfinders.
  - **CPM 35** (`cam_cpm35.png`): Ivory disposable body with dark top plate, concentric lens rings, and dual grip buttons.

#### 3. Hardware-Aware White Balance Architecture (`3418edc`, `f5ad4a2`)
- **Samsung Galaxy S21 Sensor Calibration ([`HardwareWhiteBalanceCalibrator.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/HardwareWhiteBalanceCalibrator.kt))**:
  - Calibrated against Sony IMX555 / IMX374 spectral matrices across $[2500K..9500K]$ using Planckian locus ($y = -3.000 x^2 + 2.870 x - 0.275$).
  - Outputs `COLOR_CORRECTION_GAINS` directly to Camera2 ($R \approx 2.187, G = 1.0, B \approx 1.688$ at 5500K daylight; $R \approx 1.305, G = 1.0, B \approx 2.696$ at 2856K tungsten).
- **Single-Application Invariant**: Shaders and CPU `ImageProcessor` verify `isHardwareWbActive` and suppress software user Kelvin offset to eliminate duplicate color shifts.
- **Preset Policies**: Configured `FIXED_DAYLIGHT` (5500K) for disposables, `FIXED_TUNGSTEN` (3200K) for CT2F, `AUTO` for Zero, `HYBRID_ILLUMINANT` for vintage CCDs.

#### 4. Ricoh GR Digital (2005) CCD Profile Calibration (`b4d6eaa`)
- Replaced film emulation on `analog_warm_film` / `ccd_grd` with authentic Ricoh GR Digital (2005) 8.1MP CCD rendering.
- Eliminated inappropriate analog film dust, scratches, and orange halation from the digital camera profile.
- Generated dedicated `lut_ccd_grd.png` with punchy neutral greens, high dynamic range digital contrast, and cool-neutral shadow tone.

#### 5. App Stability & Shutter Reliability (`ea43a28`, `ae900d7`)
- **Watchdog & Freeze Prevention**: Implemented lifecycle-safe preview watchdog, try-catch guards on `imageReader.acquireLatestImage()`, and safe GL context checks.
- **ZSL Buffer Drops**: Resolved buffer drops on Samsung S21 by configuring `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY`.
- **Automatic Shutter Retry**: Added automatic retry mechanism on timeout or shutter lag.

#### 6. Hardware-Aware Autofocus Subsystem (`2f5a312`)
- Created centralized `AutofocusController` managing Camera2 `CONTROL_AF_MODE`, `CONTROL_AF_TRIGGER`, and `CONTROL_AF_STATE`.
- Implemented continuous picture autofocus (`CONTINUOUS_PICTURE`) with touch-to-metering region coordinate mapping, letterbox aspect ratio projection, and pre-capture settling gate.

#### 7. D3D Stereoscopic Lens Discipline & Fisheye Exclusion (`ac36a15`)
- **Optical Incompatibility**: Excluded circular fisheye (`FISHEYE_F`) and full-frame fisheye (`FISHEYE_W`) lenses from the D3D (`d_3d`, `analog_cinema_film`) camera profile. Fisheye curvilinear warping and black hood vignetting severely corrupt stereoscopic horizontal parallax alignment, causing depth estimation failure and severe retinal rivalry.
- **Catalog & Selection Validation**: Updated `D_3D.supportedLenses` in `CameraProfileCatalog.kt` to strictly omit fisheye lenses. Added validation in `CameraManager.selectLensAccessory` to prevent mounting unsupported lenses on D3D.
- **Viewfinder & Pipeline Guards**: Guarded `GLViewfinderRenderer.kt` and `ImageProcessor.kt` so that D3D rendering automatically falls back to `LensProfile.NORMAL` if an invalid lens state is passed.
- **Automated Unit Tests**: Added `d3dCameraProfileStrictlyExcludesFisheyeLenses` in `ThreeDModeTest.kt` asserting that `D_3D.supportedLenses` contains only compatible optical accessories.

#### 8. Memory Lifecycle Leak Prevention & UI State Synchronization (`090d9fc`)
- **Collection Shelf Selection Sync ([`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt))**: Fixed state desynchronization where `selectedColorProfileIndex` remained anchored to the initial preset index when switching cameras in the shelf. Keyed `remember(selectedPresetState.id, colorProfileIndex)` and updated `selectedColorProfileIndex` on `onSelect` with the newly selected camera's saved preference (`AppSettingsStore.getColorProfileForCamera(preset.id)`).
- **Android LMK Memory Pool Eviction ([`MainActivity.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/MainActivity.kt))**: Implemented `onTrimMemory(level: Int)` and extended `onDestroy()`. Clears bounded pools (`BitmapPool.clear()`, `IntArrayPool.clear()`, `FloatArrayPool.clear()`) during `TRIM_MEMORY_MODERATE` to prevent Android low-memory killer eviction.
- **Defensive 3D Stereo Optical Guards ([`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt))**: Enforced `effectiveLensProfile = LensProfile.NORMAL` and `isFisheye = false` inside `processAndSaveThreeDImage` to defend against invalid external calls.

#### 9. Comprehensive Performance Engineering & Memory Footprint Optimization
- **Pixel Loop JIT Dead-Code Elimination ([`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt))**: Gated 12 per-pixel observer callback invocations behind loop-invariant guard `if (hasObserver) observer?.pixel(...)`. For 12MP photos ($12.2\text{M}$ pixels), this eliminates **132,000,000 null-check branches** in the hot parallel photo development loop, enabling aggressive ART/JIT loop vectorization and instruction pipelining.
- **Native Bitmap Heap Recycling ([`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt))**: Added explicit `bmp.recycle()` and `lut512.recycle()` calls to `getOrLoadLut()`. Prevents temporary decoded 512x512 ARGB_8888 bitmap buffers (~1MB native heap per LUT load) from accumulating before garbage collection.
- **LMK Cache Flush ([`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt), [`MainActivity.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/MainActivity.kt))**: Introduced thread-safe `ImageProcessor.clearCaches()` hooked into `MainActivity.onTrimMemory` and `onDestroy()`, instantly evicting decoded LUT pixel tables, grain texture maps, and overlay buffers during OS backgrounding.
- **Zero-Allocation Skin Mask Refinement ([`SkinMaskRefiner.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/skin/SkinMaskRefiner.kt))**: Replaced `FloatArray(total)` with `FloatArrayPool.acquire(total)` for intermediate and output feathered masks, eliminating up to **48.8MB heap allocation** on every portrait capture.
- **120Hz GPU RenderNode GraphicsLayer ([`CameraWheelSelector.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/CameraWheelSelector.kt))**: Replaced `.scale(scale).alpha(alpha)` modifiers with `.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }`. Defers scale/alpha mutations directly to the GPU display list RenderNode, bypassing Compose recomposition and relayout during rotary dial dragging.
- **LazyRow Recomposition Filtering ([`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt))**: Memoized `activeCategories` via `remember(visibleCategories, shelf)` and assigned stable keys (`key = { it.name }`, `key = { it.id }`) to category tabs, color profile cards, and lenses row.

---

### Commits Completed Today (Nov 26, 2026)
1. `65f884d` — *feat(color): implement Color Profile Phase 2 dedicated 3D LUTs and authentic camera integration*
2. `b061832` — *feat(camera): replace CPM35 icon with new ivory disposable pixel art drawable*
3. `d03609c` — *feat(camera): replace D3D icon with new triple-lens 3D pixel art drawable*
4. `3418edc` — *feat(camera): update D Funs, Zero, and DQS icons and implement hardware white balance calibration*
5. `b4d6eaa` — *fix(grd): implement authentic Ricoh GR Digital 2005 CCD profile, eliminate film dust and halation, and generate dedicated lut_ccd_grd*
6. `ae900d7` — *fix(capture): eliminate ZSL buffer drops with MINIMIZE_LATENCY, stabilize D FUNS stream, and add automatic capture retry*
7. `ea43a28` — *fix(stability): prevent capture freezes with try-catch guards, lifecycle-safe preview watchdog, and GL context checks*
8. `2f5a312` — *feat(focus): implement hardware-aware autofocus subsystem, coordinate letterbox projection, and settling gate*
9. `21842fa` — *feat(color): calibrate source-relative skin protection, D3D shadow neutrality, and gallery cleanup*
10. `2130286` — *docs(reports): create persistent daily development log tracking architecture, commits, and device tests*
11. `ac36a15` — *fix(d3d): exclude fisheye lenses from D3D camera profile and enforce optical compatibility guards*
6. `ae900d7` — *fix(capture): eliminate ZSL buffer drops with MINIMIZE_LATENCY, stabilize D FUNS stream, and add automatic capture retry*
7. `ea43a28` — *fix(stability): prevent capture freezes with try-catch guards, lifecycle-safe preview watchdog, and GL context checks*
8. `2f5a312` — *feat(focus): implement hardware-aware autofocus subsystem, coordinate letterbox projection, and settling gate*
9. `21842fa` — *feat(color): calibrate source-relative skin protection, D3D shadow neutrality, and gallery cleanup*
10. `a887a31` — *feat(cpm35): calibrate authentic CCD electronic noise, gradient-adaptive detail, and digital highlight clipping*
11. `8a832f7` — *feat(skintone): implement Phase 3 authentic camera rendering, source-relative protection, and full-res optimization*
12. `0deac49` — *feat(color): implement authentic Dazz Cam 10-profile color scheme architecture*
13. `f5ad4a2` — *fix(camera): align temperature control with Dazz Cam reverse engineering analysis*
14. `378ad2b` — *fix(gallery): resize oversized back button to proportional 44dp circular target*
15. `69e6bbe` — *feat(color): implement camera-specific skin colour science for digicam and disposable profiles*
16. `c4fcce0` — *feat(d3d): calibrate cinema LUT and implement dedicated D3D skin profile with yellow-cast guard*

---

## Daily Log: November 25, 2026

### Focus Areas
1. **Skin Tone Intelligence Engine Phase 1 & 2**
2. **Digicam Physical Accuracy & Hybrid Illuminant Estimation**

### Detailed Daily Milestones
- **Skin Tone Intelligence Engine (`10233ae`)**: Built 4-layer computational skin intelligence system:
  - Layer 1: Face anatomy detection & spatial prior weighting.
  - Layer 2: Fast color-space skin probability density estimation.
  - Layer 3: Guided mask feathering and boundary refinement.
  - Layer 4: Camera-specific hue steering and highlight roll-off.
- **Continuous OKLCh Color Modeling (`07f7fa5`)**: Upgraded hue steering to perceptual OKLCh color space ($L, C, h$) with smooth sigmoid protection curves to prevent edge posterization.
- **Hybrid Illuminant Estimation (`4d393e7`)**: Implemented Grey-World and Max-RGB chromaticity estimator in `HybridIlluminantEstimator.kt` to classify scene lighting into Daylight, Tungsten, Fluorescent, or Flash.

---

## Daily Log: November 24, 2026

### Focus Areas
1. **Release Management & Version Bump**
2. **D3D Capture Pipeline & In-App Video Playback**

### Detailed Daily Milestones
- **Release v2.2.0 (`af992c7`)**: Tagged and documented v2.2.0 release, updated GitHub Actions APK release workflows.
- **D3D & Video Playback (`3d21ea5`)**: Refined stereoscopic multi-frame rendering for D3D stereo capture and improved hardware video decoding in the media player.

---

## Daily Log: November 22, 2026

### Focus Areas
1. **UI Refinements & Zoom Mode Normalization**

### Detailed Daily Milestones
- **UI Tuning (`e05811c`)**: Normalized pill icon sizes in viewfinder chrome, refined D3D processing indicator animations, and streamlined zoom mode controls ("Frame" vs "Lens").

---

## Daily Log: November 12, 2026

### Focus Areas
1. **Dazz Cam Tactile Controls & UI Reverse-Engineering**
2. **D3D Stereoscopic Parallax Calibration**

### Detailed Daily Milestones
- **Dazz Cam Temperature & Exposure Slider (`376446c`, `2f0944b`, `d139b98`)**: Engineered exact tactile temperature slider with dotted line markings, black-and-white ring thumb, and haptic feedback.
- **Stereoscopic Wigglegram (`a0ebc62`, `7938d11`, `5bb8381`)**: Calibrated depth stratification and convergence pivot lock to ensure zero horizontal drift during 3-frame looping animations.

---

## Entry 5: CPM 35 Live Preview Pipeline Parity and Production-Grade Correction (2026-11-26 15:10)

### 1. Root Cause Identification
- **Pipeline Split**: Still photo capture of CPM 35 ran `DigicamColorEngine.process()` on CPU and returned immediately, completely bypassing `lut_disp_flash.png`. In contrast, live preview and video shaders sampled `lut_disp_flash.png` and applied duplicate contrast and saturation alongside hardcoded GLSL skin thresholds.
- **GLSL Skin Distortion**: `ShaderConstants.kt` used hardcoded ratios (`maxHealthyG = color.r * 0.84`, `minHealthyB = color.r * 0.36`) on post-grade pixels, causing severe green subtraction, hot magenta hue drift, and hard $R=255$ highlight clipping.
- **Resolved Previous Contradictions**: Clarified that $R/B > 3.8$ was an observed symptom of the uncorrected strobe blue-starvation defect, while tabulated values ($1.72–2.63$) represent the calibrated target locus. Red clipping at 255 was eliminated by adding an asymptotic soft-knee highlight shoulder.

### 2. Architecture & Shader Implementations
- **Preview Diagnostic Mode**: Added `uDiagnosticMode` (modes 0..6: Production, Passthrough, LUT-only, Profile-only, No Skin Prot, Skin Locus Mask, Parity) with Kotlin bindings in `GLViewfinderRenderer.kt` and `CameraManager.kt`.
- **Pre-Grade Skin Tone Protection**: Replaced hardcoded thresholds in `FRAGMENT_SHADER_VIEWFINDER` and `FRAGMENT_SHADER_STILL` with continuous elliptical locus evaluation derived from pristine `preGradeColor`, smoothstep luminance gating, smooth mustard suppression, continuous blue headroom, and rational asymptotic highlight compression.
- **Calibrated LUT with Complementary Inversion**: Regenerated `lut_disp_flash.png` using inverse shader contrast (1.04) and saturation (1.02), achieving exact $\Delta E = 0.00$ colorimetric parity with `DigicamColorEngine`.

### 3. Verification & Metrics
- **Unit Tests**: 169 tests completed, 0 failures (100.0% pass rate).
- **Physical Device**: Deployed and tested on Samsung Galaxy S21 5G (`SM-G991B`, Android 14); Native Heap 34.1 MB; zero GL errors.

---

## Entry 6: Ricoh GR Digital (2005) CCD Color Science Deep Audit and Preview Correction (2026-11-26 15:30)

### 1. Root Cause Identification
- **Runaway Cyan/Blue Drift on Neutral Grayscale**: In `generate_all_luts.py` (`grade_ccd_grd`), asymmetric shadow cooling caused $b > r$ on all neutral tones, triggering an unconditional `if b > r:` boost ($b \times 1.14$, $r \times 0.96$) from black to pure white. Neutral gray 128 was corrupted with $+25.5$ blue cast, and pure white 255 had red clipped to 237.8 with cyan tint.
- **Double Contrast Multiplication**: The LUT encoded contrast $1.28$ while the viewfinder shader applied contrast $1.16$, creating an effective contrast of $\approx 1.485$ that crushed all shadow detail below $20/255$ to hard black.
- **Scanned Photochemical Film Artifacts**: Digital CCD preset was mistakenly bound to scanned 35mm film grain (`grain_ccdr.jpg`) and scanned film mechanical vignette (`vignette_dclassic.jpg`).
- **Legacy Preset ID Routing**: Preset was registered under legacy `analog_warm_film` instead of dedicated CCD digital identification.

### 2. Architecture & Pipeline Implementations
- **Canonical Preset ID & Backward Compatibility**: Introduced canonical preset ID `"ccd_grd"` across `PresetCatalog.kt`, `CameraProfileCatalog.kt`, `ShaderConstants.kt`, and `ImageProcessor.kt`, with robust alias fallback for `"analog_warm_film"`, `"grd"`, `"gr d"`, `"gr dr"`, and `"gr digital"`.
- **Strict Neutral Gray Axis Preservation**: Rewrote `grade_ccd_grd` with strict chroma gating ($\text{chroma} \le 0.015$). Output maintains $R_{out} = G_{out} = B_{out}$ across all 256 luminance levels ($\Delta E = 0.00$, max neutral channel difference = 0).
- **Calibrated CCD Tone Curve**: Balanced midtone contrast (1.14) with complementary LUT contrast (1.10) to match the shader base (1.04). Shadow detail at $10/255$ is cleanly preserved at $8/255$. Smooth rational highlight shoulder at $0.94$ with continuous rolloff.
- **Procedural CCD Electronic Sensor Noise**: Switched `grainProfile` to `GrainMode.PROCEDURAL` (`intensity = 0.09f`, `scale = 0.50f`, `isColorNoise = true`, `assetPath = ""`).
- **Elimination of Analog Leaks**: Removed `vignette_dclassic.jpg` and static grain from GR D. Set dust, light leaks, and halation to disabled.
- **Native Aspect Ratio & Prime FOV**: Standardized to 4:3 native aspect ratio and 28mm wide prime with 47mm digital crop mode.

### 3. Verification & Metrics
- **Unit Tests**: 170 tests completed, 0 failures (100.0% pass rate).
- **Physical Device**: Deployed and tested on Samsung Galaxy S21 5G (`SM-G991B`, Android 14); Native Heap 29.8 MB, Dalvik Heap 6.0 MB; zero GL errors.

---

## Test & Build Verification Summary

| Metric | Status |
| :--- | :--- |
| **Unit Test Suite** | **170 tests passing** (0 failures, 0 skipped, 100.0% pass rate) |
| **Build State** | `assembleDebug` SUCCESSFUL |
| **Physical Hardware** | Verified on Samsung Galaxy S21 5G (`SM-G991B`, Android 14) via ADB |
| **Git Working Tree** | Clean, verified and documented |

---

## Entry 7: Comprehensive Architecture Verification, Pipeline Unification, Concurrency/Memory Hardening & Hardware WB Green-Tint Fix (2026-11-27 01:15)

### 1. Confirmed Bugs & Root Causes Fixed
- **Default Color Profile Override (`BUG-01`)**: `CameraProfileCompatibility`, `GLViewfinderRenderer`, `GPUVideoProcessor`, and `ImageProcessor` replaced 28 cameras' calibrated native LUTs (`D FUNS`, `GR D`, `DQS`, `Classic U`, `FXNR`, `D3D`, `CPM 35`, `CCD R`, etc.) with `C400`/`FX33`/`19P`/`FXN2` profile LUTs and stacked extra profile deltas. Implemented `CameraProfileCompatibility.resolveLutPlan(...)` so default profiles execute 100% of the camera's own LUT + `NEUTRAL_ADJUSTMENT`, custom chromatic profiles blend 35% camera / 65% profile, `22M` monochrome stays strictly in `SRGB` input space (`primaryLutIsProfile`), and fixed cameras (`vsco_b1`, `vsco_x1`, `ccd_cool_digital`, `vsco_handycam`, `zero`, `analog_dblack`, `cam_*`) isolate cleanly.
- **Hardware White Balance Green Tint (`BUG-02`)**: `HardwareWhiteBalanceCalibrator` passed raw `XYZ -> Sensor` `SENSOR_COLOR_TRANSFORM1/2` (S21 wide row sums `[0.435, 0.965, 0.674]`, Green 2.22x Red) into `CaptureRequest.COLOR_CORRECTION_TRANSFORM` (Balanced Sensor -> Linear sRGB). Derived exact row-normalized `XYZ_D50_TO_SRGB * SENSOR_FORWARD_MATRIX1/2` CCMs (every row summing to `1.000f`) from `research/dumpsys_media_camera.txt` for all S21 sensors (`0`, `1`, `2`, `21`/`3`), added runtime `CameraCharacteristics` extraction, wired active lens camera ID switching + Pro manual CCM in `CameraManager`, and isolated non-Samsung OEMs from S21 coefficients.
- **Video, Digicam, Starburst & Flash Parity (`BUG-03..06`)**: Upgraded `GPUVideoProcessor` with `lutPlan`, secondary 2D LUT binding, scanned grain upload via `GrainStage.resolve(...)`, `isHardwareWbActive` guard, and `intensity` scaling. Updated `ImageProcessor` (`applyDigicamProfileAndTuningPass` for `CPM 35`/`CCD R`, removed duplicate `applyStarburstDiffraction` call, gated `flash_01.jpg` on `isFlashActive`, unified `finishFilmDevelopment` + `developBitmap` + `GalleryScreen` EXIF-oriented mutable decoding & negative-first redevelopment).
- **D FUNS Duplicate Grading (`BUG-07`)**: Neutralized duplicate `PresetCatalog.ccd_flash.colorGrading` stacking on `lut_dfuns_v1.png` (`MAE` on `research/dfuns/dfuns-validation/` improved `14.291 -> 10.179`), protected `lut_dfuns_v1.png` from `generate_all_luts.py`, and left `D FUNS` grain (`grains/grain_dfuns.jpg`) untouched.
- **Concurrency & Memory Safety (`BUG-08..10`)**: Replaced `ForkJoinPool.commonPool()` + `CountDownLatch.await(2000L)` across 6 engines with bounded `ParallelPixelRunner` (`AtomicIntegerArray` deterministic drain state machine guaranteeing workers finish before buffer `release()`), guarded all 4 `ImageProcessor` pipelines with `processingSemaphore.withPermit`, removed eager `bmp.recycle()` in `ImageProcessor.clearCaches()` / `BorderRenderer.clearCache()`, added atomic `captureGeneration` watchdog tokens, and converted `BufferPool` (`BitmapPool`, `IntArrayPool`, `FloatArrayPool`) to heap-fraction byte budgets + `MainActivity.onTrimMemory` trimming.

### 2. Files Modified / Added
- Production: `CameraProfileCompatibility.kt`, `ColorProfileInspector.kt`, `GLViewfinderRenderer.kt`, `GPUVideoProcessor.kt`, `ImageProcessor.kt`, `BorderRenderer.kt`, `ParallelPixelRunner.kt` (new), `BufferPool.kt`, `SkinColourEstimator.kt`, `SkinMaskRefiner.kt`, `SkinToneIntelligenceEngine.kt`, `DigicamColorEngine.kt`, `DigicamOpticalEngine.kt`, `DigicamSensorEngine.kt`, `HardwareWhiteBalanceCalibrator.kt`, `DeviceCalibrationManager.kt`, `CameraProfileManager.kt`, `CameraManager.kt`, `PresetCatalog.kt`, `GalleryScreen.kt`, `MainActivity.kt`, `generate_all_luts.py`.
- Tests & Docs: `CameleoProductionSafetyTest.kt` (new, 11 tests), `docs/CAMELEO_COMPREHENSIVE_ARCHITECTURE_AUDIT.md` (new).

### 3. Tests, Builds & Hardware Status
- **Unit Tests**: `.\gradlew.bat testDebugUnitTest --offline` -> **181 tests total, 181 passed, 0 failed, 0 skipped** (`BUILD SUCCESSFUL`).
- **APK Builds**: `assembleDebug` (`173,208,669 B`) & `assembleRelease` (`164,462,282 B`, `lintVitalRelease` passed) -> **`BUILD SUCCESSFUL`**.
- **Physical Hardware (Galaxy S21 5G)**: **NOT VERIFIED** during this session (`adb devices -l` empty).
- **Next Priorities**: Reconnect S21 via ADB for hardware capture/PSS/FPS verification across all 33 presets, and resolve Play Store third-party grain/border/branding assets documented in [CAMELEO_COMPREHENSIVE_ARCHITECTURE_AUDIT.md](file:///c:/Users/User/Downloads/DazzProject/docs/CAMELEO_COMPREHENSIVE_ARCHITECTURE_AUDIT.md).

---

## Entry 8: Golf Camera (`disp_classic`) Motion Photo Capture, Multi-Format Import, GPU Temporal Pipeline & UI Upgrade (2026-11-27 07:58)

### 1. Summary of Completed Work
- **Zero Color/Still Alteration Guarantee**: Verified `PresetCatalog.kt` (`disp_classic`), `CameraProfileCatalog.kt` (`DISPOSABLE_FUNS` / `lut_disp_classic.png` / `grains/grain_dfuns.jpg` / `grains/grain_dexp2.jpg`), `ShaderConstants.kt`, and all other 32 presets remain untouched. Motion output renders directly through unmodified `FRAGMENT_SHADER_STILL` + `resolveLutPlan(...)` + `GrainStage.resolve(...)`.
- **Bounded Rolling Motion & Audio Capture**:
  - Added [`GlMotionFrameExtractor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GlMotionFrameExtractor.kt) and wired hooks into [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt) to sample <= 24 FPS frames into [`RollingMotionRingBuffer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/RollingMotionRingBuffer.kt) (45 frames / 6 MB compressed JPEG cap / 2s rolling window) with non-blocking backpressure, paired with optional `RECORD_AUDIO`-guarded `RollingAudioRingBuffer` PCM capture.
  - Added symmetrical 1s / 2s (default) / 3s capture coordination (`captureGolfMotionPhoto`, `syncGolfMotionArming`, `cancelGolfMotionCapture`) in [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt), keeping CameraX strictly on 2 streams and preserving normal still photography when Motion is disabled.
- **Temporal Cadence & Hardware H.264/AAC + GIF89a Encoding**:
  - Added `GolfTemporalEffectPlanner` (Vintage 14 FPS vs Smooth 24 FPS, animated `grainSeed`, +/- 0.026 EV organic flicker, optional shutter-synced flash bloom without torch strobing), [`GolfMotionEncoder.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionEncoder.kt) (aspect-preserving 16px-aligned dimensions, 3-pass GL ES 3.0 + MediaCodec Surface), [`GolfMotionProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionProcessor.kt), and pure-Kotlin GIF89a [`GolfGifExporter.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfGifExporter.kt).
- **Multi-Format Motion Photo Import**: Implemented [`MotionPhotoImporter.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/MotionPhotoImporter.kt) supporting Samsung (`MotionPhoto_Data` / `SEFH` / `SEFT`), Google (`Container:Directory` / `MicroVideoOffset` / EOI->`ftyp`), Apple Live Photo pairs, and standalone MP4/MOV clips, safely falling back to `StillOnly` for plain static JPEGs. Fixed an edge case caught during unit testing where Samsung trailers with minimal `SEFT` without `SEFH` failed to trim trailing bytes cleanly.
- **Dedicated Golf Viewfinder UI & Darkroom Single-Item Playback**:
  - Created [`GolfMotionUi.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/GolfMotionUi.kt) and wired [`ViewfinderScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ViewfinderScreen.kt) exclusively for `disp_classic` (`GolfTopToolbar`, `GolfViewfinderOverlay`, `GolfModeAndSettingsStrip`, expandable `CameraWheelSelector`, `GolfBottomControlRow`, `GolfMotionSettingsDrawer`).
  - Paired single logical MediaStore items via [`GolfMotionStore.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionStore.kt) + [`GalleryCache.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryCache.kt), and created [`GolfMotionGalleryPlayer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GolfMotionGalleryPlayer.kt) wired into [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt) (still cover default, `MOTION` badge, tap/press/toggle looping playback, hairline progress bar, cover frame scrubber, `FileProvider` MP4/JPEG share, Movies MP4 / Still frame / GIF89a export).

### 2. Verification, Builds & Hardware Status
- **Unit Tests**: Added [`GolfMotionPhotoTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/motion/GolfMotionPhotoTest.kt) (7 tests). `.\gradlew.bat :app:testDebugUnitTest --rerun --offline` -> **188 tests total, 188 passed, 0 failed, 0 errors, 0 skipped** (`BUILD SUCCESSFUL`).
- **APK Builds**: `assembleDebug` (`172,224,315` B) and `assembleRelease` (`161,154,322` B, `lintVitalRelease` passed) -> **`BUILD SUCCESSFUL`**.
- **Physical Hardware (Samsung Galaxy S21 5G)**: **NOT VERIFIED** (`adb devices -l` empty — live MediaCodec encode, AudioRecord mic capture, Samsung/Google physical Motion Photo gallery import, frame-time stability, and playback smoothness require connected device testing).
- **Detailed Report**: [GOLF_MOTION_PHOTO_IMPLEMENTATION_REPORT.md](file:///c:/Users/User/Downloads/DazzProject/docs/GOLF_MOTION_PHOTO_IMPLEMENTATION_REPORT.md).
- **Next Priorities**: Connect Galaxy S21 via ADB, run `adb install -r app\build\outputs\apk\debug\app-debug.apk`, test live Golf Still + 1s/2s/3s Motion captures across `Vintage` and `Smooth`, verify MediaStore export/share flows, and capture benchmark logs.

---

## Entry 9 — Olympus Mju II (`analog_mju2`) Authentic 35mm Compact Film Camera Emulation

**Date**: November 27, 2026  
**Milestone**: Dedicated 1997 35mm f/2.8 Compact Film Camera (`MJU II`) + Calibrated 3D LUT + Flash-Aware Highlight Roll-Off

### 1. Summary of Changes
1. **Identity, Artwork & Preservation (`PresetCatalog.kt`, `CameraProfileCatalog.kt`)**:
   - Added `analog_mju2` (**"MJU II"**, *"1997 Compact Film"*, `CameraCategory.ANALOG_FILM`, `1997`, `35mm`, `AspectRatioMode.RATIO_3_2`, excluding fisheye and `d3dProfile`) as the 34th preset without modifying any existing camera preset or existing LUT file.
   - Converted user-supplied wordmark-free champagne clamshell pixel-art icon with nearest-neighbor scaling into `192x192` transparent [`cam_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/cam_mju2.png) (`3,364` B) and generated preview sample [`sample_analog_mju2.jpg`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/sample_analog_mju2.jpg) (`57,667` B).
2. **Independent 3D LUT & Film Stock Routing (`calibrate_mju_ii.py`, `generate_all_luts.py`, `CameraProfileCompatibility.kt`, `SkinToneProfile.kt`)**:
   - Generated [`lut_mju2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_mju2.png) (`512x512` Hald-8 `SRGB`, `103,288` B) with neutral gray axis `max_gray_channel_delta_norm = 0.0`, strict Hald trilinear monotonicity across all 256 steps (verified via near-neutral blend in cube corners), natural OKLCh skin preservation (`|Δh| <= 2.37°`, zero red clipping), realistic foliage/sky/red separation, and neutral shader/preset multipliers so zero contrast/saturation double-stacking occurs.
   - Mapped `C400` default to 100% `lut_mju2.png`, `EG200`/`FXN2`/`03G` to secondary LUT blend (`0.65f`), and `22M` to monochrome `lut_profile_22m.png`. Added `SkinToneProfileCatalog.MJU_II` (`skin_mju2`).
3. **Optics, Procedural Grain & True Flash-Aware Pipeline (`ShaderConstants.kt`, `GLViewfinderRenderer.kt`, `CameraManager.kt`, `ImageProcessor.kt`, `CollectionScreen.kt`, `ColorTuningSheet.kt`, `GalleryScreen.kt`)**:
   - Configured crisp center / mild outer softness (`0.030f`), gentle vignette (`0.16f @ 0.88f`), restrained CA (`0.0012f`), subtle barrel (`-0.006f`), high-threshold halation (`0.88f @ 0.14f`), procedural monochrome silver-halide grain (`GrainMode.PROCEDURAL`, `0.12f @ 0.85f`, zero scanned plates), clean borderless defaults on switching into `analog_mju2`, optional `'97` date stamp pill, and hardware `FLASH_STATE` / JPEG EXIF `TAG_FLASH` confirmation triggering highlight shoulder (`0.89f`) & skin highlight knee (`0.84f`) only when flash fired.

### 2. Verification, Builds & Hardware Status
- **Python Calibration & LUT Registry**: `python tools/mju_ii/calibrate_mju_ii.py` passed (`neutral_gray_monotonic = true`, `max_gray_channel_delta_norm = 0.0`); `generate_all_luts.ALL_GRADES['lut_mju2.png']` callable verified `True`.
- **Offline Unit Tests**: Added [`MjuIIEmulationTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/camera/profile/MjuIIEmulationTest.kt) (5 tests including real PNG Hald decoding/trilinear verification) and updated preset counts (`33 -> 34`). `.\gradlew.bat :app:testDebugUnitTest assembleDebug --rerun --offline` -> **193 tests total across 32 suites, 193 passed, 0 failed, 0 errors, 0 skipped** (`BUILD SUCCESSFUL`).
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (`172,224,532` bytes).
- **Physical Hardware (Samsung Galaxy S21 5G)**: **NOT VERIFIED** (`adb devices -l` empty — live Camera2 `FLASH_STATE_FIRED`, AF/AE speed, GL viewfinder parity, and outdoor/portrait flash captures require physical hardware testing).
- **Detailed Docs**: [OLYMPUS_MJU_II_CAMERA_IMPLEMENTATION_REPORT.md](file:///c:/Users/User/Downloads/DazzProject/docs/OLYMPUS_MJU_II_CAMERA_IMPLEMENTATION_REPORT.md) and [MJU_II_COLOR_AND_OPTICAL_SPEC.md](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/mju_ii/MJU_II_COLOR_AND_OPTICAL_SPEC.md).

## November 27 — D FUNS original appearance and green-tint regression

**MEASURED RESULT** — Measured four supplied sRGB photographs. The hand patch was RGB (171,100,69), and the notebook patch had Lab a* +2.80. Historical and working LUTs differ on identical inputs. No creative asset was edited during the audit.

**OBSERVED FACT** — Fixed the missing per-device sensor calibration in manual WB, separated the film tint slider from hardware gains and corrected the diagnostic treatment of stored temperature offsets. Added a non-diagonal colour regression, LUT/grain integrity checks and an opt-in same-source render comparison.

**NOT VERIFIED** — The S21 was absent from ADB. Current end-to-end appearance, flash and preview/export parity remain open. [Audit and evidence](DFUNS_ORIGINAL_APPEARANCE_AND_GREEN_TINT_REGRESSION_REPORT.md)

**MEASURED RESULT** — All 195 unit tests passed in each build variant. All three host film-model tests passed.


---

## Entry 10 — Thermal Vision (`thermal_vision` / `THERMAL`) Experimental IR Camera

**Date**: November 27, 2026  
**Milestone**: Simulated Thermal Imaging Camera (`THERMAL`) across Live Preview, Photo Capture, Video Recording & Media Import

### 1. Summary of Changes
1. **Preset Identity, Artwork & Strict Preservation (`PresetCatalog.kt`, `CameraProfileCatalog.kt`, `CameraProfileCompatibility.kt`, `AppSettings.kt`)**:
   - Added `CameraCategory.EXPERIMENTAL` ([`CameraPreset.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/model/CameraPreset.kt)) and registered `"thermal_vision"` (**"THERMAL"**, *"Simulated IR Vision"*, `cam_thermal`) as the 35th camera preset without modifying any existing preset, LUT, grain asset, or Golf / MJU II workflow.
   - Mapped `thermal_vision` in [`CameraProfileCompatibility.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/profile/CameraProfileCompatibility.kt) with `isSupported = false`, `supportsVideo = true`, `supportsStill = true`, and empty LUT/grain asset paths in [`ShaderConstants.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt).
   - Generated pixel-art icon [`cam_thermal.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/cam_thermal.png) (`192×192`, `1,619` B), calibration metrics [`thermal_calibration_metrics.json`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/thermal/thermal_calibration_metrics.json), palette ramp, and 5 preview sample JPEGs (`sample_thermal_*.jpg`).
2. **Smooth Palettes, GPU Shaders & Subject Guidance (`com.craftcam.app.thermal.*`, `GlThermalMaskExtractor.kt`, `ThermalGlHelper.kt`, `ShaderConstants.kt`)**:
   - Implemented 5 smooth Catmull-Rom false-color palettes (`Rainbow Thermal`, `Ironbow`, `Red Hot`, `White Hot`, `Black Hot`) in [`ThermalPaletteEngine.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalPaletteEngine.kt) (`256×5` `GL_LINEAR` texture; measured max per-step delta `0.00483`–`0.02095`).
   - Added bounded ~10 Hz `128×96` offscreen mask extraction (`GlThermalMaskExtractor.kt` with `AtomicBoolean` backpressure) feeding [`ThermalSubjectEstimator.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalSubjectEstimator.kt) (Camera2 face boxes + YCbCr skin/clothing concordance + P05–P96 adaptive bounds + EMA temporal smoothing) and clean GPU-only luminance/chroma fallback.
   - Wired unified thermal shaders into live viewfinder ([`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt)), live video recording ([`GPUVideoProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GPUVideoProcessor.kt), unlocking `CaptureMode.VIDEO` solely for `thermal_vision` in [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt)), still processing ([`ThermalVisionProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalVisionProcessor.kt) + [`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt)), and imported MP4/MOV video transcoding ([`ThermalVideoTranscoder.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/thermal/ThermalVideoTranscoder.kt)).
3. **Dedicated Thermal HUD & Darkroom Integration (`ThermalVisionUi.kt`, `ViewfinderScreen.kt`, `GalleryScreen.kt`, `CollectionScreen.kt`)**:
   - Built [`ThermalVisionUi.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ThermalVisionUi.kt) and integrated it into [`ViewfinderScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ViewfinderScreen.kt) with 5-palette switcher, `HOT/COLD` vertical gradient scale, `INVERT`, sensitivity & contrast sliders, `PHOTO | VIDEO` mode switcher, `IMPORT` picker (`ImageAndVideo`), Effects/HUD drawer (microbolometer softness, electronic noise, adaptive contrast, AI guidance, monochrome HUD, crosshair), and front/rear camera toggle. Always displays simulated status (`SIMULATED IR`); zero fabricated `°C`/`°F` values.
   - Mapped `"THERMAL"` outputs in [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt) and registered all 5 samples in [`CollectionScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/collection/CollectionScreen.kt).

### 2. Verification, Builds & Hardware Status
- **MEASURED RESULT — Unit Tests**: Added [`ThermalVisionTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/thermal/ThermalVisionTest.kt) (5 tests) and updated preset counts (`34 -> 35`). `.\gradlew.bat :app:testDebugUnitTest assembleDebug --offline` -> **32 suites, 200 tests, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESSFUL`).
- **MEASURED RESULT — Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (`172,225,031` bytes).
- **NOT VERIFIED — Physical Android Device**: `adb devices -l` empty; on-device live OpenGL shader execution, `VideoCapture` / `ThermalVideoTranscoder` encoding, `ImageReader` mask frame rate, and physical portrait thermal behavior remain **NOT VERIFIED** pending connected hardware testing.
- **Detailed Report**: [THERMAL_VISION_CAMERA_IMPLEMENTATION_REPORT.md](file:///c:/Users/User/Downloads/DazzProject/docs/THERMAL_VISION_CAMERA_IMPLEMENTATION_REPORT.md).

---

## Entry 11 — Creative Temperature Filter (`-100` Frozen Blue to `+100` Deep Red)

**Date**: November 27, 2026  
**Milestone**: Independent Luminance-Aware Creative Temperature Color Filter across Viewfinder, Still, Video, Golf Motion & Darkroom

### 1. Summary of Changes
1. **Color Science & Hardware WB Independence ([`CreativeTemperatureEngine.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/color/CreativeTemperatureEngine.kt), [`CameraState.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraState.kt), [`AppSettings.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/config/AppSettings.kt), [`CameraManager.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/camera/CameraManager.kt))**:
   - Added `creativeTemperature: Float = 0.0f` (`-100..+100`) and `creativeTempSkinProtection: Boolean = true` (`KEY_CREATIVE_TEMP_SKIN_PROTECT`) strictly decoupled from `kelvin`, `UserTuningConfig.temperatureOffset`, and `Camera2 COLOR_CORRECTION_GAINS`.
   - Implemented luminance-aware 5-stage non-linear transformation with exact `|T| < 0.05` identity pass-through, shadow black-point anchor, highlight headroom preservation, soft-knee gamut compression above `0.84`, partial Rec.709 luminance preservation, deep-red density refinement above `+65`, and optional continuous portrait skin protection.
   - Enforced monochrome (`analog_dblack`) and `22M` color-profile lock (`0.0f`) via `CameraProfileCompatibility.resolveLutPlan` plus automatic reset to `0.0f` when switching cameras.
2. **Unified GPU & CPU Pipeline Parity ([`ShaderConstants.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt), [`GLViewfinderRenderer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GLViewfinderRenderer.kt), [`GPUVideoProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/gl/GPUVideoProcessor.kt), [`GolfMotionEncoder.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionEncoder.kt), [`ImageProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt), [`NaturalPhotoProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/processor/NaturalPhotoProcessor.kt), [`GolfMotionProcessor.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/motion/GolfMotionProcessor.kt))**:
   - Inserted GLSL **Stage 9b** (`uCreativeTemperature`, `uCreativeTempSkinProtect`, `applyCreativeTemperature`) after Film Fade and prior to Halation and Halide Grain in both `FRAGMENT_SHADER_VIEWFINDER` and `FRAGMENT_SHADER_STILL`.
   - Routed Creative Temperature consistently across GL viewfinder, standard GL video capture, Golf motion capture/import, CPU still development, Gallery import, and Gallery negative redevelopment without touching existing LUTs, grain, or optics.
3. **Pixel-Art UI & Calibration Verification ([`CreativeTemperatureControl.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/CreativeTemperatureControl.kt), [`CameraExposureTemperatureControls.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/CameraExposureTemperatureControls.kt), [`ColorTuningSheet.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/components/ColorTuningSheet.kt), [`CameraInfoPills.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/CameraInfoPills.kt), [`ViewfinderScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/viewfinder/ViewfinderScreen.kt), [`GalleryScreen.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GalleryScreen.kt), [`GolfMotionGalleryPlayer.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/java/com/craftcam/app/ui/gallery/GolfMotionGalleryPlayer.kt))**:
   - Generated pixel-art drawables [`ic_temp_snowflake.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/ic_temp_snowflake.png) (`72x72`, `534` B) and [`ic_temp_flame.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/res/drawable/ic_temp_flame.png) (`72x72`, `442` B) and calibration metrics/contact sheets in [`tools/creative_temp/generate_and_verify.py`](file:///c:/Users/User/Downloads/DazzProject/tools/creative_temp/generate_and_verify.py) & [`docs/calibration/creative_temperature/`](file:///c:/Users/User/Downloads/DazzProject/docs/calibration/creative_temperature/).
   - Built custom gradient slider with center magnetic detent, haptic tick, numerical readout + semantic label, Skin Guard toggle, one-tap reset, monochrome lock indicator, `FILTER / WB` quick-panel mode switch, Darkroom import/redevelop integration, and Golf Motion Import compact controls.

### 2. Verification, Builds & Hardware Status
- **MEASURED RESULT — Calibration Swatches**: `creative_temperature_metrics.json` verified `T = 0` exact identity (`mid_gray=[128,128,128]`), `max_single_step_delta_mid_gray = 0.020973` across 201 steps, `-100` `mid_gray=[27,116,235]`, and `+100` `mid_gray=[236,51,6]` with protected `skin_light=[235,155,84]` vs unprotected `[238,43,8]`.
- **MEASURED RESULT — Unit Tests & Build**: Added [`CreativeTemperatureTest.kt`](file:///c:/Users/User/Downloads/DazzProject/app/src/test/java/com/craftcam/app/color/CreativeTemperatureTest.kt) (`8` tests). `.\gradlew.bat :app:testDebugUnitTest assembleDebug --offline` -> **`33` suites, `208` tests, `0` failures, `0` errors, `0` skipped** (`BUILD SUCCESSFUL`), [`app-debug.apk`](file:///c:/Users/User/Downloads/DazzProject/app/build/outputs/apk/debug/app-debug.apk) = **`172,225,187` bytes**.
- **NOT VERIFIED — Physical Android Device**: `adb devices -l` empty; on-device live preview/capture/video shader execution remains **NOT VERIFIED**.
- **Detailed Report**: [CREATIVE_TEMPERATURE_FILTER_REPORT.md](file:///c:/Users/User/Downloads/DazzProject/docs/CREATIVE_TEMPERATURE_FILTER_REPORT.md).

## November 27 — D3D subject anchoring and depth parallax

**MEASURED RESULT** — Four original MP4s confirmed the 10 fps four-view bounce. Extreme-view bridge motion in the two off-centre portraits fell from 33.51 to 2.29 px and from 14.11 to 0.01 px in annotated geometry replays. The other two clips retained less background motion.

**OBSERVED FACT** — D3D now uses capture-aligned convergence metadata and an adaptive virtual baseline. The complete debug and release unit suites passed, with 210 tests each. **NOT VERIFIED** — No S21 was connected for live capture. [Correction report](D3D_STEREOSCOPIC_PARALLAX_CORRECTION_REPORT.md) · [Comparison video](calibration/d3d/comparison.mp4)
