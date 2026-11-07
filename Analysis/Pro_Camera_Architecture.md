# CAMELEO Pro Camera Architecture
## Manual Photography Controls & Real-Time Exposure Tools

**Document Version:** 1.0.0  
**Target Hardware Baseline:** Samsung Galaxy S21 5G (`SM-G991B`), Android 14 (API 34), Mali-G78 MP14  
**Implementation Classification:** Independent Clean-Room Implementation (Android Camera2 / CameraX / OpenGL ES 3.0)  

---

## 1. Architectural Structure

CAMELEO integrates three distinct, fully isolated camera engine modes:

```
CAMELEO
│
├── Natural
│   └── Automatic flagship computational photography (0 film artifacts)
│
├── Film
│   └── Authentic analog emulsion, 3D Hald CLUTs, scanned halide grain, halation, leaks, dust, cutouts, date stamps
│
└── Pro
    └── Full manual camera controls (ISO, Shutter, Kelvin WB, MF) + Live Focus Peaking, Zebras, and 256-bin Histogram
```

### Complete Isolation Guarantee
In **CAMELEO Pro** mode:
- **0% Film Grain:** Scanned and procedural grain shaders are bypassed.
- **0% Film Halation & Glow:** Disabled.
- **0% Light Leaks & Prism Flares:** Disabled.
- **0% Scanned Dust Patina:** Viewfinder overlay dust and scratches are suppressed.
- **0% Film Borders & Body Cutouts:** Full uncropped optical frame preserved.
- **0% Retro Date Stamps:** Bypassed.
- **0% Film LUTs:** Neutral sensor preview with accurate photographic metering.

---

## 2. Camera2 Hardware Interop & Capabilities

Device capabilities are dynamically queried from `CameraCharacteristics` upon binding each camera facing:

```kotlin
data class CameraHardwareCapabilities(
    val cameraId: String,
    val hardwareLevel: Int,
    val minIso: Int,
    val maxIso: Int,
    val minShutterNs: Long,
    val maxShutterNs: Long,
    val supportsManualExposure: Boolean,
    val minFocusDistance: Float, // in diopters (> 0 means AF/MF lens)
    val supportsManualFocus: Boolean,
    val minEv: Int,
    val maxEv: Int,
    val evStep: Float,
    val supportsManualWb: Boolean,
    val supportsColorGains: Boolean
)
```

### Samsung Galaxy S21 5G (`SM-G991B`) Capability Baseline:
- **Hardware Level:** `INFO_SUPPORTED_HARDWARE_LEVEL_FULL` (Camera 0 - Rear Wide)
- **ISO Range:** `[50, 3200]`
- **Shutter Duration Range:** `[100,000 ns, 15,000,000,000 ns]` ($1/10000\,\text{s}$ to $15\,\text{s}$)
- **Focus Distance Range:** `[0.0 diopters, 10.0 diopters]` ($0.0 = \infty$, $10.0 = 10\,\text{cm}$ macro)
- **Exposure Compensation:** `[-4, +4]` in steps of $0.5\,\text{EV}$ ($-2.0\,\text{EV}$ to $+2.0\,\text{EV}$)
- **Color Correction:** Supports `COLOR_CORRECTION_MODE_TRANSFORM_MATRIX` and manual `COLOR_CORRECTION_GAINS`.

---

## 3. Manual Sensor Controls

### 3.1 ISO Control
- Manual ISO steps: $50, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200$.
- Filtered dynamically to the active sensor's `minIso..maxIso`.
- When set to `AUTO`: Camera2 AE automatically chooses sensor sensitivity, and real-time active ISO is streamed from `CaptureResult.SENSOR_SENSITIVITY` to the UI tile.

### 3.2 Shutter Speed Control
- Expressed in standard photographic notation ($1/12000\,\text{s} \dots 15\,\text{s}$).
- Conversion between photographic notation and nanoseconds:
  $$\text{exposureTimeNs} = \text{round}\left(\frac{\text{numerator} \times 10^9}{\text{denominator}}\right)$$
- When manual shutter is set with Auto ISO: Camera2 AE mode is switched to manual with the sensor duration locked to the user's choice while tracking current sensitivity.

### 3.3 Exposure Value (EV) Compensation
- Available in Auto Exposure mode via `CameraControl.setExposureCompensationIndex()`.
- Supports exact hardware range `minEv..maxEv` with device step resolution.

