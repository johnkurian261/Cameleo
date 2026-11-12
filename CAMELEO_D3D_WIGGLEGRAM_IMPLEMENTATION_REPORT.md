# CAMELEO / dazzz — Authentic D3D Multi-Frame Wigglegram Implementation Report

## 1. Executive Summary

This report documents the architectural overhaul of the **D3D camera** in CAMELEO (dazzz).
Previously, the D3D preset attempted to simulate 3D through real-time screen-space UV displacement in the live preview shader and single-frame static warping. This produced a pronounced, artificial horizontal rubber-stretching effect across the entire viewfinder.

Following evidence-based analysis of classic 3D quad-lens film cameras (Nishika N8000, Nimslo 3D, RETO 3D) and the authentic Dazz Cam D3D architecture, the D3D implementation has been restructured into an authentic **Multi-Frame Stereoscopic Wigglegram** pipeline:
- **Live Preview**: 100% stable, zero UV stretching/deformation, authentic D3D 35mm film color science and grain.
- **Capture Execution**: Quad-viewpoint stereoscopic parallax synthesis (`ThreeDPhotoProcessor`) producing a smooth oscillating 6-frame bounce sequence (`Lens 1 -> 2 -> 3 -> 4 -> 3 -> 2`) at 10 fps.
- **Output**: Hardware-encoded H.264 MP4 looping video saved directly to the device gallery with instant prewarmed thumbnail caching.

---

## 2. Subsystem Classification & Compliance Matrix

| Subsystem / Capability | Status | Implementation Details |
|---|---|---|
| **Viewfinder Stability (Zero Stretching)** | **IMPLEMENTED** | Removed live-preview UV displacement blocks from `ShaderConstants.kt` (viewfinder & video shaders). |
| **D3D Color Science & Emulsion** | **IMPLEMENTED** | Real D3D LUT (`lut_d3d.png`) applied during live preview and capture framing. |
| **D3D Film Grain Integration** | **IMPLEMENTED** | Cycles authentic scanned 35mm Kodak film grains (`grain_d3d1.jpg`, `grain_d3d2.jpg`, `grain_d3d3.jpg`). |
| **Monocular Depth & Disparity Mapping** | **IMPLEMENTED** | `ThreeDDepthEngine` with Sobel DfD sharpness, face anchor detection, and dynamic convergence pivot. |
| **Quad-Lens Parallax Synthesis** | **IMPLEMENTED** | `ParallaxWarpEngine` sub-pixel backward warping (4 viewpoints: -1.0, -0.333, +0.333, +1.0). |
| **Oscillating Bounce Sequence** | **IMPLEMENTED** | 6-frame cadence `[0, 1, 2, 3, 2, 1]` with smooth cyclic continuity. |
| **Hardware Video Encoding** | **IMPLEMENTED** | `ThreeDVideoEncoder` using native Android `MediaCodec` (H.264/AVC, 10 fps, 4 loops). |
| **Gallery & MediaStore Integration** | **IMPLEMENTED** | Saves to `DCIM/Cameleo` with `isVideo = true` and instant 256px thumbnail. |
| **Configurable Parallax Strength** | **IMPLEMENTED** | `d3dParallaxStrength = 0.012f` (conservative default, adjustable via `tuning.d3dStrengthOffset`). |
| **Hardware Dual-Camera Stereo Disparity** | **DEVICE DEPENDENT** | Software depth estimation fallback used on standard Camera2 devices without multi-camera API access. |

---

## 3. Analysis of the Previous Problem (Why It Felt Like Stretching)

### 3.1 The Live Shader Deformation
In the previous implementation, the OpenGL ES fragment shaders (`ShaderConstants.kt`) executed a real-time depth prior calculation:
```glsl
float depthPrior = clamp((1.0 - yNorm) * 0.65 + (1.0 - centerDist * 1.3) * 0.35, 0.0, 1.0);
float delta = (depthPrior - pivot) * uD3DStrength;
float parallaxOffset = delta * uD3DParallax * 0.5;
vec2 uvL = clamp(uv + vec2(-parallaxOffset + caOffset, 0.0), ...);
vec2 uvR = clamp(uv + vec2(parallaxOffset - caOffset, 0.0), ...);
```
Because `depthPrior` was a naive linear vertical gradient, the top and bottom of every frame were pulled horizontally in opposite directions by up to 24 pixels on every frame. This created a jarring rubber-stretching / funhouse-mirror artifact in the viewfinder.

### 3.2 Single-Frame Parallax Baking
When capturing a photo, `ImageProcessor.processAndSaveImage()` routed through `D3DPhotoFilter.apply()`, baking horizontal pixel displacement directly into a single 2D JPEG. A single 2D image cannot convey true motion parallax; the displaced pixels simply appeared as horizontal blur and smear.

---

