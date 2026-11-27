#!/usr/bin/env python3
"""
Dedicated Calibration & Color Science Engine for CAMELEO Olympus MJU II (analog_mju2).

Aesthetic Target:
- Premium 1990s compact 35mm film camera (Olympus Mju II / Stylus Epic 35mm f/2.8)
- Direct flash photography aesthetic
- Creamy natural skin tones (warm beige, soft peach highlights)
- Rich green-black shadows and deep forest-green/olive undertones
- Muted olive and teal environmental backgrounds
- Rich black leather and clothing
- Clean creamy highlights with soft photographic compression
- Zero global green cast or skin contamination
"""

import csv
import hashlib
import json
import math
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
LUT_OUT = ROOT / "app" / "src" / "main" / "assets" / "lut_olympus_mju2.png"
CAL_DIR = ROOT / "docs" / "calibration" / "olympus_mju2"
ORIG_DIR = CAL_DIR / "originals"


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else (hi if v > hi else v)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = clamp((x - edge0) / max(1e-6, edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


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
    return 0.5 * (1.0 + math.cos(math.pi * t))


def srgb_to_linear(c: float) -> float:
    c = clamp(c)
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c: float) -> float:
    c = clamp(c)
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1.0 / 2.4)) - 0.055


def srgb_to_oklch(r: float, g: float, b: float):
    rl, gl, bl = srgb_to_linear(r), srgb_to_linear(g), srgb_to_linear(b)
    l = 0.4122214708 * rl + 0.5363325363 * gl + 0.0514459929 * bl
    m = 0.2119034982 * rl + 0.6806995451 * gl + 0.1073969566 * bl
    s = 0.0883024619 * rl + 0.2817188376 * gl + 0.6299787005 * bl

    l_ = l ** (1.0 / 3.0)
    m_ = m ** (1.0 / 3.0)
    s_ = s ** (1.0 / 3.0)

    L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    b_val = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    C = math.hypot(a, b_val)
    h_deg = math.degrees(math.atan2(b_val, a)) % 360.0
    return L, C, h_deg


def olympus_tone_curve(x: float, contrast: float = 1.15) -> float:
    """
    Punchy, high-definition 1990s compact film S-curve with deep black point,
    sculptural lower midtones, bright direct-flash upper midtones, and smooth creamy shoulder.
    """
    x = clamp(x)
    # Monotonic S-curve
    if x < 0.5:
        s = 0.5 * math.pow(2.0 * x, contrast)
    else:
        s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), contrast)

    # Highlight compression shoulder above 0.82
    shoulder_knee = 0.82
    if s > shoulder_knee:
        ex = s - shoulder_knee
        headroom = 1.0 - shoulder_knee
        s = shoulder_knee + headroom * (ex / (ex + headroom * 0.35))

    return clamp(s)


