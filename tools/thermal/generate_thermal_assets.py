#!/usr/bin/env python3
"""
Generates CAMELEO Thermal Vision assets:
1. Original 48x48 -> 192x192 pixel-art industrial camera icon (cam_thermal.png)
2. Smooth 256-step 5-palette verification ramp (thermal_palettes_ramp.png)
3. Curated sample preview JPEGs across all 5 palettes derived cleanly from internal samples
4. Quantitative palette calibration metrics JSON
"""

import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable"
CAL_DIR = ROOT / "docs" / "calibration" / "thermal"


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else (hi if v > hi else v)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = clamp((x - edge0) / max(1e-6, edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def catmull_rom(p0: float, p1: float, p2: float, p3: float, t: float) -> float:
    t2 = t * t
    t3 = t2 * t
    return 0.5 * (
        (2.0 * p1)
        + (-p0 + p2) * t
        + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
        + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
    )


def eval_spline_stops(stops, t: float):
    """Evaluates C1 smooth RGB spline over sorted (pos, (r,g,b)) control points."""
    t = clamp(t)
    if t <= stops[0][0]:
        return stops[0][1]
    if t >= stops[-1][0]:
        return stops[-1][1]

    idx = 0
    for i in range(len(stops) - 1):
        if stops[i][0] <= t <= stops[i + 1][0]:
            idx = i
            break

    t0, c1 = stops[idx]
    t1, c2 = stops[idx + 1]
    local_t = (t - t0) / max(1e-6, t1 - t0)
    c0 = stops[max(0, idx - 1)][1]
    c3 = stops[min(len(stops) - 1, idx + 2)][1]

    out = []
    for ch in range(3):
        v = catmull_rom(c0[ch], c1[ch], c2[ch], c3[ch], local_t)
        # Keep spline strictly bounded within neighborhood hull to prevent ringing/banding
        lo = min(c1[ch], c2[ch])
        hi = max(c1[ch], c2[ch])
        out.append(clamp(v, max(0.0, lo - 0.02), min(1.0, hi + 0.02)))
    return tuple(out)


def rgb_hex(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) / 255.0 for i in (0, 2, 4))


PALETTES = {
    "rainbow_thermal": [
        (0.00, rgb_hex("#12052C")),  # Deep purple cold
        (0.16, rgb_hex("#232EA8")),  # Royal cobalt blue
        (0.35, rgb_hex("#0AA8DC")),  # Luminous cyan
        (0.54, rgb_hex("#19C65E")),  # Emerald thermal green
        (0.74, rgb_hex("#F4E022")),  # Intense yellow
        (0.90, rgb_hex("#EF421A")),  # Hot orange-red
        (1.00, rgb_hex("#D91818")),  # Crisp red thermal peak
    ],
    "ironbow": [
        (0.00, rgb_hex("#06030E")),  # Void black
        (0.22, rgb_hex("#3E0C6E")),  # Deep violet-purple
        (0.45, rgb_hex("#9E1E5C")),  # Crimson magenta
        (0.66, rgb_hex("#DC4622")),  # Radiant red
        (0.84, rgb_hex("#FA920A")),  # Glowing orange
        (1.00, rgb_hex("#FFF79C")),  # Yellow hot core
    ],
    "red_hot": [
        (0.00, rgb_hex("#071538")),  # Abyssal cold blue
        (0.24, rgb_hex("#15458F")),  # Deep cold steel blue
        (0.46, rgb_hex("#2C639E")),  # Cool azure transition
        (0.64, rgb_hex("#8D2652")),  # Deep magenta-crimson transition (zero brown mud)
        (0.84, rgb_hex("#E32419")),  # Vivid red hot
        (1.00, rgb_hex("#FF6A45")),  # Bright red-hot core
    ],
    "white_hot": [
        (0.00, rgb_hex("#07080A")),
        (0.35, rgb_hex("#454950")),
        (0.70, rgb_hex("#A8ADB5")),
        (1.00, rgb_hex("#F8FAFC")),
    ],
    "black_hot": [
        (0.00, rgb_hex("#F8FAFC")),
        (0.30, rgb_hex("#A8ADB5")),
        (0.65, rgb_hex("#454950")),
        (1.00, rgb_hex("#07080A")),
    ],
}


def build_cam_thermal_icon(out_path: Path):
    """Constructs an original 48x48 pixel-art compact industrial thermal camera, scaled 4x -> 192x192."""
    W, H = 48, 48
    canvas = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    OUTLINE = (16, 18, 22, 255)
    CHARCOAL_TOP_HI = (78, 86, 100, 255)
    CHARCOAL_TOP = (56, 62, 74, 255)
    CHARCOAL_MID = (42, 47, 56, 255)
    CHARCOAL_DARK = (31, 35, 43, 255)
    CHARCOAL_DEEP = (23, 26, 33, 255)

    GRIP_DARK = (22, 24, 30, 255)
    GRIP_RIB = (35, 39, 47, 255)

    ORANGE_HI = (255, 166, 76, 255)
    ORANGE_MAIN = (255, 107, 26, 255)
    ORANGE_MID = (232, 82, 16, 255)
    ORANGE_SHADE = (182, 56, 8, 255)

    BEZEL_HI = (118, 127, 144, 255)
    BEZEL_MID = (76, 83, 97, 255)
    BEZEL_DARK = (46, 51, 61, 255)

    IR_DEEP = (16, 10, 28, 255)
    IR_PURPLE = (52, 26, 82, 255)
    IR_MAGENTA = (112, 42, 98, 255)
    IR_TEAL = (28, 88, 106, 255)
    IR_AMBER = (242, 118, 38, 255)
    IR_SPEC = (255, 228, 198, 255)

    def set_px(x: int, y: int, c):
        if 0 <= x < W and 0 <= y < H:
            canvas[y][x] = c

    # 1. Outer body mask: x in 4..43 (width 40), y in 12..39 (height 28) with rounded chamfer corners
    def in_body(x: int, y: int) -> bool:
        if x < 4 or x > 43 or y < 12 or y > 39:
            return False
        # 2px chamfer at top/bottom corners
        if (x == 4 or x == 43) and (y <= 13 or y >= 38):
            return False
        if (x == 5 or x == 42) and (y == 12 or y == 39):
            return False
        return True

    for y in range(H):
        for x in range(W):
            if in_body(x, y):
                # Stepped charcoal armor gradient
                if y == 13:
                    col = CHARCOAL_TOP_HI
                elif y <= 17:
                    col = CHARCOAL_TOP
                elif y <= 25:
                    col = CHARCOAL_MID
                elif y <= 34:
                    col = CHARCOAL_DARK
                else:
                    col = CHARCOAL_DEEP
                set_px(x, y, col)

    # Top tactile shutter trigger (left: x 8..14, y 9..11) — bright industrial orange
    for y in range(9, 12):
        for x in range(8, 15):
            if y == 9 and (x == 8 or x == 14):
                continue
            if y == 9:
                set_px(x, y, ORANGE_HI)
            elif x >= 13:
                set_px(x, y, ORANGE_SHADE)
            else:
                set_px(x, y, ORANGE_MAIN)

    # Top-right thermal palette mode wheel / heat sink plate (x 31..39, y 10..11)
    for y in range(10, 12):
        for x in range(31, 40):
            set_px(x, y, BEZEL_HI if (y == 10 and x % 2 == 1) else BEZEL_DARK)

    # Ergonomic left grip with tactile ribs + safety orange trim stripe (x 14)
    for y in range(15, 38):
        for x in range(5, 14):
            if x <= 12 and y >= 17 and y <= 36:
                set_px(x, y, GRIP_RIB if (y % 2 == 0) else GRIP_DARK)
        # Vertical orange seam highlight bar
        if 14 <= y <= 37:
            c = ORANGE_HI if y < 18 else (ORANGE_SHADE if y > 33 else ORANGE_MAIN)
            set_px(14, y, c)

    # Bottom industrial bumper accent bar (x 35..41, y 35..37)
    for x in range(36, 42):
        set_px(x, 35, ORANGE_HI if x < 40 else ORANGE_MAIN)
        set_px(x, 36, ORANGE_MID)

    # Upper optical alignment viewport / sensor window (x 35..40, y 15..20)
    for y in range(15, 21):
        for x in range(35, 41):
            if (x in (35, 40) and y in (15, 20)):
                set_px(x, y, CHARCOAL_DARK)
            elif x in (35, 40) or y in (15, 20):
                set_px(x, y, OUTLINE)
            elif x <= 37 and y <= 17:
                set_px(x, y, (98, 134, 168, 255))
            else:
                set_px(x, y, (18, 21, 28, 255))
    # Status amber LED next to sensor window
    set_px(32, 16, ORANGE_HI)
    set_px(33, 16, ORANGE_MAIN)

    # Recessed Germanium IR Lens Assembly centered at (26.5, 26.5)
    cx, cy = 26.5, 26.5
    for y in range(15, 39):
        for x in range(15, 39):
            dx = x - cx
            dy = y - cy
            r = math.hypot(dx, dy)
            if r <= 10.6:
                if r > 9.4:
                    # Outer rugged collar ring
                    col = OUTLINE
                elif r > 7.9:
                    # Machined charcoal-slate bevel ring
                    if dx + dy < -3.0:
                        col = BEZEL_HI
                    elif dx + dy > 4.0:
                        col = BEZEL_DARK
                    else:
                        col = BEZEL_MID
                elif r > 6.8:
                    # Signature orange thermal bezel ring
                    if dy < 0 and dx < 3.0:
                        col = ORANGE_MAIN
                    elif dx + dy < -4.0:
                        col = ORANGE_HI
                    else:
                        col = ORANGE_SHADE
                elif r > 5.7:
                    # Dark optical baffle recess
                    col = OUTLINE
                else:
                    # Germanium IR coated curved glass gradient
                    if r < 2.2 and dx <= 0 and dy <= 0:
                        col = IR_SPEC if (x == 24 and y == 24) else IR_AMBER
                    elif dx + dy < -1.5:
                        col = IR_TEAL if (x % 2 == 0 and dy < -1.0) else IR_MAGENTA
                    elif r < 3.5:
                        col = IR_PURPLE
                    else:
                        col = IR_DEEP
                set_px(x, y, col)

    # Generate crisp 1px dark outline around non-transparent body + top buttons
    final_grid = [[canvas[y][x] for x in range(W)] for y in range(H)]
    for y in range(H):
        for x in range(W):
            if canvas[y][x][3] == 0:
                # 4-connected outline look cleanly like cam_ir / cam_d3d
                touches_solid = False
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox, y + oy
                    if 0 <= nx < W and 0 <= ny < H and canvas[ny][nx][3] > 0:
                        touches_solid = True
                        break
                if touches_solid:
                    final_grid[y][x] = OUTLINE

    small = Image.new("RGBA", (W, H))
    spx = small.load()
    for y in range(H):
        for x in range(W):
            spx[x, y] = final_grid[y][x]

    icon = small.resize((192, 192), Image.Resampling.NEAREST)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out_path, format="PNG")
    return icon


