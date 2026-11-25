# CAMELEO Skin Tone Calibration Guide & Profile Catalog

## 1. Overview & Mathematical Framework

CAMELEO's Skin Tone Intelligence Engine calibrates skin rendition and protection across 6 core camera profiles (`D_FUNS`, `CPM35`, `CCD`, `FXNR`, `VSCO`, and `NATURAL`). Each profile defines the behavior of human skin under vintage color grading without compromising physiological authenticity.

The pipeline executes in 4 stages for each pixel $i$:

$$\begin{aligned}
\text{Luma } Y_i &= 0.299 R_i + 0.587 G_i + 0.114 B_i \\
\text{Conf } c_i &= \text{refinedMask}[i] \quad (c_i \in [0.0, 1.0])
\end{aligned}$$

### Stage 1: Lower-Midtone Lift
For $Y_i \in [30, 160]$:
$$L_{factor} = 4 \times \left(\frac{Y_i - 30}{130}\right) \times \left(1 - \frac{Y_i - 30}{130}\right) \times \text{midLift} \times c_i \times 255$$
$$R' = R + 0.95 \times L_{factor}, \quad G' = G + 0.90 \times L_{factor}, \quad B' = B + 0.80 \times L_{factor}$$

### Stage 2: Camera-Specific Gains
$$R'' = R' \times (1.0 + (g_R - 1.0) \times w_{strength} \times c_i)$$
$$G'' = G' \times (1.0 + (g_G - 1.0) \times w_{strength} \times c_i)$$
$$B'' = B' \times (1.0 + (g_B - 1.0) \times w_{strength} \times c_i)$$

### Stage 3: Asymptotic Highlight Rolloff
For $C_{max} = \max(R'', G'', B'') > \text{knee} \times 255$:
$$\text{excess} = C_{max} - \text{knee} \times 255, \quad \text{headroom} = 255 - \text{knee} \times 255$$
$$C_{rolled} = \text{knee} \times 255 + \text{headroom} \times \left(\frac{\text{excess}}{\text{excess} + 0.80 \times \text{headroom}}\right)$$
$$C_{out} = C'' \times \left(1.0 - \left(1.0 - \frac{C_{rolled}}{C_{max}}\right) \times c_i\right)$$

### Stage 4: Perceptual Saturation Protection
$$\text{sat} = \frac{C_{max} - C_{min}}{C_{max}}$$
If $\text{sat} > S_{max}$:
$$C_{final} = Y + (C_{out} - Y) \times \left(1.0 - \left(1.0 - \frac{S_{max}}{\text{sat}}\right) \times c_i \times S_{protection}\right)$$

---

## 2. Calibration Parameter Matrix

| Parameter | Description | D_FUNS | CPM35 | CCD | FXNR | VSCO | NATURAL |
|---|---|---|---|---|---|---|---|
| **`id`** | Profile Identifier | `skin_dfuns` | `skin_cpm35` | `skin_ccd` | `skin_fxnr` | `skin_vsco` | `skin_natural` |
| **`warmthStrength`** | Warmth blend weight | `0.18` | `0.22` | `0.12` | `0.14` | `0.10` | `0.05` |
| **`skinGains` $[R, G, B]$** | Skin tint multipliers | `[1.06, 0.98, 0.90]` | `[1.08, 0.97, 0.86]` | `[1.03, 1.00, 0.94]` | `[1.02, 0.99, 0.96]` | `[1.01, 1.00, 0.97]` | `[1.00, 1.00, 1.00]` |
| **`skinMidtoneLift`** | Shadow soften factor | `0.035` | `0.045` | `0.020` | `0.025` | `0.020` | `0.015` |
| **`skinHighlightKnee`**| Specular rolloff start | `0.82` (209) | `0.80` (204) | `0.88` (224) | `0.86` (219) | `0.87` (222) | `0.90` (230) |
| **`maxSkinSaturation`**| Saturation ceiling | `0.68` | `0.70` | `0.66` | `0.64` | `0.63` | `0.62` |
| **`saturationProt`** | Damping rate | `0.88` | `0.85` | `0.86` | `0.88` | `0.87` | `0.90` |
| **`highlightProt`** | Specular protection | `0.90` | `0.86` | `0.92` | `0.88` | `0.89` | `0.92` |
| **`shadowProt`** | Deep tone protection | `0.80` | `0.85` | `0.80` | `0.82` | `0.80` | `0.85` |
| **`maxHueShift`** | Maximum allowed shift | `4.0°` | `5.0°` | `3.5°` | `3.0°` | `3.0°` | `2.0°` |
| **`textureDampening`** | Grain blend on face | `0.25` | `0.20` | `0.15` | `0.22` | `0.20` | `0.35` |

