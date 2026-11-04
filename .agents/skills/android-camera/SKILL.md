---
name: android-camera
description: >-
  Provides deep domain guidance and standardized workflows for Android camera architecture in CAMELEO.
  Covers Camera2 vs CameraX selection, permission lifecycles, hardware capability querying (CameraCharacteristics),
  manual sensor controls (exposure time, ISO, focus distance, AE/AF metering), RAW/DNG sensor capture,
  stream concurrency, and hardware fallbacks across diverse Android devices.
---

# Android Camera Architecture & Hardware Integration

This skill guides the design, implementation, and troubleshooting of the camera capture subsystem for **CAMELEO** on Android. It ensures robust multi-device compatibility, optimal image quality, and low capture latency across varying hardware tiers.

---

## 1. Activation Criteria

Activate this skill when:
- Designing or modifying camera capture, preview, or recording pipelines in CAMELEO.
- Choosing between or bridging **CameraX** and **Camera2** (`Camera2Interop`).
- Querying device capabilities via `CameraCharacteristics` (Hardware level, RAW support, sensor ranges).
- Implementing manual sensor controls: exposure compensation, manual ISO (`SENSOR_SENSITIVITY`), shutter speed (`SENSOR_EXPOSURE_TIME`), focus distance (`LENS_FOCUS_DISTANCE`), and white balance.
- Handling Android runtime permissions (`android.permission.CAMERA`, `RECORD_AUDIO`, `READ_MEDIA_IMAGES`).
- Managing stream configurations, high-resolution aspect ratios, and zero-shutter-lag capture.
- Investigating device-specific camera bugs, stream crashes, or preview freezing.

---

## 2. Architectural Principles

### 2.1 CameraX vs Camera2 Boundary
In CAMELEO, we maintain a clean architectural boundary:

| Feature / Responsibility | Implementation Layer | Justification |
| :--- | :--- | :--- |
| **Viewfinder & Preview** | **CameraX** (`Preview`, `PreviewView`) | Automatic lifecycle management, display rotation handling, surface compatibility across 99%+ of Android devices. |
| **High-Quality Still Capture** | **CameraX** (`ImageCapture`) | Native zero-shutter lag (ZSL), high-resolution output negotiation, vendor extension support (Night, HDR, Bokeh). |
| **Manual Sensor Parameters** | **Camera2Interop** / `Camera2CameraControl` | Allows setting manual ISO, exposure duration, focus distance, and fixed AE/AWB locks without rewriting the lifecycle engine. |
| **RAW / DNG Sensor Output** | **Camera2** (`ImageReader` / `DngCreator`) | Required for 16-bit linear sensor dump before ISP tone mapping and sharpening. |

### 2.2 Hardware Level Guarantees (`CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL`)
Never assume a hardware feature exists without runtime verification:

1. **`LEGACY`**: Emulated HAL1 layer. No per-frame controls, no manual sensor overrides, slow capture. Fall back to standard auto-exposure and CameraX defaults.
2. **`LIMITED`**: Basic Camera2 support. Allows some manual controls, but preview and capture streams share constraints; high-speed bursts or RAW may not be supported.
3. **`FULL`**: Per-frame manual controls, sensor read-out synchronization, manual ISO and exposure time, high-speed 30fps burst capture.
4. **`LEVEL_3`**: Added YUV re-processing, RAW sensor output (`RAW10`, `RAW12`, `RAW_SENSOR`), and advanced stream combinations.

---

## 3. Workflows

### Workflow A: Device Hardware Capability Audit
Before enabling manual exposure controls or RAW capture, run a capability query:

```kotlin
fun auditCameraCapabilities(context: Context, cameraId: String): CameraDeviceProfile {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val characteristics = manager.getCameraCharacteristics(cameraId)

    val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

    val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) // Range<Int>
    val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) // Range<Long> in nanoseconds
    val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) // Float (diopters)

    return CameraDeviceProfile(
        cameraId = cameraId,
        hardwareLevel = hardwareLevel,
        supportsRaw = supportsRaw,
        isoRange = isoRange,
        exposureTimeRange = exposureTimeRange,
        supportsManualFocus = (minFocusDistance != null && minFocusDistance > 0f)
    )
}
```

