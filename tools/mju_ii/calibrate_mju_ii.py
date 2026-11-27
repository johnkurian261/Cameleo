#!/usr/bin/env python3
"""
Independently generated calibration & photometric verification suite for CAMELEO MJU II (analog_mju2).

Emulates the fixed-lens 1997 Olympus Mju II / Stylus Epic (35mm f/2.8 4-element glass lens)
paired with balanced 1990s 35mm consumer color-negative film, targeting:
- Strict neutral gray axis preservation (R_out == G_out == B_out)
- Smooth monotonic film S-curve (defined black toe, rich lower midtones, smooth highlight shoulder)
- Natural peach highlights, controlled bronze midtones, and preserved red-brown complexions (zero mustard/magenta)
- Natural olive-forest foliage, rich realistic skies, controlled warm reds, creamy whites
- Zero duplicate contrast/saturation stacking when combined with CAMELEO GLSL/CPU renderer
"""

import csv
import json
import math
import os
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
LUT_OUT = ROOT / "app" / "src" / "main" / "assets" / "lut_mju2.png"
CAL_DIR = ROOT / "docs" / "calibration" / "mju_ii"


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else (hi if v > hi else v)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = clamp((x - edge0) / max(1e-6, edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def mju2_luma_curve(x: float) -> float:
    """
    Monotonic nonlinear 35mm consumer print film transfer curve (LUT stage).
    Designed as the luminance backbone so bright highlights compress smoothly
    without distorting R/G/B chromaticity ratios on skin or brick reds.
    """
    x = clamp(x)
    # 1. Gentle S-curve (moderate ~1.08 photographic contrast)
    if x < 0.5:
        s = 0.5 * math.pow(2.0 * x, 1.08)
    else:
        s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), 1.08)

    # 2. Restrained lower-midtone sculptural richness (peaks smoothly near x=0.30, zero lift at x=0)
    lm_u = clamp((s - 0.04) / 0.58)
    lower_mid_lift = 0.016 * (4.0 * lm_u * (1.0 - lm_u)) ** 2 if 0.04 < s < 0.62 else 0.0

    # 3. Gentle high-key film shoulder above 0.84 (preserves white fabric & bright skin detail)
    shoulder_knee = 0.84
    if s > shoulder_knee:
        ex = s - shoulder_knee
        headroom = 1.0 - shoulder_knee
        s = shoulder_knee + headroom * (ex / (ex + headroom * 0.42))

    return clamp(s + lower_mid_lift)


def rgb_to_hue_deg(r: float, g: float, b: float) -> float:
    mx = max(r, g, b)
    mn = min(r, g, b)
    d = mx - mn
    if d < 1e-6:
        return 0.0
    if mx == r:
        h = 60.0 * (((g - b) / d) % 6.0)
    elif mx == g:
        h = 60.0 * (((b - r) / d) + 2.0)
    else:
        h = 60.0 * (((r - g) / d) + 4.0)
    return h % 360.0


def angular_bell(hue_deg: float, center_deg: float, half_width_deg: float) -> float:
    diff = abs((hue_deg - center_deg + 180.0) % 360.0 - 180.0)
    if diff >= half_width_deg:
        return 0.0
    t = diff / half_width_deg
    # Raised cosine bell: C1 smooth at zero and boundary
    return 0.5 * (1.0 + math.cos(math.pi * t))


