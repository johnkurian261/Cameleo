# REA Investigation: Camera Architecture & Hardware Capture Pipeline

## 1. High-Level Camera Pipeline Architecture

In Dazz Cam, the camera system is designed around a dual-path pipeline:
1. **Low-Latency Live Viewfinder Path**: A high-speed, 30/60 FPS streaming pipeline that continuously processes video frames on the GPU for the viewfinder display.
2. **High-Resolution Photo Capture Path**: An asynchronous offline pipeline that captures full-sensor RAW or uncompressed YUV data from the hardware sensor, applies non-destructive multi-stage film simulation, and outputs high-fidelity JPEGs.

```
                    ┌─────────────────────────┐
                    │      Camera Sensor      │
                    └────────────┬────────────┘
                                 │
                     [AVFoundation / CameraX]
                                 │
         ┌───────────────────────┴───────────────────────┐
         │                                               │
         ▼ (Live Stream 30/60 fps)                       ▼ (Still Photo Capture)
┌─────────────────────────────────┐             ┌─────────────────────────────────┐
│     Live Viewfinder Pipeline    │             │   Full-Resolution Render Pipe   │
├─────────────────────────────────┤             ├─────────────────────────────────┤
│ • GPU Texture / Surface input   │             │ • 12MP/48MP full uncompressed   │
│ • Low-overhead 3D LUT (32³)     │             │ • Dual-LUT Color + Tone matrix  │
│ • Animated Grain Texture Loop   │             │ • Fine photographic grain blend │
│ • Lightweight Lens Vignette     │             │ • Halation & Highlight Bloom    │
│ • Real-time Viewfinder Cropping │             │ • Authentic Light Leaks overlay │
│ • 60 FPS Metal / GLES Render    │             │ • Physical Film Frame & Stamp   │
└────────────────┬────────────────┘             └────────────────┬────────────────┘
                 ▼                                               ▼
     ┌───────────────────────┐                       ┌───────────────────────┐
     │  On-Screen Viewfinder │                       │  MediaStore / Gallery │
     └───────────────────────┘                       └───────────────────────┘
```

---

## 2. Evidence of Hardware Capture Mechanics

From the Mach-O symbol analysis of `DazzCamera` and `DZImageFilter.framework`:

- **Camera Library (`NextLevel`)**:
  - Dazz Cam embeds the open-source `NextLevel` camera engine.
  - Exported symbols: `didCompletePhotoCaptureFrom...`, `willBeginCaptureFor photoConfiguration`, `didFinishProcessingPhoto`, `NextLevelPortraitEffectsMatteDelegate`.
  - NextLevel manages camera session lifecycle, device orientation, torch/flash levels, and frame buffer distribution.

- **Exposure & White Balance Handling**:
  - Uncovered symbol: `AVCaptureWhiteBalanceGains normalize`.
  - Uncovered symbol: `ExposureDurationyAA0cD0C_So6CMTimeatF`.
  - Rather than relying solely on the device's auto-exposure, Dazz Cam applies exposure compensation and allows the user to bias exposure (EV -2.0 to +2.0) to emulate high-contrast or underexposed film stocks.

- **Lens Calibration & Undistortion**:
  - Uncovered symbol: `estimateNewCameraMatrixForUndistortRectify`.
  - Uncovered symbol: `calibrateCameraRO...`.
  - Confirms Dazz Cam computes intrinsic camera matrices to model (or rectify) lens barrel distortion, reproducing the distinct focal traits of vintage lenses and toy plastic optics (e.g. Holga, Fisheye).

- **Multi-Camera & Zoom**:
  - Uncovered symbol: `switchOverVideoZoomFactorForDeviceType`.
  - Supports optical camera switching (Wide, Ultra-Wide, Telephoto) and smooth pinch-to-zoom transitions.

---

## 3. The Dual-Pass Rendering Strategy

A critical discovery from reverse engineering Dazz Cam is that **the live viewfinder and the final photo output use differentiated rendering paths**:

### A. Viewfinder Path (Optimized for 60 FPS & Battery)
- Resolution: Rendered at viewport resolution (typically 1080p or 720p).
- Grain: Instead of calculating procedural Perlin noise on every pixel (which causes mobile GPUs to thermal throttle and drops framerates), Dazz Cam uses pre-rendered texture loops (`grain540_01` to `grain540_10`, `vhs_grain01` to `vhs_grain20`).
- Color: Applied via a single fast 3D LUT lookup kernel (`yuciLookup` / `gpuImageLookup`).
- Light Leaks & Halation: Either disabled in live preview or replaced with a lightweight approximation to maintain smooth 60 FPS interaction.

### B. Capture Path (Optimized for Photographic Fidelity)
- Resolution: Sensor native (12 MP, 24 MP, or 48 MP).
- Dual LUT Engine: Deconstructs the image into separate luminance tone curves (`_Light` LUT) and chrominance shifts (`_Color` LUT) to achieve realistic highlight roll-off and shadow dye shifts.
- Optical Effects:
  1. Highlight extraction and multi-pass hexagonal Gaussian blur for halation/bloom.
  2. Film grain blended according to photographic density curves (more grain in mid-tones, less in blown highlights).
  3. Light leaks blended in `Screen` or `Add` mode with random rotational or edge positioning.
  4. Physical film borders/sprockets composited with alpha-masking.
  5. Crisp retro date stamps rendered in the bottom-right corner.

---

## 4. Darkroom Simulation & Negative Redevelopment

Evidence extracted from `Localizable.strings` and `Darkroom.storyboardc`:
- `Redevelop_Film_Negatives`: "Redevelop the Film Negatives after shooting to adjust exposure or color."
- `Film_Negatives_Summary`: "Preserve the last film negatives setting, rather than automatically reset."
- `Save_Film_Negatives`: Dazz Cam preserves the un-graded source photograph as a "negative" in an internal darkroom cache.
- This allows users to re-render photos with different camera presets, toggle borders, adjust warmth, or remove light leaks after shooting, perfectly mirroring analog darkroom development.

---

## 5. Android Architecture Implementation Blueprint

To translate this architecture to Android:

| iOS (Dazz Cam) | Android (CRAFTCAM) | Implementation Strategy |
| :--- | :--- | :--- |
| `AVFoundation` + `NextLevel` | **CameraX (`androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`)** | Industry standard for Android; handles device quirks, front/rear switching, and tap-to-focus across thousands of OEM models. |
| Metal Kernels (`*.metallib`) | **OpenGL ES 3.0 / GLSL Shaders** | Direct hardware acceleration on Adreno, Mali, and PowerVR GPUs with 0ms driver startup latency. |
| Core Image LUT | **OpenGL 3D Texture / 2D CLUT Strip (`OES_texture_3D` or 2D Tile LUT)** | Fast `sampler2D` or `sampler3D` color grading lookup in GLSL fragment shaders. |
| `CoreMotion` Leveling | **Android `SensorManager` (Gravity & Accelerometer)** | Viewfinder leveling indicator and camera tilt feedback. |
| `CoreHaptics` Clicks | **Android `Vibrator` / `VibrationEffect`** | Tactile mechanical shutter clicks on capture and preset switching. |
| `DarkroomController` | **Jetpack Compose Darkroom ViewModel & MediaStore Worker** | Non-blocking background rendering pipeline using Kotlin Coroutines. |