### Workflow B: Configuring CameraX for Maximum Native Quality
To prevent the low-resolution downscaling or sub-optimal compression observed on stock setups:

```kotlin
val imageCapture = ImageCapture.Builder()
    // 1. Force highest available resolution without downsampling
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    )
    // 2. Prioritize image quality over shutter latency
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
    // 3. Set lossless/highest JPEG quality
    .setJpegQuality(100)
    .build()
```

### Workflow C: Applying Manual Sensor Controls via Camera2Interop
When the user selects manual shutter speed or ISO on supported hardware:

```kotlin
@OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
fun setManualExposure(
    cameraControl: CameraControl,
    iso: Int?,           // e.g., 100, 400, 800
    exposureTimeNs: Long? // e.g., 1_000_000_000L / 125L (1/125s)
) {
    val camera2Control = Camera2CameraControl.from(cameraControl)
    val captureRequestOptions = CaptureRequestOptions.Builder().apply {
        if (iso != null && exposureTimeNs != null) {
            // Disable Auto-Exposure to allow manual control
            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)
        } else {
            // Restore Auto-Exposure
            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
    }.build()

    camera2Control.setCaptureRequestOptions(captureRequestOptions)
}
```

### Workflow D: Handling Shutter Sound & Silent Capture
- Android enforces shutter sounds in specific jurisdictions (e.g., Japan, South Korea) via `MediaActionSound.mustPlayShutterSound()`.
- When using CameraX `takePicture()`, CameraX does not inject an artificial sound by default; audio playback is managed by the application layer.
- Ensure any UI audio playback respects:
  ```kotlin
  val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  val ringerMode = audioManager.ringerMode
  val canMute = !MediaActionSound.mustPlayShutterSound()
  if (canMute && (ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
      // Suppress UI shutter click sound
  }
  ```

---

## 4. Tools & Environment Reference

- **Android SDK Path**: `C:\Users\User\AppData\Local\Android\Sdk`
- **Platform Tools**: `platform-tools\adb.exe`
- **Key Inspection Commands**:
  - Dump camera characteristics: `adb shell dumpsys media.camera`
  - Check active camera clients: `adb shell dumpsys camera`
  - Inspect current frame rates and dropped frames: `adb shell dumpsys SurfaceFlinger --latency SurfaceView`

---

## 5. Limitations & Edge Cases

1. **Exposure Time Units**:
   - `CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE` is expressed in **nanoseconds** ($10^{-9}$ seconds).
   - A shutter speed of $1/250\text{s} = 4,000,000\text{ ns}$. Always convert accurately: $\text{ns} = 10^9 / \text{speed}$.
2. **Camera Hardware Level Differences**:
   - Front-facing cameras frequently operate at `LIMITED` even when rear sensors are `FULL` or `LEVEL_3`.
   - Never apply rear-sensor manual parameters to front sensors without checking their specific `CameraCharacteristics`.
3. **Capture Latency in MAXIMIZE_QUALITY**:
   - In `CAPTURE_MODE_MAXIMIZE_QUALITY`, the hardware ISP may capture multiple frames for post-capture HDR or YUV multi-frame noise reduction.
   - The UI must maintain an asynchronous processing queue and never block the main thread.

---

## 6. Expected Outputs & Verification

1. **Capability Matrix Output**: Structured report or log entry specifying device hardware level, supported resolutions, and sensor limits.
2. **Zero-Lag Shutter**: Measured latency between tap and capture callback under 300ms on `FULL` hardware devices.
3. **Full Native Resolution**: Output JPEG/Bitmap dimensions matching max physical sensor pixels (e.g., $4000 \times 3000$ for 12MP).
