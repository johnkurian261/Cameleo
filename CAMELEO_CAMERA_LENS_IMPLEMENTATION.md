# CAMELEO — Camera-Specific Shooting Screen & Lens System Implementation Report

## 1. Executive Summary & Scope Adherence
The CAMELEO camera architecture has been upgraded to a dedicated **Camera-Specific Shooting Screen & Lens System**. In strict adherence to workspace guidelines:
- **Collection / Gallery Screen Intact**: `CollectionScreen.kt` was left completely unmodified. The camera selection and collection experience retains its design and functionality.
- **Dedicated Shooting Experience**: All changes were concentrated on the shooting screen (`ViewfinderScreen.kt`), camera control managers (`CameraManager.kt`, `GLViewfinderRenderer.kt`), and full-resolution photo processing (`ImageProcessor.kt`, `ShaderConstants.kt`).
- **Clean Conceptual Separation**:
  - **CAMERA**: Simulated physical camera body (e.g., FXNR, D Classic, D Exp, CCD Digital, Instant, Fisheye, Soft Film, Warm Film, Disposable D FunS).
  - **LENS**: Optical glass accessory attached to the front of the body (e.g., Normal Prime, Wide, Fisheye F, Fisheye W, Prism, Starburst, ND, Color Flash).
  - **LOOK**: Photochemical 3D Hald CLUT color grading and film emulsion curve.
  - **FRAME**: Dynamic composition window crop, aspect ratio, and authentic border cutouts.

---

## 2. Architecture & Data Models

### 2.1 Optical Lens Profiles (`LensProfile.kt`)
Defines optical glass types, xenon gel tints, and GPU simulation parameters:
- `LensType`:
  - `NORMAL`: Standard optical prime glass ($k_1=0.0$).
  - `WIDE`: Wide-angle optical conversion element ($k_1=0.12, \text{vignette}=0.18$).
  - `FISHEYE_F`: Hemispherical circular fisheye ($k_1=0.64, k_2=0.32$, dark circular boundary, CA=0.016).
  - `FISHEYE_W`: Full-frame ultra-wide curvilinear distortion ($k_1=0.28, k_2=0.08$, soft edge falloff).
  - `PRISM`: Multi-faceted optical refraction (dispersion=0.85, chromatic splitting, ghost reflection lobes).
  - `STARBURST`: 4-point cross-screen diffraction star spikes on specular highlights (threshold=0.84, strength=0.65).
  - `ND`: Optical neutral density light attenuation ($0.28\times$ transmission, ~2 EV reduction).
  - `COLOR_FLASH`: Tinted xenon flash gelatin filter (Warm Amber, Cool Blue, Ruby Red, Neon Green).

### 2.2 Camera Body Profiles (`CameleoCameraProfile.kt` & `CameraProfileCatalog.kt`)
Each camera profile defines physical body constraints:
| Camera Profile | Preset ID | Base Optical | Available Focal Lengths | Compatible Optical Accessories | Default Ratio |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FXNR** | `analog_fxn_r` | 26mm | 35mm, 50mm | Normal, Fisheye W, Prism | 4:3 |
| **D Classic** | `analog_35mm_classic` | 26mm | 37mm, 50mm | Normal, Fisheye W, Prism, Star | 3:2 |
| **D Exp** | `analog_vintage_color` | 26mm | 28mm, 35mm | Normal, Prism, ND | 3:2 |
| **CCD Digital** | `ccd_y2k_digital` | 26mm | 35mm, 50mm | Normal, Star, ND | 4:3 |
| **Instant V** | `inst_vintage` | 26mm | 35mm (Prime) | Normal, Prism, Color Flash | 1:1 |
| **Fisheye** | `analog_gr_f` | 13mm | 13mm, 26mm | Fisheye F, Fisheye W | 4:3 |
| **Soft Film** | `analog_soft_film` | 26mm | 35mm, 50mm | Normal, Fisheye W, ND | 3:2 |
| **Warm Film** | `analog_warm_film` | 26mm | 28mm, 50mm | Normal, Fisheye W, Star | 4:3 |
| **D FunS** | `ccd_flash` | 26mm | 32mm (Fixed Plastic) | Normal, Color Flash | 4:3 |

Dynamic fallback ensures any custom preset automatically receives standard 35mm/50mm focal lengths and compatible accessories.

---

## 3. Real-Time OpenGL ES 3.0 Optical Shaders (`ShaderConstants.kt`)

The OpenGL ES 3.0 viewfinder shader (`FRAGMENT_SHADER_VIEWFINDER`) and still capture shader (`FRAGMENT_SHADER_STILL`) integrate real-time optical physics:

