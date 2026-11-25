# CAMELEO CPM35 & CCD — Phase 2 Architecture & Implementation Audit

## 1. Executive Summary

This document provides a rigorous, objective engineering audit of the Phase 1 CPM35 and CCD implementation in CAMELEO (`DazzProject`).

While Phase 1 established the functional framework—with 90 passing unit tests, camera profile differentiation, and successful debug APK compilation—this audit isolates the specific mathematical and architectural simplifications that compromise authentic digicam rendition in real-world scenes.

To maintain engineering discipline, every feature is explicitly classified into one of three verification tiers:
1. **Verified in Source Code**: Present and syntactically correct in Kotlin/GLSL.
2. **Integrated into Rendering**: Actively executed in the image processing path (`ImageProcessor.kt` / `CameraManager.kt`).
3. **Verified on Physical Hardware**: Confirmed through runtime logcat, hardware query, or captured visual evidence on a physical Android device (Samsung Galaxy S21 5G / Android 14).

---

## 2. Feature-by-Feature Verification & Classification

| Component / Feature | Implementation Location | Verification Tier | Operational Status | Technical Deficiencies & Failure Modes |
|---|---|---|---|---|
| **Illuminant Estimation** | `color/digicam/DigicamColorEngine.kt` (`estimateIlluminant`) | Integrated into Rendering | **Flawed Heuristic** | Evaluates global subsampled $B/R$ and $G/R$ ratios. Fails when scene contains colored objects: red dress (Ref 2) falsely triggers `AMBER_TUNGSTEN`; green foliage falsely triggers `YELLOW_GREEN_ARTIFICIAL`. |
| **Partial White Balance** | `color/digicam/DigicamColorEngine.kt` (`applyColorTransform`) | Integrated into Rendering | **Non-Physical** | Applies channel gain multipliers directly in non-linear sRGB gamma space. Lacks linear space chromatic adaptation, logarithmic gain interpolation, and awareness of phone ISP's prior white balance. |
| **Red Dominance Protection** | `color/digicam/DigicamColorEngine.kt` (lines 285–293) | Integrated into Rendering | **Hard-Threshold Artifacts** | Hard boundary `rf > gf * 1.25 && rf > bf * 1.25` with up to 1.35× linear boost. Causes warm skin, terracotta, and brown wood to skew unnaturally red or create visible boundary stepping. |
| **Tonal Curve & Shadow Tinting** | `color/digicam/DigicamColorEngine.kt` (`toneLut`) | Integrated into Rendering | **Verified in Code** | 256-entry S-curve with toe lift and shoulder rolloff is fast and functional, but currently operates on individual RGB channels instead of luminance/chrominance, causing slight saturation shifts on contrast changes. |
| **Optical Highlight Scatter** | `processor/digicam/DigicamOpticalEngine.kt` (`applyHighlightScatter`) | Integrated into Rendering | **Universal Color Cast** | Hard luminance threshold ($0.78$) triggers abrupt bloom. Composites a fixed golden-white tint (`[1.05, 0.98, 0.85]`) over all highlights, failing to preserve actual light-source chromaticity (e.g. cool daylight or fluorescent fixtures). |
| **Soft Digital Detail** | `processor/digicam/DigicamOpticalEngine.kt` (`applySoftDigitalDetail`) | Integrated into Rendering | **Verified in Code** | 3-tap cross blur + unsharp mask effectively reduces modern over-sharpening halos. Needs resolution-aware scaling. |
| **Electronic Sensor Noise** | `processor/digicam/DigicamSensorEngine.kt` (`applyElectronicNoise`) | Integrated into Rendering | **Simplistic Model** | Uses $\sigma = 0.35 + 0.65\sqrt{Y}$ in sRGB pixel space. Does not model Poisson-Gaussian noise variance in linear scene space ($\text{Var}(Y) = a \cdot Y + b$), nor does it subtract the noise already present in smartphone capture. |
| **Date Stamp Rendering** | `processor/DateStampRenderer.kt` | Integrated into Rendering | **Verified on Hardware** | Style A (`'8 22 '25` amber) and Style B (`2026/06/02 17:40:39` yellow) render accurately at correct proportional heights and positions. |
| **Minimally Processed Capture** | `camera/CameraManager.kt` (lines 965–991) | Integrated into Rendering | **Partial Hardware Parity** | Requests `CAPTURE_MODE_MINIMIZE_LATENCY`, `NOISE_REDUCTION_MODE_FAST`, and `EDGE_MODE_FAST`. Does not query `RAW_SENSOR`, does not verify `TONEMAP_MODE_CONTRAST_CURVE`, and does not disable vendor HDR extensions where present. |
| **Skin Tone Intelligence** | `color/skin/SkinToneIntelligenceEngine.kt` | Integrated into Rendering | **Fully Verified (Phase 1.5)** | 4-layer engine combines hardware face localization, soft probability estimation, anatomical priors, and profile-specific gains without beauty smoothing. Needs full coupling with the hybrid illuminant weights. |
| **Reference Analysis Toolkit** | `tools/digicam_calibration/analyze_references.py` | Verified in Source Code | **Limited Global Stats** | Computes only whole-image mean RGB and histogram percentiles. Lacks region-level extraction for skin, neutrals, red clothing, foliage, and specular highlights. |

---

## 3. Detailed Audit of Phase 1 Claims vs. Reality

