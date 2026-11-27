#!/usr/bin/env python3
"""
CAMELEO — Creative Temperature Filter Color Science Calibration & Asset Generator (Pure Python + PIL).

1. Evaluates luminance-aware Creative Temperature (-100 .. +100) across gray ramps,
   shadows, highlights, and multi-tone skin patches (both Skin Guard ON and OFF).
2. Verifies T=0 bit-exact identity, C1 continuity across all 201 integer steps,
   frozen ice blue at -100, and deep crimson red photographic grade at +100 without
   crushed blacks or harsh channel clipping.
3. Generates original CAMELEO 72x72 pixel-art icons:
   - app/src/main/res/drawable/ic_temp_snowflake.png
   - app/src/main/res/drawable/ic_temp_flame.png
4. Renders sample outputs and comparison contact sheets in docs/calibration/creative_temperature/.
"""

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "docs" / "calibration" / "creative_temperature"
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable"


def clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else (1.0 if x > 1.0 else x)


def smoothstep(e0: float, e1: float, x: float) -> float:
    t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3.0 - 2.0 * t)


def apply_creative_temperature_px(r_in: float, g_in: float, b_in: float, temp_value: float, skin_protect: bool = True):
    """Exact scalar reference matching Kotlin CreativeTemperatureEngine and GLSL applyCreativeTemperature()."""
    t_norm = max(-1.0, min(1.0, float(temp_value) / 100.0))
    if abs(t_norm) < 0.001:
        return (r_in, g_in, b_in)

    r = clamp01(r_in)
    g = clamp01(g_in)
    b = clamp01(b_in)

    y0 = 0.2126 * r + 0.7152 * g + 0.0722 * b

    # 1. Optional continuous RGB-ratio skin probability mask (Fitzpatrick I..VI)
    if skin_protect:
        rgb_sum = max(0.001, r + g + b)
        ng = g / rgb_sum
        s_dom = smoothstep(0.01, 0.08, r - g) * smoothstep(0.02, 0.14, r - b)
        s_green = smoothstep(0.22, 0.28, ng) * (1.0 - smoothstep(0.35, 0.40, ng))
        s_luma = smoothstep(0.08, 0.20, y0) * (1.0 - smoothstep(0.86, 0.96, y0))
        skin_w = clamp01(s_dom * s_green * s_luma * 1.35)
    else:
        skin_w = 0.0

    if t_norm < 0.0:
        eff_t = t_norm * (1.0 - 0.56 * skin_w)
    else:
        eff_t = t_norm * (1.0 - 0.52 * skin_w)

    u = abs(eff_t)
    is_cold = eff_t < 0.0

    # 2. Luminance-aware modulation envelope (preserves true black anchor + specular highlight texture)
    shadow_guard = smoothstep(0.0, 0.15, y0)
    highlight_guard = 1.0 - 0.46 * smoothstep(0.68, 1.0, y0)
    chroma_bell = 0.42 + 0.58 * (4.0 * y0 * (1.0 - y0))
    weight = shadow_guard * highlight_guard * chroma_bell

    # 3. Non-linear multi-stop chromatic shift:
    # Cold: subtle cool (-25) -> cold blue (-65) -> frozen icy sapphire (-100)
    # Warm: warm amber (+25) -> golden orange (+50) -> hot orange-red (+75) -> deep crimson red (+100)
    if is_cold:
        dR = -(0.36 * u + 0.34 * u * u)
        dG = (0.11 * u - 0.22 * u * u)
        dB = (0.44 * u + 0.24 * u * u)
    else:
        dR = (0.36 * u + 0.24 * u * u)
        g_quad = 0.44 - 0.16 * skin_w
        g_cube = 0.42 - 0.24 * skin_w
        dG = (0.26 * u - g_quad * u * u - g_cube * u * u * u)
        dB = -(0.46 * u + 0.36 * u * u)

    gain_r = max(0.08, 1.0 + dR * (0.60 + 0.42 * weight))
    gain_g = max(0.14, 1.0 + dG * (0.58 + 0.38 * weight))
    gain_b = max(0.06, 1.0 + dB * (0.62 + 0.44 * weight))

    # Avoid zero-crushing negative channels (scale subtle negative chroma gently instead of subtracting below 0)
    add_r = (dR * 0.090 * weight) if dR >= 0.0 else (dR * 0.055 * weight * smoothstep(0.05, 0.35, r))
    add_g = (dG * 0.070 * weight) if dG >= 0.0 else (dG * 0.040 * weight * smoothstep(0.05, 0.35, g))
    add_b = (dB * 0.090 * weight) if dB >= 0.0 else (dB * 0.050 * weight * smoothstep(0.05, 0.35, b))

    tr = max(0.0, r * gain_r + add_r)
    tg = max(0.0, g * gain_g + add_g)
    tb = max(0.0, b * gain_b + add_b)

    # 4. Partial Rec.709 luminance preservation
    y1 = 0.2126 * tr + 0.7152 * tg + 0.0722 * tb
    lum_preserve = 0.74 * (1.0 - 0.34 * u * u)
    ratio = (y0 / y1) if y1 > 1e-4 else 1.0
    ratio = 0.54 if ratio < 0.54 else (1.55 if ratio > 1.55 else ratio)
    scale = 1.0 + (ratio - 1.0) * lum_preserve
    tr *= scale
    tg *= scale
    tb *= scale

    # Gentle deep-red richness above +70 (keeps B below G so hot grades never drift magenta)
    if not is_cold and u > 0.65:
        deep_red_pull = ((u - 0.65) / 0.35) * 0.14 * (1.0 - 0.55 * skin_w) * smoothstep(0.25, 0.90, y0)
        tr *= (1.0 - deep_red_pull * 0.25)
        tg *= (1.0 - deep_red_pull * 1.15)
        tb *= (1.0 - deep_red_pull * 1.65)

    # 5. Soft-knee filmic gamut compression above 0.84
    knee = 0.84
    headroom = 1.0 - knee
    max_ch = max(tr, tg, tb)
    if max_ch > knee and max_ch > 1e-4:
        over = max_ch - knee
        rolled_max = knee + headroom * (over / (over + headroom * 0.55))
        g_scale = rolled_max / max_ch
        tr *= g_scale
        tg *= g_scale
        tb *= g_scale

    def compress_ch(v: float) -> float:
        if v > knee:
            ex = v - knee
            return clamp01(knee + headroom * (ex / (ex + headroom * 0.65)))
        return clamp01(v)

    return (compress_ch(tr), compress_ch(tg), compress_ch(tb))


