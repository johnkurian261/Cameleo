# CAMELEO Natural Camera Architecture
## Flagship Computational Photography Engine

**Document Version:** 1.0.0  
**Target Device Baseline:** Samsung Galaxy S21 5G (`SM-G991B`), Android 14 (API 34), ARM Mali-G78 MP14  
**Implementation Classification:** Independent CAMELEO Implementation (Clean-room reverse-engineering design)  

---

## 1. Executive Summary & Design Philosophy

CAMELEO is designed with two distinct photographic engines:
1. **CAMELEO Film Engine:** Emulates vintage analog emulsion, halide grain, chemical halation, optical light leaks, physical dust patina, camera body cutouts, and retro date stamps.
2. **CAMELEO Natural Engine:** A clean, flagship computational photography pipeline delivering authentic, modern, high-dynamic-range photographs characterized by accurate exposure, natural AWB, faithful skin tones across diverse complexions, asymptotic highlight roll-off, clean shadows, low chroma noise, and edge-aware sharpening.

### Absolute Invariant in Natural Mode
In **CAMELEO Natural** mode, **ALL** film-simulation artifacts are strictly disabled:
- **0% Film Grain** (scanned grain shaders and textures bypassed)
- **0% Halation & Glow**
- **0% Light Leaks & Prism Flares**
- **0% Dust & Scratches Patina**
- **0% Film Borders & Body Cutouts**
- **0% Retro Date Stamps**
- **0% Film Look-Up Tables (LUTs)**

---

## 2. Hardware Interop & Sensor Capture Strategy

### Target Hardware Profile: Samsung Galaxy S21 5G
- **Primary Wide Sensor:** 12.2 MP ($4032 \times 3024$ native resolution)
- **Sensor Size / Pixel Pitch:** 1/1.76", $1.8\,\mu\text{m}$
- **Hardware ISP:** Samsung Exynos 2100 with hardware multi-frame noise reduction and HDR blending
- **Exposure Compensation Range:** $[-4, +4]$ in steps of $0.5\,\text{EV}$ (representing $-2.0\,\text{EV}$ to $+2.0\,\text{EV}$)

### Exposure Strategy: Hardware Underexposure Bias
Digital camera sensors clip specular highlights irreversibly when sensor photosites saturate to $255$. Modern flagship computational pipelines (such as Liit Natural and Apple Camera) utilize a negative hardware exposure bias:
- **Target Bias:** $-0.3\,\text{EV}$ (calibrated baseline)
- **Hardware Mapping:** On the S21's $0.5\,\text{EV}$ step controller:
  $$\text{Index} = \text{round}\left(\frac{-0.3}{0.5}\right) = -1 \quad (-0.5\,\text{EV})$$
- **Result:** Hardware exposure compensation protects highlight head-room prior to sensor A/D conversion and ISP processing. The computational pipeline then lifts shadows and midtones smoothly in software.

---

## 3. Ten-Stage Computational Photography Pipeline

```
[CameraX Native Capture] 
         │ (12.2MP JPEG)
         ▼
[Stage 1: Single-Pass Orientation & Aspect Ratio Crop]
         │ (BitmapPool working buffer)
         ▼
[Stage 2: Linear RGB Decoding (Fast sRGB-to-Linear LUT)]
         │ (IntArrayPool linear buffer)
         ▼
[Stage 3: Edge-Gated Chroma Noise Filtering (YCbCr Space)]
         │
         ▼
[Stage 4: Sensor & AWB Linear Color Correction Matrix]
         │
         ▼
[Stage 5: Asymptotic Highlight Shoulder Roll-off (k = 0.65)]
         │
         ▼
[Stage 6: Rational Toe Shadow Lifting (Preserves Pure Black)]
         │
         ▼
[Stage 7: Soft HSL Skin-Tone Protection Masking]
         │
         ▼
[Stage 8: Linear Saturation, Vibrance & Micro-Contrast]
         │
         ▼
[Stage 9: Face-Dampened Edge-Aware Laplacian Sharpening]
         │
         ▼
[Stage 10: Multi-frame Expansion Hooks / Color Space Encode]
         │
         ▼
[Save to Gallery (DCIM/Cameleo - "CAMELEO Natural")]
```

### Stage 1: Single-Pass Transform Matrix
- Downscales images $>4096\,\text{px}$ only if required for memory safety (S21 12.2MP $4032 \times 3024$ processes at native full resolution).
- Applies sensor rotation and front camera mirroring in a single affine transformation pass directly into a pre-allocated `BitmapPool` buffer.
- Immediately releases the raw capture `Bitmap` and closes `ImageProxy` to minimize shutter latency to under $\sim 120\,\text{ms}$.

### Stage 2: Integer Packed Linear RGB Decoding
- Gamma-encoded sRGB values distort linear light physics. `NaturalPhotoProcessor` maintains precomputed static lookup tables:
  $$\text{srgbToLinear}[c] = \left(\frac{c}{255}\right)^{2.2} \times 1023.0$$