```glsl
// Dedicated Camera Lens System
uniform int uLensType;
uniform float uLensDistortion;
uniform float uLensEdgeCurvature;
uniform float uLensVignette;
uniform float uLensPrismDispersion;
uniform float uLensStarburstStrength;
uniform float uLensStarburstThreshold;
uniform float uLensNdFactor;
uniform vec3 uLensColorFlashTint;
uniform bool uLensIsFlashActive;
```

### Optical Transformations:
1. **Curvilinear Barrel Distortion**:
   $$r_{\text{dist}} = r \cdot (1.0 + k_1 \cdot r^2 + k_2 \cdot r^4)$$
   - Fisheye F executes hemispherical radial remapping with a smooth circular boundary hood ($r > 0.56$).
   - Fisheye W executes wide-angle full-frame curvilinear expansion with gentle peripheral roll-off.
2. **Cauchy Transverse Chromatic Aberration & Spectral Dispersion**:
   $$\text{offset} = \vec{p} \cdot (\text{effCA} \cdot r^2 \cdot 0.22)$$
   - Increased to $\text{effCA} = 0.016$ for Fisheye and $0.014$ for Prism.
3. **Prism Multi-Facet Refraction**:
   $$\Delta \vec{uv}_{1,2} = \pm (0.866, 0.50) \cdot \text{dispersion}$$
   Samples flanking refracted lobes with chromatic separation ($R \times 1.08, B \times 1.12$) blended into the primary image by radial facet weight.
4. **Specular Starburst Diffraction Rays**:
   Samples highlights where luminance $> \text{threshold}$, accumulating 4-axis diagonal diffraction spikes radiating outwards with linear falloff.
5. **Neutral Density Filter (ND)**:
   Linear light attenuation multiplied by `uLensNdFactor` ($0.28\times$).
6. **Color Flash Gel**:
   Multiplies scene radiance by `uLensColorFlashTint` when flash is active.

---

## 4. Full-Sensor 12MP Capture Processing Parity (`ImageProcessor.kt`)

To maintain complete visual parity between live preview and saved photos, `ImageProcessor.kt` incorporates matching high-performance algorithms executed across CPU cores with zero garbage collection allocations via `IntArrayPool`:
- `applyFisheyeCurvilinear(pixels, width, height, lensProfile)`: Parallel polynomial inverse mapping with circular boundary masking for Fisheye F and wide-angle mapping for Fisheye W.
- `applyPrismRefraction(pixels, width, height, lensProfile)`: Multi-sample diagonal facet displacement with Cauchy color fringe synthesis.
- `applyStarburstDiffraction(pixels, width, height, lensProfile)`: Specular highlight thresholding casting 4-axis starburst spikes.
- `ndTransmissionFactor` & `colorFlashTint`: Fused into parallel pixel processing chunks.

---

## 5. Viewfinder UI & Tactile Controls (`CameraShootingBar.kt`)

Positioned directly above the preset wheel in the shooting screen:
1. **Focal Length Selector Chips**:
   - Displays available focal lengths for the selected camera body (e.g. `[ 35mm ] [ 50mm ]`).
   - Active chip highlighted in `VintageAmber` with bold typography.
   - Tapping dynamically adapts the composition framing window (`CaptureFrameState.sizeFraction = baseMm / targetMm`) to match the field of view.
   - Cameras with a single fixed focal length display a subtle `[ 32mm FIXED ]` badge.
2. **Optical Lens Accessory Button**:
   - Displays tactile optical icon and name (e.g., `[ ⊚ FISHEYE W ▾ ]`, `[ ⟡ PRISM ▾ ]`, `[ ⊙ NORMAL ▾ ]`).
   - Highlighted in amber when an accessory is mounted.
   - Tapping opens `TactileLensSelectorSheet` displaying *only* accessories compatible with the current camera body.
   - Instant live viewfinder updates and haptic feedback on selection.
3. **Dynamic Viewfinder Model Badge**:
   - Top status badge dynamically displays active camera body, focal length, and optical accessory (e.g., `FXNR • 35MM • FISHEYE W • +0.0 EV`).

---

## 6. Build and Verification Summary
- **Kotlin Compilation (`compileDebugKotlin`)**: Successful (0 errors).
- **Unit Tests (`testDebugUnitTest`)**: 22 tasks executed, 100% tests passed.
- **APK Assembly (`assembleDebug`)**: Generated `app\build\outputs\apk\debug\app-debug.apk`.
- **Target Device**: Samsung Galaxy S21 5G (`adb-R5CR908HJ8X-c8dtFa._adb-tls-connect._tcp`).
- **Deployment Status**: Installed via ADB Streamed Install (`Success`).

