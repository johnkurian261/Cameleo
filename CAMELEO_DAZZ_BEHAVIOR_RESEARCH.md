# CAMELEO — Dazz Camera & Lens Behavior Research
## Clean-Room Reverse-Engineering & Architectural Analysis

---

## 1. Executive Notice & Legal Boundary Compliance

This research document analyzes the publicly observable behavior, exported symbol tables, and non-proprietary manifests of reference vintage camera applications (including Dazz Cam).
- **Zero DRM/FairPlay Bypass**: No protected binaries or encrypted containers were decrypted or reverse engineered beyond public symbol table inspection.
- **Strict Clean-Room Architecture**: All CAMELEO code, mathematical models, GLSL shaders, and Kotlin components are developed independently.
- **Three-Tier Epistemic Standard**: Every statement below is categorized strictly as **OBSERVED**, **INFERRED**, or **CAMELEO IMPLEMENTATION**.

---

## 2. Research Breakdown by Capability

### 2.1 Camera Selection & Shooting Interface Behavior

#### OBSERVED
1. When a camera body (e.g. D Classic, CCD R, CT2F, FXN-R, Fisheye) is selected from the camera list, the app does not restart the camera hardware session; it instantly transitions into the shooting screen with that camera's personality loaded.
2. In the shooting interface, the available focal lengths change dynamically depending on the selected camera:
   - Some cameras only show 35mm and 50mm.
   - Others show 28mm, 35mm, or 13mm and 26mm.
3. The available lens accessory button displays only lenses permitted for that specific camera:
   - For a standard 35mm rangefinder: Normal, Fisheye W, Prism.
   - For an ultra-wide / fisheye camera: Fisheye F, Fisheye W.
   - For a CCD camera: Normal, Starburst, ND.
4. Switching lenses does not black out the camera preview, recreate the OpenGL context, or reset exposure compensation.

#### INFERRED
1. The camera system is structured around an immutable or configuration-driven camera profile model rather than hardcoded UI branches.
2. Lens accessories are modeled as optical post-processing passes rather than distinct camera profiles.
3. The focal length selector changes either the physical sensor zoom factor (when supported by hardware multi-lens systems) or the digital composition cropping factor.

#### CAMELEO IMPLEMENTATION
- We create `CameleoCameraProfile`, defining:
  - `supportedFocalLengths: List<FocalLengthOption>`
  - `supportedLenses: List<LensType>`
  - `defaultLens: LensType`
  - `defaultFocalLength: Float`
  - `defaultAspectRatio: AspectRatioMode`
- `CameraManager` manages `CameraShootingState` in Kotlin `StateFlow`.
- Changing cameras or lenses updates the GL shader uniforms and composition framing in under 5ms without re-binding CameraX.

---

### 2.2 Fisheye Optical Transformation (Fisheye F vs Fisheye W)

#### OBSERVED
1. Selecting **Fisheye F** creates a dramatic, bulbous curvilinear optical distortion:
   - The center of the image remains relatively sharp and rectilinear.
   - Distortion increases rapidly toward the perimeter with noticeable radial stretching.
   - Moderate mechanical vignetting curves inwards at the four corners without fully clipping into a black circular circle.
2. Selecting **Fisheye W** creates a gentler wide-angle optical curvature:
   - Flatter field of view with mild curvilinear barrel distortion.
   - Low vignetting and subtle edge falloff.
3. Visible transverse chromatic aberration (blue/red color fringing) occurs selectively along high-contrast curved edges near the borders.

#### INFERRED
1. The distortion is evaluated using a non-linear polynomial radial mapping in texture coordinates:
   $$r_{\text{distorted}} = r \cdot (1 + k_1 r^2 + k_2 r^4)$$
   or an equi-solid angle projection:
   $$r_{\text{distorted}} = \tan(r \cdot \theta) / \theta$$
2. Fisheye F utilizes a higher second-order distortion coefficient ($k_1 \approx 0.65, k_2 \approx 0.35$) while Fisheye W utilizes a moderate first-order coefficient ($k_1 \approx 0.28, k_2 \approx 0.08$).
3. The center is preserved by setting the derivative $\frac{dr_{\text{distorted}}}{dr}\Big|_{r=0} \approx 1.0$.

#### CAMELEO IMPLEMENTATION
- Implemented in OpenGL ES 3.0 fragment shader via radial coordinate transformation:
  $$\vec{p} = \text{uv} - 0.5, \quad r = \|\vec{p}\|$$
  $$\vec{p}_{\text{fish}} = \vec{p} \cdot \left(1.0 + k_{\text{dist}} \cdot r^2 + k_{\text{edge}} \cdot r^4\right)$$
- `Fisheye F`: $k_{\text{dist}} = 0.62$, $k_{\text{edge}} = 0.32$, vignette falloff $0.55$, edge softness $0.18$.
- `Fisheye W`: $k_{\text{dist}} = 0.26$, $k_{\text{edge}} = 0.08$, vignette falloff $0.22$, edge softness $0.08$.
- High-resolution parity in `ImageProcessor.kt` using an identical bilinear coordinate lookup.