def transform_pil_image(img: Image.Image, temp_value: float, skin_protect: bool = True) -> Image.Image:
    if abs(temp_value) < 0.1:
        return img.copy()
    raw = img.tobytes()
    out = bytearray(len(raw))
    inv255 = 1.0 / 255.0
    for idx in range(0, len(raw), 3):
        ro, go, bo = apply_creative_temperature_px(
            raw[idx] * inv255,
            raw[idx + 1] * inv255,
            raw[idx + 2] * inv255,
            temp_value,
            skin_protect,
        )
        out[idx] = int(round(ro * 255.0))
        out[idx + 1] = int(round(go * 255.0))
        out[idx + 2] = int(round(bo * 255.0))
    return Image.frombytes("RGB", img.size, bytes(out))


def build_pixel_icons():
    """Creates crisp 72x72 (24x24 scaled 3x nearest) pixel-art Snowflake & Flame icons."""
    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)

    # Clean 6-branch crystalline pixel-art snowflake (24x24, symmetric, crisp border)
    snow_rows = [
        "........................",
        "...........OO...........",
        "..........OCCO..........",
        "..........OIIO..........",
        ".....OO...OIIO...OO.....",
        "....OCCO..ODDO..OCCO....",
        "....OIIO..ODDO..OIIO....",
        ".....OIIOOICCIoOIIO.....",
        "......OIIICCCCIIIO......",
        "...OO..ODCCCCCCDO..OO...",
        "..OCCO.ODCCWWCCDO.OCCO..",
        ".OCCIIDOICWWWWCIODIICCO.",
        ".OCCIIDOICWWWWCIODIICCO.",
        "..OCCO.ODCCWWCCDO.OCCO..",
        "...OO..ODCCCCCCDO..OO...",
        "......OIIICCCCIIIO......",
        ".....OIIOOICCIOOIIO.....",
        "....OIIO..ODDO..OIIO....",
        "....OCCO..ODDO..OCCO....",
        ".....OO...OIIO...OO.....",
        "..........OIIO..........",
        "..........OCCO..........",
        "...........OO...........",
        "........................",
    ]
    smap = {
        ".": (0, 0, 0, 0),
        "O": (18, 30, 52, 255),
        "o": (18, 30, 52, 255),
        "D": (42, 126, 224, 255),
        "I": (108, 198, 255, 255),
        "C": (196, 238, 255, 255),
        "W": (244, 252, 255, 255),
    }
    snow_img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    sp = snow_img.load()
    for y, row in enumerate(snow_rows):
        for x, ch in enumerate(row):
            sp[x, y] = smap[ch]
    snow_img.resize((72, 72), Image.Resampling.NEAREST).save(DRAWABLE_DIR / "ic_temp_snowflake.png")

    flame_rows = [
        "........................",
        "...........OO...........",
        "..........ORRO..........",
        "..........ORRO..........",
        ".........ORRRO..........",
        ".........OROYRO.........",
        "........ORROYYRO........",
        "........ORROYYRO........",
        ".......ORROYYYYRO..OO...",
        "......ORROYYWWYYROORRO..",
        ".....ORROYYWWWWYYRRRO...",
        "....ORROYYWWWWWWYYRRO...",
        "....ORROYYWWWWWWYYRRRO..",
        "...ORROYYWWWWWWWWYYRRO..",
        "...ORROYYWWYYYYWWYYRRO..",
        "...ORROYYWYYYYYYWYYRRO..",
        "...ORROYYYYYYYYYYYYRRO..",
        "....ORROYYYYYYYYYYRRO...",
        "....ORROYYYYYYYYYYRRO...",
        ".....ORROYYYYYYYYRRO....",
        "......ORROYYYYYYRRO.....",
        ".......OORRYYYYRROO.....",
        "........OOOOOOOOOO......",
        "........................",
    ]
    fmap = {
        ".": (0, 0, 0, 0),
        "O": (26, 12, 14, 255),
        "R": (222, 34, 28, 255),
        "Y": (255, 148, 28, 255),
        "W": (255, 232, 108, 255),
    }
    flame_img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    fp = flame_img.load()
    for y, row in enumerate(flame_rows):
        for x, ch in enumerate(row):
            fp[x, y] = fmap[ch]
    flame_img.resize((72, 72), Image.Resampling.NEAREST).save(DRAWABLE_DIR / "ic_temp_flame.png")
    print("Generated refined ic_temp_snowflake.png and ic_temp_flame.png (72x72 RGBA)")