### 3.4 Kelvin & Tint White Balance
- **Kelvin Range:** $2500\,\text{K}$ to $9000\,\text{K}$ in $100\,\text{K}$ increments.
- **Tint Range:** $-0.50$ (Green) to $+0.50$ (Magenta).
- **Physical Model:**
  Converts Kelvin to Planckian blackbody radiator spectra (Tanner Helland blackbody model), computes inverse channel emission factors relative to green, and applies tint multipliers to produce `RggbChannelVector(rGain, gEvenGain, gOddGain, bGain)`.
- Applied via:
  ```kotlin
  builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
  builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
  builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
  ```

### 3.5 Manual Focus
- Camera2 represents lens focus distance in **diopters** ($1 / \text{meters}$):
  - $0.0\,\text{diopters} = \infty$ (Far)
  - $d_{\text{min}}\,\text{diopters} = \frac{1}{d_{\text{min}}}\,\text{meters}$ (Near / Macro)
- UI maps slider position smoothly from Near to Far with real-time physical distance approximation ($0.1\,\text{m}, 0.2\,\text{m}, 0.5\,\text{m}, 1.0\,\text{m}, 2.0\,\text{m}, \infty$).
- When manual focus is unsupported (e.g. front camera fixed-focus lens), the UI falls back gracefully to `AF ONLY` with an informative badge.

---

## 4. Real-Time Viewfinder Exposure Tools (OpenGL ES 3.0)

### 4.1 Focus Peaking
- **Algorithm:** Sobel-like 4-neighbor spatial luminance gradient computed in `FRAGMENT_SHADER_VIEWFINDER`:
  $$g_x = Y(x + \text{texel}_x, y) - Y(x - \text{texel}_x, y)$$
  $$g_y = Y(x, y + \text{texel}_y) - Y(x, y - \text{texel}_y)$$
  $$\text{edge} = \sqrt{g_x^2 + g_y^2}$$
  $$\text{mask} = \text{smoothstep}(\text{threshold}, \text{threshold} + 0.08, \text{edge})$$
- Edge highlights rendered in selectable peaking colors: **Green** (default), **Red**, **Blue**, **Yellow**.
- **Performance:** 100% GPU-bound within the single viewfinder render pass ($< 0.15\,\text{ms}$ GPU overhead). Does not modify still photo captures.

### 4.2 Zebra Overexposure Warning
- **Algorithm:** Detects pixel luminance $Y \ge \text{threshold}$ (configurable $70\%, 75\%, 80\%, 85\%, 90\%, 95\%, 100\%$).
- Generates animated $45^\circ$ diagonal high-contrast zebra stripes:
  $$\text{stripe} = \text{step}\left(0.5, \text{fract}\left(\frac{x + y + t \cdot 45}{22.0}\right)\right)$$
- **Performance:** Zero additional texture passes or CPU memory copies.

### 4.3 Live Luminance Histogram
- **256-bin Integer Histogram:** Represents luminance from $0$ (pure black) to $255$ (pure white).
- **Downsampled Asynchronous Readback:**
  - Reads a tiny $64 \times 48$ center region ($3,072$ pixels = $12.2\,\text{KB}$) via `glReadPixels`.
  - Throttled to once every 4 frames ($\sim 15\,\text{FPS}$ update rate).
  - 256-bin accumulation computed asynchronously on `Dispatchers.Default`.
  - Displayed as a semi-transparent HUD in the upper corner of the viewfinder.
  - Zero GL rendering thread stalls; zero full-resolution bitmap allocations.

---

## 5. Safe Fallback Behavior

| Hardware Condition | Fallback Action | Guarantee |
| :--- | :--- | :--- |
| **Front camera fixed focus** | Disable manual focus slider; lock to `AF` | App does not crash; front camera capture unaffected. |
| **Front camera manual WB unsupported** | Keep WB locked to `AWB` | Clean color balance preserved. |
| **Camera switching** | Re-query `CameraCharacteristics` and adapt manual values to new sensor ranges | No out-of-range Camera2 parameter exceptions. |
| **Capture during in-flight manual adjustments** | High-resolution capture uses active manual options | Full $4032 \times 3024$ native resolution preserved. |