### Claim 1: "Dynamic Illuminant Estimation accurately classifies the scene into four distinct lighting regimes."
- **Audit Finding: Partially True, but Highly Fragile.**
- The existing implementation computes:
  $$\text{blueRatio} = \frac{\text{avg}(B)}{\text{avg}(R)}, \quad \text{greenRatio} = \frac{\text{avg}(G)}{\text{avg}(R)}$$
- If a subject wears a bright scarlet red dress (as in Reference 2) under midday sunlight, the red fabric inflates `avg(R)` across the subsampled grid, driving `blueRatio` below $0.35$. The estimator incorrectly declares the scene `AMBER_TUNGSTEN` and applies a heavy $1.18\times$ red gain and $0.32\times$ blue cut!
- Similarly, a landscape with lush green foliage drives `greenRatio` above $0.82$, triggering false `YELLOW_GREEN_ARTIFICIAL` classification.
- **Remedy for Phase 2**: Build `HybridIlluminantEstimator` that excludes saturated pixels (chroma $> 0.25$), computes grey-world and shades-of-grey on non-saturated midtones, analyzes specular highlight chromaticity, reads Camera2 `COLOR_CORRECTION_GAINS`, and outputs continuous regime weights.

### Claim 2: "Partial White Balance retains 55%–85% of ambient warmth."
- **Audit Finding: Mathematically Inaccurate.**
- The existing code multiplies sRGB values by fixed multipliers (`gains = [1.18, 0.88, 0.32]`).
- This is not partial chromatic adaptation. In digital color science, white balance gains must be applied in a **linear color space** ($RGB_{linear}$).
- Furthermore, modern smartphones have already executed AWB on the JPEG stream. Applying another fixed multiplier blindly risks **double white balance correction** or over-tinting neutral surfaces.
- **Remedy for Phase 2**: Linearize RGB, calculate chromatic adaptation in linear space, and apply logarithmic gain interpolation:
  $$\text{partialGain}_c = \exp(s_c \cdot \ln(G_{\text{target}, c}))$$
  calibrated separately for daylight, tungsten, fluorescent, and flash.

### Claim 3: "Hue-dependent red dominance protection retains vivid scarlet."
- **Audit Finding: Hard-Threshold Artifacts.**
- The check `rf > gf * 1.25f && rf > bf * 1.25f` creates a step discontinuity.
- A pixel with $R=200, G=159, B=100$ gets 0% boost, while $R=200, G=158, B=100$ suddenly receives a $1.35\times$ boost.
- Warm skin tones ($R \approx 200, G \approx 140, B \approx 100$) occasionally cross this threshold and become hyper-saturated or sunburned.
- **Remedy for Phase 2**: Implement continuous hue-chroma-luminance response with smooth Gaussian/cosine angular falloff centered at scarlet ($H \approx 0^\circ$), smoothly attenuating to zero boost as hue transitions toward skin ($25^\circ$) or orange ($40^\circ$).

### Claim 4: "Physical electronic CCD sensor noise model replaces film grain."
- **Audit Finding: Simplistic Non-Physical Formula.**
- The formula $\sigma = 0.35 + 0.65\sqrt{Y}$ was evaluated directly in 8-bit non-linear gamma space.
- Sensor shot noise is Poisson distributed in linear electron/photon counts, while read noise is Gaussian in the analog signal domain.
- When applied on top of an already-processed smartphone JPEG, the app was adding noise on top of noise that had already undergone smartphone noise reduction.
- **Remedy for Phase 2**: Formalize the Poisson-Gaussian variance model $\text{Var}(Y) = a \cdot Y + b$ in linear space, subtract the estimated input noise floor, and apply spatially correlated chroma noise to emulate 2000s Bayer demosaicing artifacts.

### Claim 5: "Single-frame capture enforcement bypasses computational photography."
- **Audit Finding: Partial; Needs Hardware Query Validation.**
- Setting `CAPTURE_MODE_MINIMIZE_LATENCY` signals CameraX to prioritize speed over multi-frame HDR, but Samsung's OEM camera HAL can still invoke post-capture processing unless `CONTROL_SCENE_MODE_DISABLED` is set and `RAW_SENSOR` / `YUV_420_888` paths are inspected.
- **Remedy for Phase 2**: Build `DigicamCaptureController` that audits `CameraCharacteristics`, logs applied `CaptureResult` settings, and implements fallbacks.

---

## 4. Phase 2 Architectural Blueprint

To solve these deficiencies cleanly, Phase 2 implements:
1. **`HybridIlluminantEstimator.kt`**: Continuous lighting weights, Camera2 metadata, neutral-pixel extraction, highlight chromaticity, shades-of-grey, saturated color rejection.
2. **`DigicamColorEngine.kt` (v2)**: Linear color space pipeline, logarithmic partial white balance interpolation, smooth hue-chroma red protection, continuous lighting regime blending.
3. **`DigicamOpticalEngine.kt` (v2)**: Smooth soft-knee highlight diffusion, multiscale scatter, light-source color preservation, daylight restraint.
4. **`DigicamSensorEngine.kt` (v2)**: Linear Poisson-Gaussian noise model ($\text{Var}(Y) = aY + b$), differential noise injection, spatially correlated chroma texture.
5. **`DigicamCaptureController.kt`**: CameraCharacteristics audit, single-frame enforcement, CaptureResult verification.
6. **`DigicamInspector.kt`**: Developer diagnostic stage inspector and exporter.
7. **`tools/digicam_calibration/analyze_references.py` (v2)**: Region-level colorimetric extractor.