def render_thermal_sample(
    src_path: Path,
    dst_path: Path,
    palette_key: str,
    add_portrait_heat_cluster: bool = False,
):
    """Generates clean 420x280 thermal preview card from internal repo photography."""
    stops = PALETTES[palette_key]
    im = Image.open(src_path).convert("RGB").resize((420, 280), Image.Resampling.BICUBIC)
    w, h = im.size
    px = im.load()
    out = Image.new("RGB", (w, h))
    opx = out.load()

    for y in range(h):
        ny = y / float(h)
        for x in range(w):
            nx = x / float(w)
            r, g, b = px[x, y]
            rn, gn, bn = r / 255.0, g / 255.0, b / 255.0
            luma = 0.2126 * rn + 0.7152 * gn + 0.0722 * bn
            warm_bias = clamp((rn - bn) * 0.16, -0.06, 0.12)

            if add_portrait_heat_cluster:
                # Emphasize human/foreground central cluster softly without erasing natural internal detail
                dx = (nx - 0.50) / 0.22
                dy = (ny - 0.56) / 0.34
                subj = max(0.0, 1.0 - (dx * dx + dy * dy))
                subj = subj * subj * (3.0 - 2.0 * subj)
                bg_cool = luma * 0.76 - 0.06
                fg_warm = 0.50 + luma * 0.44 + warm_bias
                heat = bg_cool * (1.0 - subj) + fg_warm * subj
            else:
                heat = (luma - 0.5) * 1.15 + 0.5 + warm_bias

            heat = clamp(heat)
            cr, cg, cb = eval_spline_stops(stops, heat)
            opx[x, y] = (
                int(round(cr * 255.0)),
                int(round(cg * 255.0)),
                int(round(cb * 255.0)),
            )

    dst_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(dst_path, format="JPEG", quality=92)