## 4. The New Architecture: Authentic Multi-Frame Wigglegram

### 4.1 Live Viewfinder Pipeline
```text
Camera2 / CameraX Preview
          ↓
OpenGL ES 3.0 Texture Sampling (oesUV)
          ↓
[Clean Preview - Zero UV Distortion]
          ↓
D3D 3D LUT Color Grading (lut_d3d.png)
          ↓
Scanned 35mm Film Grain (grain_d3d1.jpg)
          ↓
Clean, Stable 4:3 Viewfinder
```

### 4.2 Capture & Wigglegram Pipeline
```text
User Taps Shutter (D3D Active)
          ↓
High-Resolution Hardware Sensor Capture (ImageCapture)
          ↓
Orientation Normalization & 4:3 Crop
          ↓
D3D Film Grading & Optical Emulsion (applyPresetEffects)
          ↓
ThreeDPhotoProcessor.processAndSaveThreeDPhoto()
          ├── 1. Monocular Depth Estimation & Disparity Map (ThreeDDepthEngine)
          │      • Sobel defocus sharpness energy
          │      • Hardware face anchor detection
          │      • Center-weighted radial prior
          │      • Dynamic convergence pivot calculation (pivotDisparity = 0.45f)
          │
          ├── 2. Quad-Lens Stereoscopic Warping (ParallaxWarpEngine)
          │      • 4 physical viewpoints: [-1.0f, -0.333f, +0.333f, +1.0f]
          │      • Conservative parallax baseline (d3dParallaxStrength = 0.012f)
          │      • Sub-pixel backward warping (target Z-buffering, 0 holes, 0 comb artifacts)
          │      • 6-frame bounce sequence: [Lens 1, Lens 2, Lens 3, Lens 4, Lens 3, Lens 2]
          │
          ├── 3. Retro Analog Emulsion (RetroFrameRenderer)
          │      • Authentic grain cycling (grain_d3d1, grain_d3d2, grain_d3d3)
          │      • Sub-pixel lateral chromatic dispersion (rgbSeparationPx = 0.5px)
          │      • Film registration gate tolerance (mechanicalJitterPx = 0.25px)
          │      • Leaf shutter exposure variance (exposureVariance = 0.008f)
          │
          └── 4. Hardware H.264 Video Encoding (ThreeDVideoEncoder)
                 • Native Android MediaCodec
                 • 10 fps playback rate (snappy 4-lens pop)
                 • 4 complete bounce loops (~2.4s seamless loop)
          ↓
MediaStore Video Gallery Persistence (DCIM/Cameleo) + Instant Prewarmed Thumbnail
```

---

## 5. Parameter Calibration & Comparison

| Parameter | Previous Value | New Calibrated Value | Impact / Rationale |
|---|---|---|---|
| **Live Viewfinder UV Warping** | Enabled (24px displacement) | **Disabled (0px)** | Viewfinder is 100% stable; framing is clean and precise. |
| **Output Type** | Static JPEG | **Looping H.264 MP4** | Delivers true stereoscopic motion parallax (wigglegram). |
| **`d3dParallaxStrength`** | 0.015f | **0.012f** | Conservative, subtle depth shift without tearing. |
| **`frameCount`** | 1 (static) | **4 lenses (6 bounce frames)** | Matches Nishika N8000 quad-lens stereoscopic cadence. |
| **`fps`** | N/A | **10 fps** | Authentic vintage stereoscopic playback rhythm. |
| **`loopCount`** | N/A | **4 cycles** | Smooth ~2.4 second looping presentation. |
| **`grainIntensity`** | 0.22f | **0.18f** | Subtle organic film grain texture without noise clutter. |

---

## 6. Verification & Test Execution

### 6.1 Unit Tests
All unit tests in `ThreeDModeTest` and `CaptureMenuAndSettingsAuditTest` pass:
- `testCaptureModeIncludes3D`: PASSED
- `testThreeDConfigDefaults`: PASSED
- `testD3DAnimationConfigurationAndBounds`: PASSED
- `testD3DPresetRouting`: PASSED
- `testDisparityMapBilinearSampling`: PASSED
- `testQuadLensBounceCadenceAndOffsets`: PASSED
- `testDepthDisplacementMonotonicityAndPivotPinning`: PASSED

### 6.2 Device Verification (Samsung Galaxy S21 5G)
- **Preview Stability**: Verified clean, tack-sharp viewfinder without any horizontal stretching or vertical gradient distortion.
- **Capture Latency**: Shutter triggers immediately; background processing updates progress indicators ("Capturing 3D...", "Developing 3D Wigglegram...").
- **Memory Safety**: Working bitmaps bounded to 1080px during 3D synthesis to prevent OutOfMemoryError on Android 14.
- **Gallery Playback**: Saved MP4 plays seamlessly with 10 fps stereoscopic oscillation and video badge indicator.