def grade_olympus_core(
    r: float,
    g: float,
    b: float,
    contrast: float = 1.15,
    shadow_green_strength: float = 0.024,
    olive_strength: float = 0.032,
    teal_strength: float = 0.028,
    skin_warmth: float = 0.016,
    skin_cream_hi: float = 0.018,
    highlight_cream: float = 0.014
):
    """
    Master Olympus MJU II Split-Tone Color Science.
    """
    l_in = 0.2126 * r + 0.7152 * g + 0.0722 * b
    if l_in > 1e-5:
        l_target = olympus_tone_curve(l_in, contrast)
        ratio = l_target / l_in
        mx_scaled = max(r * ratio, g * ratio, b * ratio)
        if mx_scaled > 0.992:
            ratio *= 0.992 / mx_scaled
        rc, gc, bc = clamp(r * ratio), clamp(g * ratio), clamp(b * ratio)
    else:
        rc, gc, bc = 0.0, 0.0, 0.0

    luma = 0.2126 * rc + 0.7152 * gc + 0.0722 * bc
    mx = max(rc, gc, bc)
    mn = min(rc, gc, bc)
    chroma = mx - mn
    hue = rgb_to_hue_deg(rc, gc, bc)

    # Detect skin locus (hue in 15°..55°, warm chroma)
    skin_bell = angular_bell(hue, 32.0, 24.0) if chroma > 0.015 else 0.0
    is_skin = skin_bell * smoothstep(0.02, 0.12, chroma) * smoothstep(0.18, 0.40, luma)

    # 1. DEEP SHADOW GREEN-BLACK SPLIT-TONING (Restrained to non-skin shadows)
    # Reaches peak intensity around luma ~ 0.08..0.18, vanishes strictly at pure black (luma=0) and midtones (luma>0.35)
    shadow_gate = smoothstep(0.01, 0.08, luma) * (1.0 - smoothstep(0.18, 0.38, luma))
    # Exclude warm skin from shadow greening
    shadow_green_factor = shadow_gate * (1.0 - is_skin) * shadow_green_strength

    if shadow_green_factor > 0.0:
        # Green-black undertone: boost green, slightly suppress red and blue
        gc += shadow_green_factor * 1.2
        rc -= shadow_green_factor * 0.7
        bc -= shadow_green_factor * 0.4

    # 2. LOWER MIDTONE OLIVE & TEAL FOLIAGE/ENVIRONMENT
    if chroma > 0.015:
        c_gate = smoothstep(0.015, 0.12, chroma)
        mid_gate = smoothstep(0.08, 0.22, luma) * (1.0 - smoothstep(0.55, 0.82, luma))

        # A. Olive environmental foliage (105°..135°)
        olive_bell = angular_bell(hue, 118.0, 36.0)
        if olive_bell > 0.0:
            w_ol = c_gate * olive_bell * mid_gate * olive_strength
            # Shift towards organic olive/forest green
            gc += w_ol * 0.4
            rc += w_ol * 0.3  # warm olive touch
            bc -= w_ol * 0.5

        # B. Nocturnal Teal & Glass reflection (175°..215°)
        teal_bell = angular_bell(hue, 192.0, 32.0)
        if teal_bell > 0.0:
            w_tl = c_gate * teal_bell * mid_gate * teal_strength
            bc += w_tl * 0.6
            gc += w_tl * 0.5
            rc -= w_tl * 0.8

        # C. Warm Peach & Creamy Skin Protection (20°..45°)
        if skin_bell > 0.0:
            w_sk = c_gate * skin_bell
            hi_sk = smoothstep(0.55, 0.88, luma) * (1.0 - smoothstep(0.92, 0.99, luma))
            mid_sk = smoothstep(0.25, 0.50, luma) * (1.0 - smoothstep(0.70, 0.90, luma))
            # Creamy beige highlights + peach warmth, zero green
            rc += w_sk * (skin_cream_hi * hi_sk + skin_warmth * mid_sk)
            gc += w_sk * (skin_cream_hi * 0.65 * hi_sk - skin_warmth * 0.15 * mid_sk)
            bc += w_sk * (skin_cream_hi * 0.20 * hi_sk - skin_warmth * 0.10 * mid_sk)

    # 3. CREAMY HIGHLIGHT CHROMA COMPRESSION
    if luma > 0.78:
        hi_t = smoothstep(0.78, 1.0, luma)
        # Gentle warm cream highlight tint
        rc += hi_t * highlight_cream
        gc += hi_t * (highlight_cream * 0.80)
        bc += hi_t * (highlight_cream * 0.50)
        # Soft desaturation of specular blowouts towards creamy white
        luma_post = 0.2126 * rc + 0.7152 * gc + 0.0722 * bc
        desat = hi_t * 0.35
        rc = rc * (1.0 - desat) + luma_post * desat
        gc = gc * (1.0 - desat) + luma_post * desat
        bc = bc * (1.0 - desat) + luma_post * desat

    return clamp(rc), clamp(gc), clamp(bc)


def grade_config_a(r: float, g: float, b: float):
    """Configuration A: Balanced Editorial Flash (Production Candidate)"""
    return grade_olympus_core(
        r, g, b,
        contrast=1.15,
        shadow_green_strength=0.024,
        olive_strength=0.032,
        teal_strength=0.028,
        skin_warmth=0.016,
        skin_cream_hi=0.018,
        highlight_cream=0.014
    )


def grade_config_b(r: float, g: float, b: float):
    """Configuration B: Green Nightlife Film (Stronger green shadows & cooler tones)"""
    return grade_olympus_core(
        r, g, b,
        contrast=1.18,
        shadow_green_strength=0.040,
        olive_strength=0.045,
        teal_strength=0.040,
        skin_warmth=0.014,
        skin_cream_hi=0.016,
        highlight_cream=0.012
    )


def grade_config_c(r: float, g: float, b: float):
    """Configuration C: Premium Compact Film (Subtle green shadow, more neutral)"""
    return grade_olympus_core(
        r, g, b,
        contrast=1.10,
        shadow_green_strength=0.012,
        olive_strength=0.018,
        teal_strength=0.016,
        skin_warmth=0.012,
        skin_cream_hi=0.014,
        highlight_cream=0.010
    )


def generate_hald8_png(path: Path, grade_fn=grade_config_a) -> Image.Image:
    """Generates standard 512x512 3D LUT (64x64x64 Hald)"""
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
                ro, go, bo = grade_fn(r_in, g_in, b_in)
                px[x, y] = (
                    max(0, min(255, int(round(clamp(ro) * 255.0)))),
                    max(0, min(255, int(round(clamp(go) * 255.0)))),
                    max(0, min(255, int(round(clamp(bo) * 255.0)))),
                    255,
                )
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG")
    return img


