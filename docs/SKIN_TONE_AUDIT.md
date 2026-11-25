# CAMELEO Retro Camera Application — Skin Tone Intelligence Audit Report

## 1. Executive Summary

An exhaustive audit of the CAMELEO (`DazzProject`) codebase was conducted, covering Kotlin source files, Java interfaces, GLSL shaders, camera profile configurations, LUT assets, and neural model files.

The audit investigated whether the application possesses:
1. Implicit skin-colour response
2. Colour-based skin detection
3. Face detection
4. Semantic skin segmentation
5. Skin-tone protection
6. Camera-specific skin rendering

### Summary of Findings
| Capability | Implementation Status | Active in Pipeline? | Primary Source File(s) |
|---|---|---|---|
| **A. Implicit skin-colour response** | **Partially Implemented** | **Yes** | `ShaderConstants.kt` (L510-528), `ImageProcessor.kt` (L915-934), `DigicamColorEngine.kt` (L286-295) |
| **B. Colour-based skin detection** | **Partially Implemented / Fragmented** | **Partial (Natural mode only)** | `NaturalPhotoProcessor.kt` (L350-362), `SkinToneProcessor.kt` (L53-65, unused), `ColorScienceEngine.kt` (L234-235, unused), `SkinRegionAnalyzer.kt` (L54-68, unused) |
| **C. Face detection** | **Fully Implemented** | **Yes** | `HardwareFaceDetector.kt` (L22-101), `ImageFaceDetectorFallback.kt` (L107-166), `CameraManager.kt` (L848) |
| **D. Semantic skin segmentation** | **Missing** | **No** | No deep learning or body-part parsing model for exposed human skin is present |
| **E. Skin-tone protection** | **Partially / Incorrectly Implemented** | **Partial (Natural only) / Global Vibrance in Film** | `NaturalPhotoProcessor.kt` (L363-372), `ImageProcessor.kt` (L930, global vibrance, not skin-selective) |
| **F. Camera-specific skin rendering** | **Missing as a dedicated skin engine** | **No (Global only)** | No independent skin profile exists per camera (`D_FUNS`, `CPM35`, `CCD`, `FXNR`) |

---

## 2. Detailed Audit by Capability Category

### A. Implicit Skin-Colour Response
- **Status**: Partially Implemented (Active).
- **Locations**:
  - `app/src/main/java/com/craftcam/app/gl/shaders/ShaderConstants.kt` (lines 510–528, 1166–1184)
  - `app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt` (lines 915–934)
  - `app/src/main/java/com/craftcam/app/color/digicam/DigicamColorEngine.kt` (lines 286–295)
- **Mechanisms**:
  1. **Global Vibrance Blending**: In the film rendering pass, `skinProtection` (0.0 to 1.0) is configured on `FilmProfile`. When saturation is adjusted, it blends between linear saturation and a 3×3 vibrance matrix (`lr=0.2126, lg=0.7152, lb=0.0722`). However, this is applied **uniformly across all pixels in the frame**; it does not selectively identify or protect skin.
  2. **Asymptotic Highlight Shoulder**: In `NaturalPhotoProcessor.kt` (lines 309–328) and `HighlightRollOff.kt`, a continuous max-channel rational shoulder prevents highlight channels from clipping abruptly, preserving chromaticity ratios in bright skin highlights.
  3. **Saturated Red Protection**: In `DigicamColorEngine.kt` (lines 286–295), pixels where $R > 1.25 G \land R > 1.25 B$ receive red saturation protection, preventing scarlet tones from burning into orange.
  4. **3D Camera LUTs**: Assets like `lut_ccd_r.png`, `lut_disp_flash.png`, and `lut_dfuns_v1.png` encode implicit tonal responses for skin, but are static 3D transforms without scene or skin intelligence.

---

### B. Colour-Based Skin Detection
- **Status**: Partially Implemented / Fragmented.
- **Locations**:
  - `app/src/main/java/com/craftcam/app/processor/NaturalPhotoProcessor.kt` (lines 350–362) — **Active only in Zero camera**
  - `app/src/main/java/com/craftcam/app/color/SkinToneProcessor.kt` (lines 53–65) — **Disconnected / Unused**
  - `app/src/main/java/com/craftcam/app/color/ColorScienceEngine.kt` (lines 234–235) — **Disconnected / Unused**
  - `app/src/main/java/com/craftcam/app/portrait/SkinRegionAnalyzer.kt` (lines 54–68) — **Disconnected / Unused**
  - `app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt` (lines 1063–1066, 1147–1150) — **Grain attenuation heuristic**
