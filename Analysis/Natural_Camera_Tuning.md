# CAMELEO Natural Camera Tuning & Parameter Guide
## S21 5G Hardware-Calibrated Baseline

**Document Version:** 1.0.0  
**Target Device Baseline:** Samsung Galaxy S21 5G (`SM-G991B`), Android 14 (One UI 6.0), Exynos 2100  
**Implementation Classification:** Independent CAMELEO Parameter Calibration  

---

## 1. Overview of the Natural Tuning Lab

The **CAMELEO Natural Engine** includes a real-time tuning architecture designed to balance highlight headroom, midtone skin fidelity, shadow depth, and microcontrast.

Settings can be inspected and calibrated in real time via the **⚙ LAB TUNING** bottom sheet, accessible directly from the live viewfinder whenever **NATURAL** mode is selected.

---

## 2. Samsung Galaxy S21 5G (`SM-G991B`) Calibrated Baseline

The table below details the complete parameter suite, its tuning range, and the hardware-calibrated default values:

| Parameter | Unit / Range | S21 Baseline Default | Functional Role & Mathematical Implementation |
| :--- | :--- | :--- | :--- |
| **Exposure Bias** | $-2.0\,\text{EV} \dots +1.0\,\text{EV}$ | **$-0.30\,\text{EV}$** | Maps to hardware exposure compensation index $-1$ ($-0.5\,\text{EV}$). Protects specular highlights from sensor photosite clipping. |
| **Color Temperature** | $-1.0 \dots +1.0$ | **$0.00$** | Adjusts red/blue balance in linear color space prior to sRGB display encoding. |
| **Color Tint** | $-1.0 \dots +1.0$ | **$+0.02$** | Shifts green/magenta balance to neutralize Samsung ISOCELL/Sony IMX555 ambient green cast. |
| **Saturation** | $0.50 \dots 2.00$ | **$1.06$** | Restrained linear color vibrance; boosts muted tones without oversaturating vivid colors. |
| **Contrast** | $0.50 \dots 2.00$ | **$1.04$** | Gentle S-curve contrast centered at perceptual luminance $0.35$. |
| **Highlight Roll-off** | $0.00 \dots 1.00$ | **$0.65$** | Asymptotic rational shoulder compression above threshold $T = 0.70$. Prevents harsh specular clipping. |
| **Shadow Lift** | $0.00 \dots 1.00$ | **$0.18$** | Quadratic toe shadow lift. Lifts deep shadow detail while preserving true $0.0$ black depth. |
| **Chroma Noise Red.** | $0.00 \dots 1.00$ | **$0.35$** | Edge-gated $3 \times 3$ bilateral filter in $YC_bC_r$ space. Eradicates purple/green chroma blotches. |
| **Edge Sharpening** | $0.00 \dots 1.00$ | **$0.28$** | High-pass Laplacian filter ($3 \times 3$) gated by noise floor to preserve smooth gradients. |
| **Local Contrast** | $0.00 \dots 1.00$ | **$0.12$** | Micro-contrast enhancement along texture boundaries. |
| **Skin Protection** | $0.00 \dots 1.00$ | **$0.85$** | Soft HSL Gaussian mask ($26^\circ$ hue center); dampens saturation boost and attenuates sharpening by $60\%$ on skin. |
| **A/B Split Compare** | Boolean (`ON`/`OFF`) | **`OFF`** | Generates split-screen photo with 2px amber divider (Left = Original Sensor Capture, Right = CAMELEO Natural). |

---

## 3. Mathematical Formulations

### A. Asymptotic Highlight Compression (Rational Shoulder)
To eliminate digital clipping in highlights (e.g. bright skies and window light), pixels with linear luminance $v > T$ ($T = 0.70$) are compressed asymptotically:
$$v' = T + \frac{v - T}{1.0 + k \cdot (v - T)}$$
where $k = \text{highlightRollOff} = 0.65$.  
As $v \to \infty$, $v'$ approaches $T + 1/k = 0.70 + 1.54 \approx 2.24$, ensuring a soft, filmic shoulder roll-off rather than abrupt flatline clipping.

### B. True-Black Preserving Shadow Lift
To recover dark shadow details without washing out blacks:
$$v' = v + \text{lift} \cdot (1.0 - v)^2 \cdot 0.35$$
Because the adjustment factor contains $(1.0 - v)^2$, the lift decays quadratically as luminance approaches midtones, and at $v = 0$, the lift slope remains gentle, preserving contrast depth and avoiding the milky gray shadows typical of naive gamma lifts.

### C. Soft HSL Skin-Tone Protection Mask
Human skin tones across all ethnicities fall within a consistent hue range ($[10^\circ, 45^\circ]$ in HSL space, with peak density near $26^\circ$):
$$w_{\text{skin}} = \exp\left(-\frac{(H - 26^\circ)^2}{2 \cdot 18^2}\right) \cdot S_{\text{factor}} \cdot L_{\text{factor}}$$
Where:
- $S_{\text{factor}} = \text{smoothstep}(0.12, 0.24, S) \cdot (1.0 - \text{smoothstep}(0.55, 0.68, S))$
- $L_{\text{factor}} = \text{smoothstep}(0.18, 0.28, L) \cdot (1.0 - \text{smoothstep}(0.78, 0.88, L))$

When $w_{\text{skin}} > 0.05$:
1. Saturation boost is attenuated: $\text{sat}' = \text{lerp}(\text{globalSat}, 1.0, w_{\text{skin}} \cdot 0.75)$.
2. Red-tinted faces ($H < 18^\circ$) receive a gentle warm peach correction: $H' = \text{lerp}(H, 26^\circ, w_{\text{skin}} \cdot 0.35)$.
3. Laplacian sharpening is reduced by $60\%$ to prevent accentuating pores, blemishes, or fine facial hair while preserving sharp hair and eyes.

---

## 4. Real-Time Viewfinder Sync & A/B Split Comparison

- In the live viewfinder, `GLViewfinderRenderer` applies the linear matrix, highlight shoulder, and shadow lift in real time via the fragment shader (`uNaturalModeEnabled`).
- When **A/B SPLIT** is toggled ON, photos developed by `NaturalPhotoProcessor` are saved with the left half showing the untouched camera capture (direct from sensor ISP) and the right half displaying the complete CAMELEO Natural pipeline, separated by a crisp 2-pixel amber dividing line (`#FFC83B`).