def grade_analog_mju2(r: float, g: float, b: float):
    """
    MJU II (1997 Compact 35mm f/2.8 Glass + Consumer Color-Negative Film) LUT mapping.
    Strict invariants:
      - Neutral gray axis (r == g == b) -> R_out == G_out == B_out (0 chromatic drift).
      - C1-continuous hue and chroma gating (no piecewise seam banding).
      - Highlight compression preserves R/G/B ratios so bright skin highlights stay peachy, never yellow.
    """
    # Ratio-preserving luminance-blended tone curve (80% ratio-preserving max/luma + 20% gentle per-channel emulsion toe)
    l_in = 0.2126 * r + 0.7152 * g + 0.0722 * b
    if l_in > 1e-5:
        l_target = mju2_luma_curve(l_in)
        ratio = l_target / l_in
        # Keep highlights strictly below 1.0 while maintaining natural hue ratios
        mx_ratio_scaled = max(r * ratio, g * ratio, b * ratio)
        if mx_ratio_scaled > 0.992:
            ratio *= 0.992 / mx_ratio_scaled
        rr, gr, br = clamp(r * ratio), clamp(g * ratio), clamp(b * ratio)
    else:
        rr, gr, br = 0.0, 0.0, 0.0

    # Pure per-channel component in shadows/midtones AND strictly within near-neutral axis
    # so 64x64x64 Hald trilinear neighbor vertices near gray stay monotonically ordered.
    chroma_in = max(r, g, b) - min(r, g, b)
    neutral_blend = 1.0 - smoothstep(0.018, 0.095, chroma_in)
    hi_fade = 1.0 - smoothstep(0.65, 0.92, l_in)
    rp, gp, bp = mju2_luma_curve(r), mju2_luma_curve(g), mju2_luma_curve(b)
    blend_ch = max(0.22 * hi_fade, neutral_blend)
    rc = rr * (1.0 - blend_ch) + rp * blend_ch
    gc = gr * (1.0 - blend_ch) + gp * blend_ch
    bc = br * (1.0 - blend_ch) + bp * blend_ch

    luma = 0.2126 * rc + 0.7152 * gc + 0.0722 * bc
    mx = max(rc, gc, bc)
    mn = min(rc, gc, bc)
    chroma = mx - mn

    if chroma > 1e-5:
        c_gate = smoothstep(0.015, 0.14, chroma)
        hue = rgb_to_hue_deg(rc, gc, bc)

        # A. Skin & Warm Earth Locus (peach highlights + rich controlled bronze, preserved red-brown/blue)
        skin_bell = angular_bell(hue, 26.0, 24.0)
        if skin_bell > 0.0:
            hi_skin = smoothstep(0.50, 0.85, luma) * (1.0 - smoothstep(0.90, 0.99, luma))
            mid_skin = angular_bell(clamp(luma, 0.0, 1.0) * 180.0, 72.0, 56.0)
            w_skin = c_gate * skin_bell
            # Natural peach/warm-bronze lift while avoiding G-rise yellow cast
            rc += w_skin * (0.015 * hi_skin + 0.012 * mid_skin)
            gc += w_skin * (0.002 * hi_skin - 0.003 * mid_skin)
            bc += w_skin * (-0.002 * hi_skin + 0.002 * mid_skin)

        # B. Natural Olive & Forest Greens (temper synthetic neon leaf green -> organic 35mm foliage)
        foliage_bell = angular_bell(hue, 120.0, 40.0)
        if foliage_bell > 0.0:
            w_fol = c_gate * foliage_bell
            g_blend = gc * 0.955 + rc * 0.045
            mid_dens = math.sin(math.pi * clamp(luma))
            gc = gc * (1.0 - w_fol * 0.58) + g_blend * (w_fol * 0.58) - 0.012 * w_fol * mid_dens
            rc += 0.009 * w_fol * mid_dens
            bc -= 0.007 * w_fol * mid_dens

        # C. Realistic Blue Skies (clean cobalt/azure depth without heavy teal-cyan distortion)
        sky_bell = angular_bell(hue, 215.0, 36.0)
        if sky_bell > 0.0:
            w_sky = c_gate * sky_bell * smoothstep(0.18, 0.68, luma) * (1.0 - smoothstep(0.86, 0.99, luma))
            bc += 0.022 * w_sky
            rc -= 0.009 * w_sky
            gc -= 0.003 * w_sky

        # D. Controlled Warm Reds (brick/flowers/signs stay rich without channel blowout)
        red_bell = angular_bell(hue, 4.0, 22.0)
        if red_bell > 0.0:
            w_red = c_gate * red_bell
            sat_ratio = chroma / max(1e-4, mx)
            if sat_ratio > 0.68:
                rc -= 0.014 * w_red * smoothstep(0.68, 0.95, sat_ratio)
            gc += 0.004 * w_red * (1.0 - smoothstep(0.60, 0.92, luma))

        # E. Restrained Midtone Film Vibrance (tapers smoothly in blacks and specular whites)
        luma_post = 0.2126 * clamp(rc) + 0.7152 * clamp(gc) + 0.0722 * clamp(bc)
        mid_vib_gate = smoothstep(0.08, 0.26, luma_post) * (1.0 - smoothstep(0.76, 0.96, luma_post))
        sat_factor = 1.0 + 0.060 * c_gate * mid_vib_gate
        rc = luma_post + sat_factor * (rc - luma_post)
        gc = luma_post + sat_factor * (gc - luma_post)
        bc = luma_post + sat_factor * (bc - luma_post)

    return clamp(rc), clamp(gc), clamp(bc)