---

## 3. Profile-by-Profile Aesthetic Objectives

### 3.1 D_FUNS (Single-Use 35mm Disposable Film)
- **Aesthetic Identity**: High-contrast flash falloff, saturated plastic-lens characteristics, organic warm film negative curves.
- **Skin Response**: Warm golden-amber direct flash highlights with gentle midtone shadow fill.
- **Protection Priority**: Prevents flash blowout on oily forehead/nose skin while preserving characteristic film grain texture.

### 3.2 CPM35 (Golden Early Digital Compact)
- **Aesthetic Identity**: Calibrated directly against References 1, 2, 3, and 6. Rich amber tungsten indoor glow, glowing highlights, soft electronic diffusion.
- **Skin Response**: Generous amber-golden skin tint with deep warm midtones.
- **Protection Priority**: Prevents red clothing (as in Reference 2) and amber incandescent lamps from pushing facial skin into radioactive neon orange.

### 3.3 CCD (2000s Y2K Digicam)
- **Aesthetic Identity**: Calibrated directly against References 4, 5, 7, and 8. Crisp sensor texture, golden raking daylight, yellow-green fluorescent tolerance.
- **Skin Response**: Neutral-golden daylight tones with balanced skin saturation.
- **Protection Priority**: Prevents green fluorescent cast guard from turning faces sickly green under public transit or office lighting (Reference 7).

### 3.4 FXNR (Classic 35mm Color Negative)
- **Aesthetic Identity**: Fine color negative dye clouds, subtle contrast rolloff, pastel greens, clean teals.
- **Skin Response**: True-to-life portraiture with subtle peach undertones.
- **Protection Priority**: High micro-contrast preservation across pores and freckles with balanced highlight roll-off.

### 3.5 VSCO (Contemporary Analog Emulation)
- **Aesthetic Identity**: Subtle muted tones, raised black levels, controlled highlights.
- **Skin Response**: Gentle neutral-warm portrait response with low saturation bias.
- **Protection Priority**: Balanced saturation protection that maintains natural lip, eye, and cheek flush without desaturation.

### 3.6 NATURAL (Reference Flagship Portrait Mode)
- **Aesthetic Identity**: Unfiltered computational photography reference for multi-complexion ground truth.
- **Skin Response**: $100\%$ zero hue bias ($g_R = g_G = g_B = 1.00$). Maximum fidelity across Fitzpatrick Types I through VI.
- **Protection Priority**: Strict anti-homogenization; protects deep melanin from black crush while preserving delicate pale undertones.

---

## 4. Tuning Guidelines for Engineers

When fine-tuning profiles or adding new camera presets:
1. **Never Increase `warmthStrength` Above `0.30`**: Excessive warmth triggers unnatural facial hue disparity relative to clothing and surroundings.
2. **Never Decrease `maxSkinSaturation` Below `0.55`**: Skin will appear bleached, pasty, and desaturated.
3. **Never Increase `maxHueShiftDegrees` Above `6.0°`**: Restricting hue rotation guarantees that ethnic undertones (olive, bronze, cool espresso) are never altered or Caucasian-biased.
4. **Use `debugSkinToneStages`**: Enable diagnostic stage dump in `UserTuningConfig` to inspect intermediate probability maps and refine parameters on physical devices.