- **Analysis of Methods**:
  1. **NaturalPhotoProcessor HSV Box**:
     - Tests whether $hue \in [2^\circ, 50^\circ]$ (around center $26.0^\circ$), $sat \in [0.10, 0.70]$, and $val \in [0.15, 0.92]$.
     - Computes soft weighting using $\cos(\cdot)$ and $\text{smoothstep}$.
     - **Weaknesses**: Active ONLY when `engineMode == CameraEngineMode.NATURAL` ("Zero" camera). For all film/retro presets (`CPM35`, `CCD`, `D FUNS`, `FXNR`, `VSCO`, etc.), this code is never executed. Furthermore, under tungsten lighting ($B/R \le 0.35$), large background areas (wood, walls, food) enter this HSV range, resulting in false positives.
  2. **ColorScienceEngine YCbCr Bounding Box**:
     - Defines $Cr \in [0.52, 0.70]$, $Cb \in [0.35, 0.48]$, and $Y \in [0.10, 0.92]$.
     - **Weaknesses**: Completely unused in the application. Neither `CameraManager` nor `ImageProcessor` instantiates or calls `ColorScienceEngine`.
  3. **SkinRegionAnalyzer Normalized Chromaticity**:
     - Computes $r_{\text{norm}} = R / (R+G+B)$ and $g_{\text{norm}} = G / (R+G+B)$.
     - Requires $r_{\text{norm}} \in [0.36, 0.58]$ and $g_{\text{norm}} \in [0.26, 0.38]$ with $R - G \in [10, 90]$.
     - **Weaknesses**: Disconnected / never called in the capture or processing pipeline.
  4. **ImageProcessor Grain Heuristic**:
     - Simple threshold: $R > G \land G > B \land R \in [0.25, 0.90]$. Attenuates film grain by up to 30%. Does not perform colour or exposure protection.

---

### C. Face Detection
- **Status**: Fully Implemented (Active).
- **Locations**:
  - `app/src/main/java/com/craftcam/app/portrait/FaceDetector.kt` (lines 22–101, `HardwareFaceDetector`)
  - `app/src/main/java/com/craftcam/app/portrait/FaceDetector.kt` (lines 107–166, `ImageFaceDetectorFallback`)
  - `app/src/main/java/com/craftcam/app/camera/CameraManager.kt` (lines 810–832, 848–849, 1657)
- **Mechanisms**:
  - `HardwareFaceDetector` enables `CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE` on Camera2 sessions.
  - Ingests `CaptureResult.STATISTICS_FACES` directly from the camera sensor ISP at 60 FPS with zero CPU overhead.
  - Normalizes bounds, rotation, eye positions, and mouth positions into `FaceRegion`.
  - Fallback `ImageFaceDetectorFallback` uses `android.media.FaceDetector` on an RGB_565 downsampled bitmap when Camera2 metadata is absent.
  - `CameraManager` captures `captureState.detectedFaces` and passes them to `ImageProcessor.processAndSaveImage(...)`.
- **Limitation**: Face detection alone is not skin segmentation. It identifies bounding boxes around facial features, but completely misses necks, shoulders, exposed arms, hands, and legs.

---

### D. Semantic Skin Segmentation
- **Status**: Missing.
- **Locations**: None.
- **Analysis**:
  - `app/src/main/assets/models/` contains only `depth_anything_v2_small.onnx` (monocular depth estimation).
  - No dedicated ML Kit, MediaPipe, LiteRT, TFLite, or ONNX model for human skin or body-part parsing is present.
  - `SubjectSegmenter.kt` (lines 43–275) segments the overall human figure (including clothing and hair) for portrait depth of field, but does not segment exposed skin.

---

### E. Skin-Tone Protection
- **Status**: Partially / Incorrectly Implemented.
- **Locations**:
  - `app/src/main/java/com/craftcam/app/processor/NaturalPhotoProcessor.kt` (lines 363–372)
  - `app/src/main/java/com/craftcam/app/color/SkinToneProcessor.kt` (lines 74–93, unused)
  - `app/src/main/java/com/craftcam/app/processor/ImageProcessor.kt` (lines 915–935)