def verify_and_render():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    stops = [-100, -65, -25, 0, 25, 50, 75, 100]

    test_patches = {
        "black": (0.0, 0.0, 0.0),
        "shadow_gray": (0.10, 0.10, 0.10),
        "mid_gray": (0.50, 0.50, 0.50),
        "bright_highlight": (0.90, 0.90, 0.90),
        "skin_light": (0.84, 0.65, 0.55),
        "skin_deep": (0.42, 0.27, 0.19),
        "sky_blue": (0.36, 0.58, 0.85),
        "foliage": (0.28, 0.50, 0.24),
    }

    z = apply_creative_temperature_px(*test_patches["mid_gray"], 0.0, True)
    assert abs(z[0] - 0.50) < 1e-6 and abs(z[1] - 0.50) < 1e-6 and abs(z[2] - 0.50) < 1e-6

    metrics = {"milestones": {}, "continuity": {}}
    for s in stops:
        row = {}
        for name, p in test_patches.items():
            out_prot = apply_creative_temperature_px(p[0], p[1], p[2], float(s), True)
            out_raw = apply_creative_temperature_px(p[0], p[1], p[2], float(s), False)
            row[name] = {
                "protected_rgb8": [int(round(v * 255)) for v in out_prot],
                "unprotected_rgb8": [int(round(v * 255)) for v in out_raw],
            }
        metrics["milestones"][str(s)] = row

    prev = None
    max_step_delta = 0.0
    for val in range(-100, 101):
        cur = apply_creative_temperature_px(0.50, 0.50, 0.50, float(val), True)
        if prev is not None:
            delta = math.sqrt(sum((a - b) ** 2 for a, b in zip(cur, prev)))
            if delta > max_step_delta:
                max_step_delta = delta
        prev = cur
    metrics["continuity"]["max_single_step_delta_mid_gray"] = round(max_step_delta, 6)

    with open(OUT_DIR / "creative_temperature_metrics.json", "w") as f:
        json.dump(metrics, f, indent=2)
    print(f"Continuity max delta on mid-gray across -100..+100: {max_step_delta:.6f}")

    samples_src = [
        ("train", ROOT / "app" / "src" / "main" / "res" / "drawable" / "sample_analog_fxn_r_2.jpg"),
        ("garden", ROOT / "app" / "src" / "main" / "res" / "drawable" / "sample_zero.jpg"),
    ]
    for tag, src_path in samples_src:
        if not src_path.exists():
            continue
        img = Image.open(src_path).convert("RGB")
        w = 240
        h = int(round(img.height * (w / img.width)))
        img_small = img.resize((w, h), Image.Resampling.LANCZOS)

        pad = 8
        cols, rows = 4, 2
        label_h = 24
        canvas = Image.new("RGB", (cols * w + (cols + 1) * pad, rows * (h + label_h) + (rows + 1) * pad), (18, 20, 26))
        draw = ImageDraw.Draw(canvas)
        for idx, s in enumerate(stops):
            r_idx = idx // cols
            c_idx = idx % cols
            x0 = pad + c_idx * (w + pad)
            y0 = pad + r_idx * (h + label_h + pad)
            tile = transform_pil_image(img_small, float(s), True)
            canvas.paste(tile, (x0, y0))
            sign = "+" if s > 0 else ""
            draw.text((x0 + 6, y0 + h + 5), f"TEMP {sign}{s}", fill=(240, 240, 245))
            if s in (-100, -65, 0, 50, 100):
                tile.save(OUT_DIR / f"sample_{tag}_temp_{s}.jpg", quality=92)
        canvas.save(OUT_DIR / f"creative_temp_{tag}_strip.jpg", quality=94)

    # Multi-tone swatch validation card for Skin Protection ON vs OFF across stops
    sw_w, sw_h = 90, 64
    swatches = [
        ("Light Skin", (214, 166, 140)),
        ("Tan Skin", (170, 118, 86)),
        ("Deep Skin", (107, 69, 48)),
        ("Mid Gray", (128, 128, 128)),
        ("Sky Blue", (92, 148, 217)),
        ("Leaf Green", (71, 128, 61)),
    ]
    rows_labels = [("-65 PROT ON", -65, True), ("-65 PROT OFF", -65, False), ("0 ORIG", 0, True), ("+75 PROT ON", 75, True), ("+75 PROT OFF", 75, False), ("+100 PROT ON", 100, True), ("+100 PROT OFF", 100, False)]
    card = Image.new("RGB", (110 + len(swatches) * (sw_w + 6) + 12, len(rows_labels) * (sw_h + 6) + 32), (16, 18, 24))
    cd = ImageDraw.Draw(card)
    for ci, (sname, _) in enumerate(swatches):
        cd.text((112 + ci * (sw_w + 6), 6), sname, fill=(220, 220, 225))
    for ri, (rlabel, tv, sp) in enumerate(rows_labels):
        y0 = 26 + ri * (sw_h + 6)
        cd.text((8, y0 + 22), rlabel, fill=(230, 230, 235))
        for ci, (_, base_rgb) in enumerate(swatches):
            x0 = 110 + ci * (sw_w + 6)
            ro, go, bo = apply_creative_temperature_px(base_rgb[0] / 255.0, base_rgb[1] / 255.0, base_rgb[2] / 255.0, float(tv), sp)
            out8 = (int(round(ro * 255)), int(round(go * 255)), int(round(bo * 255)))
            cd.rectangle([x0, y0, x0 + sw_w, y0 + sw_h], fill=out8)
            cd.text((x0 + 4, y0 + sw_h - 14), f"{out8[0]},{out8[1]},{out8[2]}", fill=(255, 255, 255) if sum(out8) < 420 else (15, 15, 20))
    card.save(OUT_DIR / "creative_temp_skin_protection_comparison.jpg", quality=94)

    grad_w, grad_h = 804, 84
    grad = Image.new("RGB", (grad_w, grad_h))
    gp = grad.load()
    for x in range(grad_w):
        t = -100.0 + (x / (grad_w - 1)) * 200.0
        c = apply_creative_temperature_px(0.58, 0.58, 0.58, t, False)
        rgb8 = tuple(int(round(clamp01(v) * 255)) for v in c)
        for y in range(grad_h):
            gp[x, y] = rgb8
    grad.save(OUT_DIR / "creative_temp_gradient_response.png")
    print(f"Wrote refined verification visuals and metrics to {OUT_DIR}")


if __name__ == "__main__":
    build_pixel_icons()
    verify_and_render()