- Unpacks 32-bit ARGB pixels into linear light values ($0 \dots 1023$) with zero heap object allocations using `IntArrayPool`.

### Stage 3: Edge-Gated Chroma Noise Reduction
- Transforms pixels to $YC_bC_r$ color space.
- Detects high-frequency luma edges using a cross gradient:
  $$\Delta Y = |Y(x+1, y) - Y(x-1, y)| + |Y(x, y+1) - Y(x, y-1)|$$
- In low-gradient regions (shadows, flat walls, skies), applies a $3 \times 3$ box filter across $C_b$ and $C_r$ chroma channels.
- Eradicates unsightly purple and green color blotches without softening fine texture details or hair.

### Stage 4: Sensor & AWB Linear Color Matrix
- Corrects sensor-specific spectral sensitivities using a $3 \times 3$ linear color correction matrix:
  $$\begin{bmatrix} R' \\ G' \\ B' \end{bmatrix} = \mathbf{M}_{\text{CCM}} \begin{bmatrix} R \\ G \\ B \end{bmatrix}$$
- Calibrated for the S21 to neutralize green tint in ambient light and deliver neutral D65 daylight rendering.

### Stage 5: Asymptotic Highlight Shoulder Roll-Off
- Prevents harsh digital clipping in skies, street lamps, and specular reflections.
- Employs a smooth rational compression knee above threshold $T = 0.70$:
  $$v' = T + \frac{v - T}{1.0 + k \cdot (v - T)} \quad \text{where } k = 0.65$$
- Highlights transition smoothly to peak white without blowing out neighboring color channels or shifting hue.

### Stage 6: Rational Shadow Lift (True Black Depth)
- Lifts deep shadows while preserving contrast depth at true black:
  $$v' = v + \text{lift} \cdot (1.0 - v)^2 \cdot (1.0 - \text{toe})$$
- Unlike global gamma lifts which turn blacks into a milky gray, the quadratic falloff ensures that value $0.0$ remains strictly $0.0$.

### Stage 7: Soft HSL Skin-Tone Protection Mask
- Converts linear RGB to Hue, Saturation, and Lightness.
- Detects human skin tones around nominal center $H_0 = 26^\circ$ ($[10^\circ, 45^\circ]$ range) with broad soft weighting:
  $$w_{\text{skin}} = \exp\left(-\frac{(H - 26^\circ)^2}{2 \cdot 18^2}\right) \cdot \text{clamp}\left(\frac{S - 0.15}{0.20}, 0, 1\right)$$
- Protects skin from oversaturation, unnatural ruddy red casts, and hyper-sharpening across all ethnicities.

### Stage 8: Linear Saturation, Vibrance & Micro-Contrast
- Applies an S-curve contrast curve around perceptual midtone $0.35$.
- Enhances color vibrancy selectively: less saturated colors receive higher boost than already saturated areas.

### Stage 9: Face-Dampened Edge-Aware Laplacian Sharpening
- Computes high-pass Laplacian kernel:
  $$\nabla^2 L = 4 \cdot L(x, y) - L(x-1, y) - L(x+1, y) - L(x, y-1) - L(x, y+1)$$
- Gated by a noise floor threshold to prevent amplifying sensor grain in flat areas.
- **Face Dampening:** Sharpening intensity is attenuated by $60\%$ over the skin mask ($w_{\text{skin}}$), keeping portraits natural and flattering while preserving crisp hair, eyelashes, and clothing textures.

### Stage 10: Multi-Frame Expansion Architecture
- Designed with modular hooks allowing future multi-frame RAW/YUV burst alignment (e.g. Mertens Exposure Fusion, Motion-compensated MFNR) to feed directly into Stage 2 without altering the downstream color science.

---

## 4. Real-Time Viewfinder Sync (OpenGL ES 3.0)

To provide an authentic preview of CAMELEO Natural:
1. `GLViewfinderRenderer` dynamically toggles `uNaturalModeEnabled`.
2. When enabled:
   - Film 3D LUT sampling is bypassed.
   - Scanned film grain texture pass is bypassed.
   - Viewfinder fragment shader executes real-time linear matrix color conversion, asymptotic highlight compression, and rational shadow lift.
3. `ViewfinderOverlay` dynamically suppresses dust, camera cutouts, film borders, and date stamps.

---

## 5. Memory Safety & Concurrency Model

1. **Zero Intermediate Bitmap Allocations:**
   - All spatial transformations execute via affine Matrix into a pooled `Bitmap`.
   - All 10 computational stages execute in-place on packed integer buffers acquired from `IntArrayPool`.
   - Intermediate buffers are returned immediately to the pool in `finally` blocks.
2. **Background Concurrency:**
   - Photo capture releases the hardware shutter immediately upon `onCaptureSuccess` ($\sim 120\,\text{ms}$).
   - Computational processing executes asynchronously on `Dispatchers.Default` inside an application-level `SupervisorJob` coroutine scope, guaranteeing completion even if the user transitions to Gallery.

