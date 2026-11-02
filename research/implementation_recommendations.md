# REA Investigation: Implementation Recommendations & Technical Roadmap

Based on the empirical findings from reverse engineering Dazz Cam, this document defines the technical roadmap, architectural patterns, and engineering recommendations for building **CRAFTCAM** — an original, high-performance Android vintage camera application.

---

## 1. Core Technology Stack Recommendations

```
┌────────────────────────────────────────────────────────┐
│                   CRAFTCAM STACK                       │
├──────────────────────┬─────────────────────────────────┤
│ Language             │ Kotlin 2.x (Modern idiomatic)   │
│ UI Framework         │ Jetpack Compose + Material 3    │
│ Camera Engine        │ Android CameraX 1.4+            │
│ GPU Pipeline         │ OpenGL ES 3.0 / GLSL ES 3.00    │
│ State Management     │ Kotlin StateFlow + ViewModel    │
│ Async / Concurrency  │ Kotlin Coroutines & Dispatchers │
│ Media Storage        │ Android MediaStore API          │
│ Architecture         │ Clean Architecture / Modular    │
└──────────────────────┴─────────────────────────────────┘
```

---

## 2. Real-Time Viewfinder Pipeline Architecture

### Objective
Maintain rock-solid 60 FPS live camera preview with zero perceptible shutter lag, low thermal dissipation, and instant live effect preview.

### Pipeline Design
1. **CameraX Setup**:
   - Bind `Preview.Builder()` to a custom `SurfaceProvider`.
   - Provide a `SurfaceTexture` managed by an OpenGL ES 3.0 render thread.
2. **Zero-Copy Frame Acquisition**:
   - The camera sensor streams YUV frames directly into a GL texture target `GL_TEXTURE_EXTERNAL_OES`.
   - The hardware camera ISP handles YUV-to-RGB conversion automatically with 0ms CPU penalty.
3. **Single-Pass Uber-Fragment Shader**:
   - In live preview mode, avoid multiple sequential framebuffer passes.
   - Combine all active camera effects into a single GPU fragment shader execution:
     - Optical distortion & chromatic aberration UV coordinate warp.
     - 3D Color LUT trilinear interpolation.
     - Parametric film tone adjustments (contrast, temperature, tint, fade).
     - Photographic mid-tone weighted film grain.
     - Lens vignette falloff.
4. **Display**:
   - Render directly to an Android `GLSurfaceView` or `SurfaceView` embedded seamlessly within the Jetpack Compose layout.

---

## 3. High-Resolution Photo Capture & Darkroom Pipeline

### Pipeline Flow
```
[ CameraX ImageCapture ] ──► [ Uncompressed Bitmap / ByteArray ]
                                          │
                                          ▼
                         [ OpenGL ES Offscreen FBO (Native Sensor Res) ]
                                          │
                                          ▼
                         [ Stage 1: Lens Warp & Chromatic Aberration ]
                                          │
                                          ▼
                         [ Stage 2: Dual-Pass 3D LUT Color & Tone ]
                                          │
                                          ▼
                         [ Stage 3: Halation & Highlight Diffusion ]
                                          │
                                          ▼
                         [ Stage 4: High-Res Film Grain Synthesis ]
                                          │
                                          ▼
                         [ Stage 5: Light Leak Screen Composite ]
                                          │
                                          ▼
                         [ Stage 6: Film Frame & Retro Date Stamp ]
                                          │
                                          ▼
                         [ Stage 7: JPEG Compression & EXIF Injection ]
                                          │
                                          ▼
                         [ Android MediaStore / DCIM / CRAFTCAM ]
```

---

## 4. Preset Architecture & Data Modeling

Presets must be completely decoupled from shader code, structured as declarative, immutable Kotlin configuration models:

```kotlin
data class CameraPreset(
    val id: String,
    val name: String,
    val category: CameraCategory,
    val description: String,
    val year: String,
    val colorGrading: ColorGradingConfig,
    val grain: GrainConfig,
    val lens: LensConfig,
    val lightLeak: LightLeakConfig?,
    val halation: HalationConfig?,
    val dateStamp: DateStampConfig?,
    val border: BorderConfig?
)
```

Each preset family (Analog Film, CCD Digital, Disposable, Instant, Camcorder) possesses distinct characteristics:
- **Analog Film**: Gentle highlight roll-off, warm shadows, medium grain, classic slide/print borders.
- **CCD Digital**: Crisp contrast, saturated blues/greens, digital sharpening, clean blacks, no light leaks.
- **Disposable**: Heavy peripheral vignette, plastic chromatic aberration, high contrast, warm light leaks.
- **Instant**: High black point fade, soft pastel tonal curve, white paper instant border.
- **Camcorder**: Scanlines, horizontal color bleeding, low-fidelity grain, green OSD date stamp.

---

## 5. UI/UX Design Direction: Tactile Vintage Camera Experience

1. **Dark Photographic Viewfinder Canvas**:
   - Deep matte black and graphite background (`#0D0E11`, `#15161A`).
   - High-contrast, minimal amber/orange accent colors (`#FF8C1A`) evoking vintage camera dials and LED displays.
2. **Tactile Mechanical Controls**:
   - Prominent textured physical shutter button with spring-back animation and haptic feedback.
   - Smooth rotary wheel or horizontal carousel for camera switching.
   - Distinct mechanical audio clicks for dial clicks, shutter release, and camera lens change.
3. **Four Core Navigation Surfaces**:
   - **Camera**: Immersive live viewfinder with quick controls (Flash, Aspect Ratio, Exposure EV, Camera Flip, Timer).
   - **Camera Collection**: Beautiful catalog cards showcasing camera histories, specifications, and sample photos.
   - **Gallery**: Integrated film negative album with full-screen viewer, photo metadata, and sharing.
   - **Settings**: High-resolution toggles, front camera mirroring, date stamp formatting, haptic controls.

---

## 6. Milestone Execution Strategy

To ensure zero technical debt and rock-solid stability, implementation proceeds in distinct, verifiable milestones:
- **Milestone 1**: REA Investigation & Technical Documentation (Completed).
- **Milestone 2**: Android Project Setup, CameraX integration, and live OpenGL ES preview.
- **Milestone 3**: Camera Collection UI & Preset configuration engine.
- **Milestone 4**: Real-time GLSL Color Grading & 3D LUT pipeline.
- **Milestone 5**: Photographic Film Grain & Analog tone curves.
- **Milestone 6**: Light leaks, optical halation/bloom, and lens effects.
- **Milestone 7**: Full-resolution capture pipeline, EXIF handling, and MediaStore export.
- **Milestone 8**: Integrated gallery, photo detail inspector, and settings.
- **Milestone 9**: Mobile performance optimization, thermal testing, and battery efficiency.
- **Milestone 10**: APK packaging, smoke testing, and final deliverables.