def simulate_cameleo_renderer_cpu(r_in: float, g_in: float, b_in: float, is_flash: bool = False):
    """Mirrors ImageProcessor.kt + ShaderConstants.kt unified SDR pipeline math on an input sRGB pixel."""
    r, g, b = grade_analog_mju2(r_in, g_in, b_in)
    # No duplicate contrast/saturation/temp/tint in MJU II FilmProfile
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    s_lift = 0.035 * (1.0 - clamp(luma)) ** 2
    r += s_lift
    g += s_lift
    b += s_lift

    roll_off = 0.89 if is_flash else 0.94
    max_c = max(r, g, b)
    if max_c > roll_off:
        d = max_c - roll_off
        s = 1.0 - roll_off
        rolled = roll_off + s * (d / (d + s))
        scale = rolled / max_c
        r *= scale
        g *= scale
        b *= scale
        luma2 = 0.2126 * r + 0.7152 * g + 0.0722 * b
        desat = clamp((luma2 - roll_off) / (1.0 - roll_off)) * 0.45
        r = r * (1.0 - desat) + luma2 * desat
        g = g * (1.0 - desat) + luma2 * desat
        b = b * (1.0 - desat) + luma2 * desat

    shoulder_clip = 0.992
    r = clamp(r * shoulder_clip)
    g = clamp(g * shoulder_clip)
    b = clamp(b * shoulder_clip)
    return r, g, b


def srgb_to_oklch(r: float, g: float, b: float):
    def to_lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    rl, gl, bl = to_lin(clamp(r)), to_lin(clamp(g)), to_lin(clamp(b))
    l = 0.4122214708 * rl + 0.5363325363 * gl + 0.0514459929 * bl
    m = 0.2119034982 * rl + 0.6806995451 * gl + 0.1073969566 * bl
    s = 0.0883024619 * rl + 0.2817188376 * gl + 0.6299787005 * bl
    l_ = l ** (1.0 / 3.0)
    m_ = m ** (1.0 / 3.0)
    s_ = s ** (1.0 / 3.0)
    L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    b_ = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    C = math.hypot(a, b_)
    h = (math.degrees(math.atan2(b_, a))) % 360.0
    return L, C, h