- **Identified Deficiencies**:
  1. In `NaturalPhotoProcessor.kt`, skin saturation is capped to 0.52 and red-shifted skin is steered toward a fixed peach hue ($26.0^\circ$):
     `hue = hue * (1f - skinWeight * 0.35f) + targetSkinHue * (skinWeight * 0.35f)`
     **Problem**: This homogenizes diverse natural complexions toward a single target hue, which violates photographic authenticity and racial diversity principles.
  2. In `ImageProcessor.kt` (film pipeline), the variable `skinProtection` only modulates a global vibrance matrix. It does not protect skin from being tinted green by cross-processing LUTs, blown out by high-contrast tone curves, or crushed in deep shadows.
  3. The dedicated `SkinToneProcessor` and `ColorScienceEngine` implementations are completely unreferenced in the production build.

---

### F. Camera-Specific Skin Rendering
- **Status**: Missing as a dedicated skin engine.
- **Analysis**:
  - No per-preset skin tone calibration exists.
  - `D_FUNS` (disposable film), `CPM35` (amber digital compact), `CCD` (Y2K pocket digicam), and `FXNR` (35mm negative) do not have camera-specific skin response profiles.
  - Any skin appearance in these modes is currently a byproduct of the global color grade, LUT, or global illuminant adaptation.

---

## 3. Detailed Failure Modes of Existing Logic

| Scenario | Existing Behavior | Failure Mode |
|---|---|---|
| **Amber Tungsten (Refs 1, 3, 6)** | Overall scene has $B/R \le 0.35$. In `NaturalPhotoProcessor`, background wood and yellow walls enter $hue \in [2^\circ, 50^\circ]$. | False positives: wooden tables, furniture, and walls are treated as skin, causing their saturation to be clamped and hue steered. |
| **Yellow-Green Artificial (Refs 7, 8)** | Overall scene has $G/R \ge 0.82$. Skin chromaticity shifts toward olive/yellow-green. | False negatives: skin falls outside rigid RGB/YCbCr locus boxes and receives zero protection. |
| **Direct Flash Portrait (Ref 2)** | High specular reflection on cheeks and forehead ($V > 0.92$). | False negatives: specular highlights drop out of the skin mask, causing patchy, discontinuous rendering. |
| **Red Clothing / Red Dress (Ref 2)** | Highly saturated scarlet fabric ($a^*=67.1, R=239$). | False positives: boundaries of red fabric leak into skin protection or red clipping guard, desaturating vibrant fashion elements. |
| **Dark Atmospheric Backgrounds** | Deep shadows and dark skin tones ($Y < 0.20$). | In `ColorScienceEngine` and `SkinRegionAnalyzer`, dark thresholds ($Y < 0.10$ or $sum < 50$) clip deep Fitzpatrick V–VI complexions. |

---

## 4. Recommended Architectural Upgrades

To solve these deficiencies, we will implement a unified **Skin Tone Intelligence Engine** (`SkinToneIntelligenceEngine`):

1. **Layer 1: Camera Colour Science**:
   - Establish illuminant-aware reference coordinates so expected skin chromaticity adapts dynamically to daylight, tungsten, direct-flash, and yellow-green artificial lighting.
2. **Layer 2: Multi-Space Soft Colour Probability**:
   - Continuous Gaussian/ellipsoidal probability density function in normalized $r\text{-}g$ chromaticity and illuminant-adapted $YC_bC_r$ space.
   - Outputs continuous confidence $P_{\text{color}} \in [0.0, 1.0]$ without binary edge artifacts.
3. **Layer 3: Anatomical Spatial Prior & Confidence-Aware Fusion**:
   - Leverages `HardwareFaceDetector` at 60 FPS.
   - Projects human anatomy (face core, neck, collarbone, shoulders, arms, hands).
   - Suppresses background false positives (wood, walls, leather) by 80% when humans are present, while allowing graceful fallback when only arms/hands are visible.
4. **Layer 4: Camera-Specific Skin Profiles & Photographic Protection**:
   - Calibrated profiles for `D_FUNS`, `CPM35`, `CCD`, `FXNR`, `VSCO`, and `Natural`.
   - Protects against over-saturation, muddy shadows, green tint leakage, and channel blowout.
   - **Strict Aesthetic Rule**: Zero beauty filters, zero whitening, zero plastic smoothing, zero artificial relighting, and zero forced homogenization of skin complexions.
5. **Developer Diagnostics**:
   - Dedicated `SkinToneDiagnostics` stage exporter for developer inspection.