---

### 2.3 Prism Lens Optical Splitting

#### OBSERVED
1. When the Prism accessory is selected, the scene exhibits optical refraction:
   - The central subject remains distinct and recognizable.
   - Lateral highlights split into 2–3 displaced spectral copies radiating outward toward the frame perimeter.
   - The offset copies exhibit chromatic dispersion (red, green, and blue components separated by slight angular offsets).
   - Soft highlight blooming occurs where refracted rays overlap.

#### INFERRED
1. This is not a color filter; it is an optical multi-tap refraction shader.
2. It samples the scene texture at symmetrical angular offsets:
   $$\vec{\delta}_1 = \vec{d} \cdot \text{offset}, \quad \vec{\delta}_2 = -\vec{d} \cdot \text{offset}$$
   where $\vec{d}$ is aligned with the radial vector from the frame center.
3. A chromatic dispersion factor shifts red and blue channels by an additional sub-offset:
   $$\vec{\delta}_R = \vec{\delta} \cdot 1.05, \quad \vec{\delta}_B = \vec{\delta} \cdot 0.95$$

#### CAMELEO IMPLEMENTATION
- In GLSL `FRAGMENT_SHADER_VIEWFINDER`:
  - Primary sample at central distorted UV.
  - Two refracted secondary samples offset along the radial axis:
    $\vec{uv}_1 = \text{clamp}(uv + \vec{p} \cdot 0.045 \cdot \text{prismStrength}, 0.001, 0.999)$
    $\vec{uv}_2 = \text{clamp}(uv - \vec{p} \cdot 0.045 \cdot \text{prismStrength}, 0.001, 0.999)$
  - Chromatic channel separation across offset samples with additive highlight weight.
  - Central region weighted by $(1.0 - \text{smoothstep}(0.1, 0.6, r))$ to preserve subject facial sharpness.

---

### 2.4 Starburst Lens (Highlight Diffraction Rays)

#### OBSERVED
1. Specular points of light (street lamps, candles, specular sun reflections) produce clean 4-point or 6-point diffraction rays.
2. Midtones and dark areas are completely unaffected by star rays.
3. The rays diminish smoothly with distance from the light source without banding.

#### INFERRED
1. A luminance threshold pass isolates specular highlights ($luma > 0.85$).
2. Directional sampling along diagonal axes ($\pm 45^\circ$ or $0^\circ, 60^\circ, 120^\circ$) accumulates light intensity away from the bright core.
3. The accumulated ray mask is composited over the scene using additive or screen blending.

#### CAMELEO IMPLEMENTATION
- In GLSL:
  - Extract specular core: `spec = max(0.0, luma - uStarburstThreshold) / (1.0 - uStarburstThreshold)`
  - Sample 4 directional axes at 2 radial distances along $45^\circ$ diagonals.
  - Accumulate ray contribution and add to the color buffer:
    $\text{color} \leftarrow \text{color} + \text{raySum} \cdot \text{uStarburstStrength} \cdot \text{vec3}(1.0, 0.95, 0.85)$
  - Preserves warm incandescent starburst color without blowing out midtones.

---

### 2.5 Neutral Density (ND) Filter

#### OBSERVED
1. Selecting the ND filter reduces exposure by approximately 2–3 EV stops.
2. In bright outdoor environments, highlights that would otherwise be blown out retain sky texture and cloud details.
3. In low light, the shutter speed visibly slows or the preview darkens naturally without milky black elevation.

#### INFERRED
1. The app adjusts the hardware camera exposure compensation index (AE compensation) downwards when ND is activated.
2. In preview, a multiplicative transmission factor ($T \approx 0.25$ for ND4 / 2 stops, $T \approx 0.125$ for ND8 / 3 stops) simulates optical attenuation.

#### CAMELEO IMPLEMENTATION
- When ND is selected in `CameraShootingState`:
  1. `CameraManager` offsets hardware exposure index:
     `targetIndex = (_uiState.exposureIndex - 4).coerceIn(minEv, maxEv)`
     reducing sensor exposure at the hardware level.
  2. GLSL live preview applies an optical transmission factor $T = 0.28$ (saving highlight headroom) with a deep, neutral photographic density curve.

---

### 2.6 Color Flash Accessories

#### OBSERVED
1. Certain cameras offer tinted flash filters (Warm Flash, Cool Blue Flash, Green Flash, Pink/Magenta Flash).
2. The color tint is **only active when the flash fires** (or in torch simulation mode); the preview is not tinted while shooting in ambient light.

#### INFERRED
1. Flash modes carry a `FlashColorProfile` (RGB transmission and warmth bias).
2. `ImageProcessor` applies the flash overlay matrix during the flash capture phase.

#### CAMELEO IMPLEMENTATION
- `LensType.COLOR_FLASH` with configurable tint options (`WARM`, `COOL`, `RED`, `BLUE`, `GREEN`).
- Activated in `ImageProcessor.kt` specifically when `uiState.flashMode != FLASH_MODE_OFF`.