def apply_lut_to_image(img: Image.Image, grade_fn) -> Image.Image:
    w, h = img.size
    out = Image.new("RGB", (w, h))
    px_in = img.load()
    px_out = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px_in[x, y][:3]
            ro, go, bo = grade_fn(r / 255.0, g / 255.0, b / 255.0)
            px_out[x, y] = (
                int(round(clamp(ro) * 255.0)),
                int(round(clamp(go) * 255.0)),
                int(round(clamp(bo) * 255.0))
            )
    return out


def main():
    CAL_DIR.mkdir(parents=True, exist_ok=True)

    print("1. Generating Production 3D LUT (Config A)...")
    generate_hald8_png(LUT_OUT, grade_config_a)
    sha256 = hashlib.sha256(LUT_OUT.read_bytes()).hexdigest()
    print(f"   Production LUT saved: {LUT_OUT.name} (SHA-256: {sha256})")

    # Generate Experimental LUTs for ablations
    lut_a_path = CAL_DIR / "lut_config_a_balanced.png"
    lut_b_path = CAL_DIR / "lut_config_b_nightlife.png"
    lut_c_path = CAL_DIR / "lut_config_c_compact.png"
    generate_hald8_png(lut_a_path, grade_config_a)
    generate_hald8_png(lut_b_path, grade_config_b)
    generate_hald8_png(lut_c_path, grade_config_c)

    print("\n2. Evaluating 3-Way Ablations across Benchmark Scenes...")
    # Render test patches
    patches = [
        ("deep_shadow_black", (0.04, 0.04, 0.04)),
        ("shadow_charcoal", (0.10, 0.10, 0.10)),
        ("skin_creamy_fair", (0.88, 0.74, 0.66)),
        ("skin_warm_peach", (0.78, 0.58, 0.48)),
        ("skin_flash_highlight", (0.94, 0.82, 0.74)),
        ("olive_background", (0.32, 0.36, 0.20)),
        ("teal_background", (0.18, 0.32, 0.34)),
        ("creamy_white_shirt", (0.95, 0.94, 0.92))
    ]

    ablation_metrics = {}
    for name, (r, g, b) in patches:
        ra, ga, ba = grade_config_a(r, g, b)
        rb, gb, bb = grade_config_b(r, g, b)
        rc, gc, bc = grade_config_c(r, g, b)
        ablation_metrics[name] = {
            "input_rgb": [round(r * 255), round(g * 255), round(b * 255)],
            "config_a_balanced": [round(ra * 255), round(ga * 255), round(ba * 255)],
            "config_b_nightlife": [round(rb * 255), round(gb * 255), round(bb * 255)],
            "config_c_compact": [round(rc * 255), round(gc * 255), round(bc * 255)],
        }

    with open(CAL_DIR / "olympus_mju2_ablation_metrics.json", "w") as f:
        json.dump(ablation_metrics, f, indent=2)

    # Render visual contact sheets on reference images (resize to 360 wide for fast generation)
    print("3. Generating Visual Contact Sheets...")
    ref_files = [
        ("ref_2_leather", ORIG_DIR / "ref_2_leather_olive.jpg"),
        ("ref_3_portrait", ORIG_DIR / "ref_3_portrait_rings.jpg"),
        ("ref_4_night", ORIG_DIR / "ref_4_night_teal.jpg")
    ]

    for ref_name, ref_path in ref_files:
        if not ref_path.exists():
            continue
        im = Image.open(ref_path)
        # Scale to max dimension 480
        im.thumbnail((480, 480))
        w, h = im.size

        im_a = apply_lut_to_image(im, grade_config_a)
        im_b = apply_lut_to_image(im, grade_config_b)
        im_c = apply_lut_to_image(im, grade_config_c)

        # 4-up strip: Original | Config A (Selected) | Config B (Nightlife) | Config C (Compact)
        strip = Image.new("RGB", (w * 4, h + 30), (20, 20, 20))
        strip.paste(im, (0, 30))
        strip.paste(im_a, (w, 30))
        strip.paste(im_b, (w * 2, 30))
        strip.paste(im_c, (w * 3, 30))

        draw = ImageDraw.Draw(strip)
        draw.text((10, 8), f"Original: {ref_name}", fill=(220, 220, 220))
        draw.text((w + 10, 8), "Config A (Selected Production)", fill=(100, 255, 150))
        draw.text((w * 2 + 10, 8), "Config B (Green Nightlife)", fill=(180, 220, 180))
        draw.text((w * 3 + 10, 8), "Config C (Moderate Compact)", fill=(200, 200, 220))

        out_strip_path = CAL_DIR / f"ablation_comparison_{ref_name}.png"
        strip.save(out_strip_path)
        print(f"   Contact sheet saved: {out_strip_path.name}")

    print("\nCalibration and Ablation Suite Completed Successfully!")


if __name__ == "__main__":
    main()
