# REA Investigation: Image Processing Engine & Shader Analysis

## 1. Metal Shader Pipeline Deconstruction

In Dazz Cam, all real-time and post-capture image transformations are executed via Apple Metal compute and Core Image kernels packaged in precompiled `.metallib` libraries. Reverse-engineering inspection of these binaries reveals the granular shader architecture:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      DAZZ CAM SHADER KERNEL MATRIX                          │
├────────────────────────┬────────────────────────────────────────────────────┤
│ Library Binary         │ Discovered Shader Entrypoints                      │
├────────────────────────┼────────────────────────────────────────────────────┤
│ filters.ci.metallib    │ • lookup (3D LUT color mapping)                    │
│                        │ • rgbShift, Aberration, prism (Optical aberration) │
│                        │ • fishEye, antiFishEye (Lens barrel distortion)    │
│                        │ • toneCurve, exposure, fade (Tonal adjustments)    │
│                        │ • alphaBlendKernel, normalOverKernel (Compositing) │
│                        │ • spinBlur, verticalBar, rand (Noise & motion)     │
├────────────────────────┼────────────────────────────────────────────────────┤
│ HSL.ci.metallib        │ • hslCurve                                         │
│                        │ • controlHues, controlHueOffsets                   │
│                        │ • controlSatOffsets, controlLightOffsets           │
├────────────────────────┼────────────────────────────────────────────────────┤
│ Tone.ci.metallib       │ • shadowHighlightAdjust                            │
│                        │ • (sourceImage, blurImage, shadow, highlight)      │
├────────────────────────┼────────────────────────────────────────────────────┤
│ DustComposition        │ • compositDustInOneMetal                           │
│ .ci.metallib           │ • (dustAtlas, positions, cellIndices, layoutInfo)  │
├────────────────────────┼────────────────────────────────────────────────────┤
│ hexagonal.ci.metallib  │ • hexagonalBokehBlurPre                            │
│                        │ • hexagonalBokehBlurAlphaVertical/Diagonal         │
│                        │ • hexagonalBokehBlurBravoCharlie                   │
├────────────────────────┼────────────────────────────────────────────────────┤
│ StarComposition        │ • starPreprocess, compositeStarsInOneMetal         │
│ .ci.metallib           │ • (contrastValue, thresholdValue, blurRadius)      │
├────────────────────────┼────────────────────────────────────────────────────┤
│ DZImageFilter/default  │ • yuciLookup, gpuImageLookup                       │
│ .metallib              │ • (inputImage, inputLUT, intensity)                │
└────────────────────────┴────────────────────────────────────────────────────┘
```

---

## 2. Mathematical Models for Original Android Re-implementation

To independently implement this functionality in Android without copying proprietary code, the mathematical models must be reconstructed from first principles.

### A. 3D Color Lookup Table (LUT) Sampling
A standard 3D color lookup table maps an input RGB color $(R, G, B) \in [0, 1]^3$ to a graded output color.
In mobile OpenGL ES, to ensure universal compatibility across all Adreno, Mali, and PowerVR GPUs, 3D LUTs are represented as a 2D mosaic texture of dimensions $512 \times 512$ (containing $8 \times 8 = 64$ slices of $64 \times 64$ pixels):

Given an input color $C = (r, g, b)$ where $r, g, b \in [0, 1]$:
1. Scale blue coordinate to slice index:
   $$z = b \cdot 63.0$$
   $$z_{\text{low}} = \lfloor z \rfloor, \quad z_{\text{high}} = \min(z_{\text{low}} + 1, 63.0), \quad f = \text{fract}(z)$$
2. Compute 2D tile coordinates for $z_{\text{low}}$ and $z_{\text{high}}$:
   $$x_{\text{tile}} = z_{\text{low}} \pmod 8, \quad y_{\text{tile}} = \lfloor z_{\text{low}} / 8 \rfloor$$
   $$u_{\text{low}} = \frac{x_{\text{tile}} \cdot 64.0 + 0.5 + r \cdot 63.0}{512.0}, \quad v_{\text{low}} = \frac{y_{\text{tile}} \cdot 64.0 + 0.5 + g \cdot 63.0}{512.0}$$
3. Sample both slices and linearly interpolate:
   $$C_{\text{out}} = \text{mix}(\text{texture}(LUT, uv_{\text{low}}), \text{texture}(LUT, uv_{\text{high}}), f)$$
4. Blend with original image by preset intensity $\alpha$:
   $$C_{\text{final}} = \text{mix}(C_{\text{in}}, C_{\text{out}}, \alpha)$$

---

### B. Analytical Photographic Grain Engine
Instead of uniform digital noise, photographic film grain has two key physical properties:
1. Spatial correlation (clustering into grains of variable size).
2. Luminance-dependent density (maximum in mid-tones, suppressed in pure blacks and saturated highlights).

The luminance $Y$ of a pixel is computed via ITU-R BT.709:
$$Y = 0.2126 R + 0.7152 G + 0.0722 B$$

The grain response curve $W(Y)$ is modeled as an inverted parabola:
$$W(Y) = 1.0 - 4.0 \cdot (Y - 0.5)^2$$
$$W(Y) = \max(W(Y), 0.0)$$

High-frequency procedural pseudo-random noise is generated on the GPU using a high-entropy hash:
$$\text{hash}(uv) = \text{fract}(\sin(uv.x \cdot 12.9898 + uv.y \cdot 78.233 + \text{seed}) \cdot 43758.5453)$$

For colored grain (such as in high-ISO color film or CCD sensor noise), independent seeds are evaluated for RGB:
$$N = (\text{hash}(uv, s_R), \text{hash}(uv, s_G), \text{hash}(uv, s_B)) - 0.5$$

The final grain is composited in photographic soft-light mode:
$$C_{\text{grain}} = C + N \cdot W(Y) \cdot \text{intensity}$$

---

### C. Optical Halation & Bloom Engine
Halation occurs when intense light penetrates the film emulsion and reflects off the backing layer, scattering back into the red-sensitive dye layer.

```
Incoming Light ──► [ Blue Layer ] ──► [ Green Layer ] ──► [ Red Layer ] ──► [ Film Base ]
                                                                                  │
                                                                         [ Reflective Halation ]
                                                                                  ▼