def main():
    icon = build_cam_thermal_icon(DRAWABLE_DIR / "cam_thermal.png")

    # Export visual 5-palette verification ramp (512 x 160) & check smoothness metrics
    ramp_w, row_h = 512, 32
    ramp_img = Image.new("RGB", (ramp_w, row_h * len(PALETTES)))
    rpx = ramp_img.load()
    metrics = {"palettes": {}}

    for row_idx, (pkey, stops) in enumerate(PALETTES.items()):
        samples = [eval_spline_stops(stops, i / 255.0) for i in range(256)]
        max_step = max(
            max(
                abs(samples[i][0] - samples[i - 1][0]),
                abs(samples[i][1] - samples[i - 1][1]),
                abs(samples[i][2] - samples[i - 1][2]),
            )
            for i in range(1, 256)
        )
        metrics["palettes"][pkey] = {
            "cold_rgb_8bit": [int(round(c * 255)) for c in samples[0]],
            "mid_rgb_8bit": [int(round(c * 255)) for c in samples[128]],
            "hot_rgb_8bit": [int(round(c * 255)) for c in samples[255]],
            "max_per_step_delta_norm": round(max_step, 5),
        }
        for x in range(ramp_w):
            rgb = eval_spline_stops(stops, x / float(ramp_w - 1))
            col = (int(round(rgb[0] * 255)), int(round(rgb[1] * 255)), int(round(rgb[2] * 255)))
            for yy in range(row_idx * row_h, (row_idx + 1) * row_h):
                rpx[x, yy] = col

    CAL_DIR.mkdir(parents=True, exist_ok=True)
    ramp_img.save(CAL_DIR / "thermal_palettes_ramp.png", format="PNG")
    with open(CAL_DIR / "thermal_calibration_metrics.json", "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)

    # Generate all 5 curated Thermal sample outputs from repo-internal photography
    src_garden = DRAWABLE_DIR / "sample_zero.jpg"
    src_station = DRAWABLE_DIR / "sample_analog_fxn_r_2.jpg"
    render_thermal_sample(src_garden, DRAWABLE_DIR / "sample_thermal_vision.jpg", "rainbow_thermal", False)
    render_thermal_sample(src_garden, DRAWABLE_DIR / "sample_thermal_ironbow.jpg", "ironbow", False)
    render_thermal_sample(src_station, DRAWABLE_DIR / "sample_thermal_redhot.jpg", "red_hot", False)
    render_thermal_sample(src_garden, DRAWABLE_DIR / "sample_thermal_whitehot.jpg", "white_hot", False)
    render_thermal_sample(src_station, DRAWABLE_DIR / "sample_thermal_blackhot.jpg", "black_hot", False)

    print("ICON:", icon.size, icon.getbbox())
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
