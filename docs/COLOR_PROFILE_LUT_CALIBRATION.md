# CAMELEO — Color Profile 3D LUT Calibration & Sensitometry

## 1. Overview

Each of the ten color profiles in CAMELEO has been independently reconstructed and calibrated using dedicated sensitometric tone curves, dye density models, and chromatic transformations.
All assets are generated via [`tools/generate_color_profile_luts.py`](file:///c:/Users/User/Downloads/DazzProject/tools/generate_color_profile_luts.py) as $512 \times 512$ Hald-8 PNGs.

---

## 2. Profile-by-Profile Calibration Matrix

| Profile ID | Display Name | Category & Stock | Characteristic Sensitometry & Color Science | Dedicated Asset |
| :---: | :--- | :--- | :--- | :--- |
| **`03G`** | **03 G** | Daylight Movie Film | Lifted shadow toe ($+0.035$), gentle shoulder ($0.88$), reduced contrast ($\gamma = 0.95$). Golden amber midtone/highlight bias, subdued chocolate shadows, restrained saturation ($0.98\times$). | [`lut_profile_03g.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_03g.png) |
| **`C400`** | **C400** | ISO 400 Print Film | Signature daylight print film baseline. Balanced S-curve, slight overexposed highlight shoulder roll-off above $0.65$, controlled olive-emerald greens, authentic peachy skin tone separation. | [`lut_profile_c400.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_c400.png) |
| **`07FF`** | **07 FF** | Motion Picture Scanner | Vision3 50D/250D film scanner profile. Vibrant cyan/teal hue separation in cool regions and skies, clean highlights, saturated palette ($1.14\times$), skin protected from cyan contamination. | [`lut_profile_07ff.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_07ff.png) |
| **`19P`** | **19 P** | Tungsten Cinema Print | Cinema print tonal transfer. High density contrast ($1.12\times$), deep magenta/violet shadow undertone, warm tungsten amber glow in highlights, saturated dye separation. | [`lut_profile_19p.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_19p.png) |
| **`19P2`** | **19 P2** | Generic Tungsten Print | Generic cinema print stock. Softer midtone gradation ($\gamma = 1.05$), delicate orchid/lavender shadow tint, smooth roll-off across warm interior lighting. | [`lut_profile_19p2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_19p2.png) |
| **`19M`** | **19 M** | Tungsten Monitor CRT | Display/monitor-calibrated warm cinema tones. CRT gamma transfer ($1.06\times$), warm amber and peach hues across midtones and highlights, clean neutral blacks. | [`lut_profile_19m.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_19m.png) |
| **`22M`** | **22 M** | Monochrome Cinema | Double-X 5222 panchromatic negative film. Spectral weighting: $\text{Luma} = 0.35 R + 0.45 G + 0.20 B$. Silver gelatin tonal curve, rich deep blacks ($toe = 0.02$), 100% achromatic. | [`lut_profile_22m.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_22m.png) |
| **`EG200`** | **EG200** | Everyday Daylight Film | Consumer daylight film (Gold/Superia). Wide dynamic range, signature subtle green tint in shadows and lower midtones ($G +0.04$), punchy warm saturation ($1.12\times$), protected skin. | [`lut_profile_eg200.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_eg200.png) |
| **`FX33`** | **FX33** | Authentic CCD Digital | Early 2000s pocket compact CCD digital grade. Punchy midtone contrast ($1.06\times$), sharp digital highlight clipping shoulder, cool neutral whites, clear cyan-blue skies. | [`lut_profile_fx33.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_fx33.png) |
| **`FXN2`** | **FXN2** | Refined Negative Film | Second-generation vintage negative film. Gentler contrast ($\gamma = 0.98$), lifted matte blacks ($toe = 0.03$), warm terracotta/apricot skin tones, soft organic roll-off. | [`lut_profile_fxn2.png`](file:///c:/Users/User/Downloads/DazzProject/app/src/main/assets/lut_profile_fxn2.png) |

---

## 3. Mathematical Foundations

### 3.1 Characteristic Sensitometry (Film S-Curve)
$$S(x, c, \text{toe}, \text{shoulder}) = \begin{cases} 
0.5 \cdot (2x)^c & x < 0.5 \\
1.0 - 0.5 \cdot (2(1 - x))^c & x \ge 0.5
\end{cases}$$
Where highlight compression applies above $\text{shoulder}$:
$$S_{\text{comp}} = \text{shoulder} + (S - \text{shoulder}) \cdot 0.40$$
And base toe density lifts shadow fog:
$$\text{Output}(x) = \text{toe} + S_{\text{comp}} \cdot (1.0 - \text{toe})$$

### 3.2 22M Panchromatic Luminance
$$\text{Luminance}_{\text{pan}} = 0.35 \cdot R + 0.45 \cdot G + 0.20 \cdot B$$
- **Red (0.35)**: Preserves healthy, luminous skin tones.
- **Green (0.45)**: Captures foliage and landscape texture with natural tonality.
- **Blue (0.20)**: Creates realistic deep skies and haze absorption.