Outgoing Aura  ◄── [ Red Halo   ] ◄───────────────────────────────────────────────┘
```

The algorithm is formulated in 3 stages:
1. **Luminance Threshold & Red Isolation**:
   $$L = \max(R, \max(G, B))$$
   $$K = \text{smoothstep}(T_{\text{threshold}}, 1.0, L)$$
   $$\vec{H}_{\text{source}} = K \cdot (R \cdot 1.4, G \cdot 0.25, B \cdot 0.05)$$
2. **Hexagonal / Gaussian Spatial Diffusion**:
   $$\vec{H}_{\text{blurred}} = \sum_{i=-N}^{N} w_i \cdot \vec{H}_{\text{source}}(uv + \vec{d}_i \cdot \text{radius})$$
3. **Screen / Additive Composite**:
   $$C_{\text{final}} = C + \vec{H}_{\text{blurred}} \cdot \text{bloomIntensity}$$

---

### D. Lens Distortion & Chromatic Aberration
Vintage prime lenses and disposable cameras exhibit optical chromatic aberration, where different wavelengths of light bend at slightly different angles as they pass through the glass or plastic element.

Given normalized viewport coordinates centered at $(0, 0)$:
$$\vec{p} = uv - (0.5, 0.5)$$
$$r^2 = \vec{p} \cdot \vec{p}$$

1. **Radial Barrel Distortion**:
   $$\vec{p}_{\text{distorted}} = \vec{p} \cdot (1.0 + k_1 r^2 + k_2 r^4)$$
   $$uv_{\text{distorted}} = \vec{p}_{\text{distorted}} + (0.5, 0.5)$$

2. **Transverse Chromatic Separation**:
   $$uv_R = uv_{\text{distorted}} + \vec{p} \cdot (\delta_{\text{chroma}} \cdot r^2)$$
   $$uv_G = uv_{\text{distorted}}$$
   $$uv_B = uv_{\text{distorted}} - \vec{p} \cdot (\delta_{\text{chroma}} \cdot r^2)$$

Output color is assembled from displaced color channels:
$$C_{\text{final}} = (R(uv_R), G(uv_G), B(uv_B))$$

---

### E. Parametric Color Tone Adjustments

The core grading parameters operate in linearized or perceptual color space:
- **Temperature & Tint**:
  $$\Delta T \implies R += 0.1 \cdot \text{temp}, \quad B -= 0.1 \cdot \text{temp}$$
  $$\Delta \text{Tint} \implies G += 0.1 \cdot \text{tint}, \quad R -= 0.05 \cdot \text{tint}, \quad B -= 0.05 \cdot \text{tint}$$
- **Contrast**:
  $$C = (C - 0.5) \cdot \text{contrast} + 0.5$$
- **Saturation**:
  $$C = \text{mix}(\vec{1} \cdot Y, C, \text{saturation})$$
- **Film Fade (Black Level Lift)**:
  $$C = C \cdot (1.0 - \text{fade}) + \text{fade}$$

---

## 3. Mobile Performance & Thermal Architecture for Android

To guarantee smooth 60 FPS live preview without device overheating on mid-range and low-end Android chipsets:
1. **Single-Pass Uber-Shader for Live Preview**:
   Combine 3D LUT sampling, chromatic aberration, vignette, and grain in a single GPU draw call. This completely eliminates intermediate texture allocations and bandwidth-heavy framebuffer swaps.
2. **Half-Precision (FP16 / `mediump`) Floats**:
   Use `mediump float` across all color calculations in GLSL. On ARM Mali and Qualcomm Adreno architectures, FP16 instructions execute at 2x the throughput of FP32 and halve register pressure.
3. **Direct OES External Texture Sampling**:
   Bind the CameraX preview surface directly to an `EGLImage` via `samplerExternalOES`. This provides zero-copy video frame streaming directly from the camera hardware ISP to the GPU.