def generate_hald8_png(path: Path):
    img = Image.new("RGBA", (512, 512))
    px = img.load()
    for b_idx in range(64):
        tx = (b_idx % 8) * 64
        ty = (b_idx // 8) * 64
        b_in = b_idx / 63.0
        for g_idx in range(64):
            g_in = g_idx / 63.0
            y = ty + g_idx
            for r_idx in range(64):
                r_in = r_idx / 63.0
                x = tx + r_idx
                ro, go, bo = grade_analog_mju2(r_in, g_in, b_in)
                px[x, y] = (
                    max(0, min(255, int(round(clamp(ro) * 255.0)))),
                    max(0, min(255, int(round(clamp(go) * 255.0)))),
                    max(0, min(255, int(round(clamp(bo) * 255.0)))),
                    255,
                )
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG")
    return img


def run_calibration_and_reports():
    CAL_DIR.mkdir(parents=True, exist_ok=True)
    generate_hald8_png(LUT_OUT)

    # 1. Measure 256-step neutral transfer curve & monotonicity
    rows = []
    max_gray_channel_delta = 0.0
    monotonic = True
    prev_out = -1.0
    for idx in range(256):
        v = idx / 255.0
        lr, lg, lb = grade_analog_mju2(v, v, v)
        fr, fg, fb = simulate_cameleo_renderer_cpu(v, v, v, is_flash=False)
        ffr, ffg, ffb = simulate_cameleo_renderer_cpu(v, v, v, is_flash=True)
        delta_lut = max(abs(lr - lg), abs(lg - lb), abs(lr - lb))
        delta_final = max(abs(fr - fg), abs(fg - fb), abs(fr - fb))
        max_gray_channel_delta = max(max_gray_channel_delta, delta_lut, delta_final)
        if fr < prev_out - 1e-6:
            monotonic = False
        prev_out = fr
        rows.append({
            "input_8bit": idx,
            "input_norm": round(v, 5),
            "lut_only": round(lr, 5),
            "final_daylight": round(fr, 5),
            "final_flash": round(ffr, 5),
        })

    with open(CAL_DIR / "mju2_transfer_curve_256.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # 2. Measure Colorimetric Patches (Skin complexions + Environmental targets + Flash)
    patches = [
        ("skin_fair_peach_hi", (0.86, 0.71, 0.62), False),
        ("skin_medium_warm", (0.76, 0.56, 0.45), False),
        ("skin_olive_bronze", (0.64, 0.46, 0.35), False),
        ("skin_deep_red_brown", (0.42, 0.25, 0.19), False),
        ("skin_flash_highlight", (0.93, 0.78, 0.68), True),
        ("foliage_sunlit_green", (0.28, 0.56, 0.22), False),
        ("foliage_forest_shadow", (0.14, 0.30, 0.14), False),
        ("sky_clear_daylight", (0.29, 0.53, 0.85), False),
        ("warm_brick_red", (0.72, 0.20, 0.16), False),
        ("creamy_white_shirt", (0.94, 0.93, 0.91), False),
        ("neutral_asphalt_gray", (0.42, 0.42, 0.42), False),
    ]

    patch_metrics = {}
    for name, (ri, gi, bi), flash in patches:
        ro, go, bo = simulate_cameleo_renderer_cpu(ri, gi, bi, is_flash=flash)
        L_in, C_in, h_in = srgb_to_oklch(ri, gi, bi)
        L_out, C_out, h_out = srgb_to_oklch(ro, go, bo)
        hue_delta = (h_out - h_in + 180.0) % 360.0 - 180.0
        patch_metrics[name] = {
            "input_rgb": [round(ri * 255, 1), round(gi * 255, 1), round(bi * 255, 1)],
            "output_rgb": [round(ro * 255, 1), round(go * 255, 1), round(bo * 255, 1)],
            "oklch_in": [round(L_in, 4), round(C_in, 4), round(h_in, 2)],
            "oklch_out": [round(L_out, 4), round(C_out, 4), round(h_out, 2)],
            "hue_shift_deg": round(hue_delta, 2),
            "chroma_ratio": round(C_out / max(1e-5, C_in), 3) if C_in > 1e-3 else 1.0,
            "red_clipped": ro >= 0.996,
        }

    # 3. Sample image verification preview if repo sample_zero.jpg exists
    sample_src = ROOT / "app" / "src" / "main" / "res" / "drawable" / "sample_zero.jpg"
    sample_stats = {}
    if sample_src.exists():
        im = Image.open(sample_src).convert("RGB")
        # Create side-by-side verification comparison
        w, h = im.size
        src_small = im.resize((420, int(round(420 * h / w))), Image.BICUBIC)
        out_small = Image.new("RGB", src_small.size)
        sp = src_small.load()
        op = out_small.load()
        for py in range(src_small.height):
            for px_x in range(src_small.width):
                r8, g8, b8 = sp[px_x, py]
                # Apply cos^4 vignette subtly mirroring MJU II 0.16 intensity, 0.88 radius
                dx = (px_x / src_small.width - 0.5) / 0.5
                dy = (py / src_small.height - 0.5) / 0.5
                rn = math.hypot(dx, dy) / 1.25
                vig = 1.0 / ((1.0 + rn * rn * 0.45) ** 2)
                vig = 1.0 + (vig - 1.0) * (0.16 * 0.65)
                ro, go, bo = simulate_cameleo_renderer_cpu(r8 / 255.0, g8 / 255.0, b8 / 255.0, is_flash=False)
                op[px_x, py] = (
                    max(0, min(255, int(round(ro * vig * 255.0)))),
                    max(0, min(255, int(round(go * vig * 255.0)))),
                    max(0, min(255, int(round(bo * vig * 255.0)))),
                )
        comp = Image.new("RGB", (src_small.width * 2 + 12, src_small.height), (20, 20, 24))
        comp.paste(src_small, (0, 0))
        comp.paste(out_small, (src_small.width + 12, 0))
        comp.save(CAL_DIR / "mju2_vs_zero_sample_comparison.jpg", quality=92)
        out_small.save(ROOT / "app" / "src" / "main" / "res" / "drawable" / "sample_analog_mju2.jpg", quality=90)
        sample_stats = {"sample_generated": True, "size": list(out_small.size)}

    summary = {
        "lut_asset": "app/src/main/assets/lut_mju2.png",
        "lut_grid": "64x64x64 Hald-8 (512x512 RGBA)",
        "lut_input_space": "SRGB",
        "neutral_gray_monotonic": monotonic,
        "max_gray_channel_delta_norm": round(max_gray_channel_delta, 7),
        "black_point_0_out": rows[0]["final_daylight"],
        "shadow_16_out": rows[16]["final_daylight"],
        "midgray_128_out": rows[128]["final_daylight"],
        "highlight_230_out": rows[230]["final_daylight"],
        "white_255_out": rows[255]["final_daylight"],
        "white_255_flash_out": rows[255]["final_flash"],
        "colorimetric_patches": patch_metrics,
        "sample_preview": sample_stats,
    }
    with open(CAL_DIR / "mju2_calibration_metrics.json", "w") as f:
        json.dump(summary, f, indent=2)

    print(json.dumps({k: v for k, v in summary.items() if k != "colorimetric_patches"}, indent=2))
    for k, v in patch_metrics.items():
        print(f"PATCH {k}: in={v['input_rgb']} out={v['output_rgb']} hue_d={v['hue_shift_deg']} chroma_r={v['chroma_ratio']} clip={v['red_clipped']}")


if __name__ == "__main__":
    run_calibration_and_reports()
