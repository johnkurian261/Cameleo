import os, math
from PIL import Image

# ─── Film Mathematics Engine ──────────────────────────────────────────────────

def clamp(v, low=0.0, high=1.0):
    return max(low, min(high, v))

def film_s_curve(x, contrast=1.2, toe=0.04, shoulder=0.90):
    x = clamp(x)
    # S-curve power formulation
    if x < 0.5:
        s = 0.5 * math.pow(2.0 * x, contrast)
    else:
        s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), contrast)
    # Shoulder roll-off
    if s > shoulder:
        delta = s - shoulder
        s = shoulder + delta * 0.45
    # Lift blacks (toe)
    return toe + s * (1.0 - toe)

def adjust_saturation(r, g, b, sat):
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return (
        luma + sat * (r - luma),
        luma + sat * (g - luma),
        luma + sat * (b - luma)
    )

def rgb_to_hsl(r, g, b):
    r, g, b = clamp(r), clamp(g), clamp(b)
    cmax = max(r, g, b)
    cmin = min(r, g, b)
    delta = cmax - cmin
    l = (cmax + cmin) / 2.0
    if delta == 0:
        h = 0.0
        s = 0.0
    else:
        s = delta / (1.0 - abs(2.0 * l - 1.0)) if (1.0 - abs(2.0 * l - 1.0)) > 1e-6 else 0.0
        if cmax == r:
            h = ((g - b) / delta) % 6.0
        elif cmax == g:
            h = ((b - r) / delta) + 2.0
        else:
            h = ((r - g) / delta) + 4.0
        h *= 60.0
    return h, s, l

def hsl_to_rgb(h, s, l):
    h = h % 360.0
    c = (1.0 - abs(2.0 * l - 1.0)) * s
    x = c * (1.0 - abs((h / 60.0) % 2.0 - 1.0))
    m = l - c / 2.0
    if 0 <= h < 60:
        r, g, b = c, x, 0
    elif 60 <= h < 120:
        r, g, b = x, c, 0
    elif 120 <= h < 180:
        r, g, b = 0, c, x
    elif 180 <= h < 240:
        r, g, b = 0, x, c
    elif 240 <= h < 300:
        r, g, b = x, 0, c
    else:
        r, g, b = c, 0, x
    return r + m, g + m, b + m

# ─── 21 DISTINCT FILM RECIPES ────────────────────────────────────────────────

# 1. 35mm Classic (D Classic / Kodak Portra 400)
# Iconic warm peach skin tones, golden sunlight, deep cyan/teal shadows
def grade_analog_35mm_classic(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.03, shoulder=0.88)
    g = film_s_curve(g, contrast=1.24, toe=0.04, shoulder=0.88)
    b = film_s_curve(b, contrast=1.16, toe=0.06, shoulder=0.92)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    sh = clamp(1.0 - luma * 2.5)
    r -= 0.08 * sh
    g += 0.03 * sh
    b += 0.12 * sh  # Deep cyan-teal shadows
    hi = clamp((luma - 0.45) * 1.8)
    r += 0.12 * hi  # Warm golden highlights
    g += 0.05 * hi
    b -= 0.08 * hi
    r, g, b = adjust_saturation(r, g, b, 1.25)
    return r, g, b

# 2. Warm Film (Kodak Gold 200 / Amber Emulsion)
# Heavy late-afternoon golden glow across the entire image
def grade_analog_warm_film(r, g, b):
    r = film_s_curve(r, contrast=1.22, toe=0.04, shoulder=0.85)
    g = film_s_curve(g, contrast=1.18, toe=0.03, shoulder=0.87)
    b = film_s_curve(b, contrast=1.10, toe=0.02, shoulder=0.92)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Amber/copper shift everywhere
    r = 1.05 * r + 0.10 * (1.0 - b)
    g = 1.02 * g + 0.04 * (1.0 - b)
    b = 0.78 * b
    r, g, b = adjust_saturation(r, g, b, 1.30)
    return r, g, b

# 3. Soft Film (Fuji Provia 100F Slide / Pentax 67)
# Crisp medium format slide film: rich emerald greens, deep cobalt blues, cool magenta shadow bias
def grade_analog_soft_film(r, g, b):
    r = film_s_curve(r, contrast=1.30, toe=0.02, shoulder=0.92)
    g = film_s_curve(g, contrast=1.28, toe=0.02, shoulder=0.92)
    b = film_s_curve(b, contrast=1.32, toe=0.03, shoulder=0.94)
    # Emerald green pop
    if g > r and g > b:
        g = clamp(g * 1.18)
        r = clamp(r * 0.90)
    # Cobalt sky pop
    if b > r and b > g:
        b = clamp(b * 1.20)
    # Magenta shadow bias
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    sh = clamp(1.0 - luma * 3.0)
    r += 0.06 * sh
    g -= 0.02 * sh
    b += 0.08 * sh
    r, g, b = adjust_saturation(r, g, b, 1.25)
    return r, g, b

# 4. Vintage Color (D Exp / 1970s Kodachrome Chromogenic)
# High contrast, lifted warm mustard shadows, earthy olive greens, tomato reds
def grade_analog_vintage_color(r, g, b):
    r = film_s_curve(r, contrast=1.38, toe=0.08, shoulder=0.82)
    g = film_s_curve(g, contrast=1.30, toe=0.07, shoulder=0.85)
    b = film_s_curve(b, contrast=1.18, toe=0.05, shoulder=0.90)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # 70s Earthy dye shifts
    r = 0.94 * r + 0.12 * g
    g = 0.04 * r + 0.96 * g
    b = 0.08 * r + 0.76 * b
    r, g, b = adjust_saturation(r, g, b, 1.32)
    return r, g, b

# 5. Faded Film (NT16 / 1968 Faded Paper Negative)
# Milky lifted shadows (toe 0.16), muted dusty pastels, tea-stained vintage haze
def grade_analog_faded_film(r, g, b):
    r = film_s_curve(r, contrast=0.88, toe=0.16, shoulder=0.78)
    g = film_s_curve(g, contrast=0.85, toe=0.15, shoulder=0.80)
    b = film_s_curve(b, contrast=0.82, toe=0.14, shoulder=0.82)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Sepia / tea stain midtones
    mid = clamp(1.0 - abs(luma - 0.5) * 2.0)
    r += 0.08 * mid
    g += 0.05 * mid
    b -= 0.05 * mid
    r, g, b = adjust_saturation(r, g, b, 0.70)
    return r, g, b

# 6. Cinema Film (D3D / 35mm Hollywood Kodak Vision3 500T)
# Aggressive blockbuster Teal & Orange split tone with rich deep blacks
def grade_analog_cinema_film(r, g, b):
    r = film_s_curve(r, contrast=1.32, toe=0.03, shoulder=0.88)
    g = film_s_curve(g, contrast=1.26, toe=0.03, shoulder=0.89)
    b = film_s_curve(b, contrast=1.22, toe=0.05, shoulder=0.90)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Heavy teal in shadows
    sh = clamp(1.0 - luma * 2.2)
    r -= 0.12 * sh
    g += 0.04 * sh
    b += 0.18 * sh
    # Intense warm amber in highlights
    hi = clamp((luma - 0.40) * 1.8)
    r += 0.18 * hi
    g += 0.06 * hi
    b -= 0.14 * hi
    r, g, b = adjust_saturation(r, g, b, 1.28)
    return r, g, b

# 7. CCD Classic (135 NE / Early 2000s Compact Digicam)
# High digital contrast, crushed blacks, metallic cool shadows, electric blue skies
def grade_ccd_classic(r, g, b):
    r = film_s_curve(r, contrast=1.36, toe=0.01, shoulder=0.96)
    g = film_s_curve(g, contrast=1.34, toe=0.01, shoulder=0.96)
    b = film_s_curve(b, contrast=1.38, toe=0.02, shoulder=0.98)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Cool metallic shadow cast
    sh = clamp(1.0 - luma * 3.0)
    r -= 0.04 * sh
    g += 0.01 * sh
    b += 0.08 * sh
    # Vivid digital sky
    if b > r:
        b = clamp(b * 1.25)
    r, g, b = adjust_saturation(r, g, b, 1.35)
    return r, g, b

# 8. CCD Flash (D FunS / Night Strobe Party Cam)
# Harsh xenon strobe pop: high microcontrast, blown highlights, pitch-black warm background
def grade_ccd_flash(r, g, b):
    r = film_s_curve(r, contrast=1.45, toe=0.02, shoulder=0.94)
    g = film_s_curve(g, contrast=1.42, toe=0.01, shoulder=0.94)
    b = film_s_curve(b, contrast=1.35, toe=0.01, shoulder=0.92)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Bright flash illumination
    if luma > 0.35:
        pop = (luma - 0.35) * 1.6
        r += 0.10 * pop
        g += 0.05 * pop
    # Deep warm shadows
    sh = clamp(1.0 - luma * 3.0)
    r += 0.06 * sh
    r, g, b = adjust_saturation(r, g, b, 1.30)
    return r, g, b

# 9. CCD Cool Digital (IR / False-Color Aerochrome & Cyber Y2K)
# Surreal false-color: foliage turns vivid scarlet/magenta, skies turn deep navy indigo
def grade_ccd_cool_digital(r, g, b):
    r = film_s_curve(r, contrast=1.35, toe=0.02, shoulder=0.92)
    g = film_s_curve(g, contrast=1.32, toe=0.02, shoulder=0.92)
    b = film_s_curve(b, contrast=1.38, toe=0.03, shoulder=0.95)
    # Aerochrome IR effect: Greens become hot pink / red
    if g > r * 0.85 and g > b * 0.85:
        orig_g = g
        r = clamp(orig_g * 1.65)
        g = clamp(orig_g * 0.25)
        b = clamp(b * 0.80 + orig_g * 0.35)
    else:
        # Cool cyber cyan-blue tone
        r = clamp(r * 0.88 - 0.05)
        g = clamp(g * 0.98 + 0.02)
        b = clamp(b * 1.15 + 0.08)
    r, g, b = adjust_saturation(r, g, b, 1.35)
    return r, g, b

# 10. CCD Y2K Digital (Classic U / MySpace Glamour)
# Saturated magenta-pink highlights, dreamy skin glow, crunchy early-digital contrast
def grade_ccd_y2k_digital(r, g, b):
    r = film_s_curve(r, contrast=1.30, toe=0.02, shoulder=0.95)
    g = film_s_curve(g, contrast=1.24, toe=0.02, shoulder=0.95)
    b = film_s_curve(b, contrast=1.28, toe=0.03, shoulder=0.96)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Pink-magenta glamour cast
    hi = clamp((luma - 0.30) * 1.6)
    r += 0.14 * hi
    g -= 0.03 * hi
    b += 0.10 * hi
    r, g, b = adjust_saturation(r, g, b, 1.38)
    return r, g, b

# 11. CCD Retro Compact (DQS / Point & Shoot Tourist Cam)
# Punchy primary saturation (Kodak Max 400 look on digicam), crisp microsharpness
def grade_ccd_retro_compact(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.02, shoulder=0.94)
    g = film_s_curve(g, contrast=1.26, toe=0.02, shoulder=0.94)
    b = film_s_curve(b, contrast=1.28, toe=0.02, shoulder=0.94)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Punchy warm-center pop
    r = 1.04 * r + 0.03 * (1.0 - luma)
    g = 1.02 * g
    b = 0.92 * b
    r, g, b = adjust_saturation(r, g, b, 1.40)
    return r, g, b

# 12. Disposable Classic (Golf / 27 Exp Single-Use)
# Intense optical vignette, warm party contrast, plastic lens chromatic shift
def grade_disp_classic(r, g, b):
    r = film_s_curve(r, contrast=1.35, toe=0.04, shoulder=0.86)
    g = film_s_curve(g, contrast=1.30, toe=0.03, shoulder=0.88)
    b = film_s_curve(b, contrast=1.22, toe=0.02, shoulder=0.90)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Warm amber disposable dye cast
    mid = clamp(1.0 - abs(luma - 0.5) * 2.2)
    r += 0.12 * mid
    g += 0.06 * mid
    b -= 0.10 * mid
    r, g, b = adjust_saturation(r, g, b, 1.32)
    return r, g, b

# 13. Disposable Flash (CPM35 / Built-in Strobe Snap)
# Punchy xenon strobe, heavy falloff, hot skin highlights, vibrant saturated colors
def grade_disp_flash(r, g, b):
    r = film_s_curve(r, contrast=1.42, toe=0.03, shoulder=0.90)
    g = film_s_curve(g, contrast=1.38, toe=0.02, shoulder=0.90)
    b = film_s_curve(b, contrast=1.30, toe=0.02, shoulder=0.92)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    if luma > 0.40:
        strobe = (luma - 0.40) * 1.5
        r += 0.12 * strobe
        g += 0.06 * strobe
        b -= 0.02 * strobe
    r, g, b = adjust_saturation(r, g, b, 1.35)
    return r, g, b

# 14. Disposable Warm (135 SR / Golden Summer Beach Vacation)
# Deep honey golden sunset glow, warm copper highlights, chocolate shadows
def grade_disp_warm(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.05, shoulder=0.84)
    g = film_s_curve(g, contrast=1.22, toe=0.04, shoulder=0.86)
    b = film_s_curve(b, contrast=1.12, toe=0.02, shoulder=0.90)
    # Heavy honey/amber infusion
    r = 1.10 * r + 0.08 * g
    g = 1.04 * g + 0.04 * r
    b = 0.72 * b
    r, g, b = adjust_saturation(r, g, b, 1.38)
    return r, g, b

# 15. Disposable Night (Half Frame High-ISO Street)
# Moody olive-green/teal shadows, gritty desaturated urban night tones
def grade_disp_night(r, g, b):
    r = film_s_curve(r, contrast=1.15, toe=0.08, shoulder=0.82)
    g = film_s_curve(g, contrast=1.18, toe=0.09, shoulder=0.85)
    b = film_s_curve(b, contrast=1.12, toe=0.07, shoulder=0.86)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Moody greenish-cyan night tone
    sh = clamp(1.0 - luma * 2.2)
    r -= 0.05 * sh
    g += 0.10 * sh
    b += 0.06 * sh
    r, g, b = adjust_saturation(r, g, b, 0.85)
    return r, g, b

# 16. Instant Classic (Polaroid Peel-Apart / White Border)
# Chemical greenish-cyan shadow drift, creamy peachy highlights, milky blacks
def grade_inst_classic(r, g, b):
    r = film_s_curve(r, contrast=1.12, toe=0.10, shoulder=0.80)
    g = film_s_curve(g, contrast=1.10, toe=0.09, shoulder=0.82)
    b = film_s_curve(b, contrast=1.05, toe=0.11, shoulder=0.84)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Chemical green-cyan shadows
    sh = clamp(1.0 - luma * 2.5)
    r -= 0.04 * sh
    g += 0.08 * sh
    b += 0.06 * sh
    # Creamy peach highlights
    hi = clamp((luma - 0.50) * 1.8)
    r += 0.10 * hi
    g += 0.06 * hi
    b -= 0.04 * hi
    r, g, b = adjust_saturation(r, g, b, 1.05)
    return r, g, b

# 17. Soft Polaroid (Square Cream / TLR)
# Dreamy warm ivory tones, soft pastel contrast, luminous gentle glow
def grade_inst_soft_polaroid(r, g, b):
    r = film_s_curve(r, contrast=0.96, toe=0.12, shoulder=0.76)
    g = film_s_curve(g, contrast=0.94, toe=0.11, shoulder=0.78)
    b = film_s_curve(b, contrast=0.90, toe=0.12, shoulder=0.80)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Ivory cream glow
    r += 0.08 * (1.0 - luma * 0.4)
    g += 0.06 * (1.0 - luma * 0.4)
    b += 0.01 * (1.0 - luma * 0.4)
    r, g, b = adjust_saturation(r, g, b, 0.90)
    return r, g, b

# 18. Vintage Instant (Aged Emulsion 600)
# Heavy turquoise/cyan shadow drift, yellowed faded whites, aged chemistry
def grade_inst_vintage(r, g, b):
    r = film_s_curve(r, contrast=1.15, toe=0.14, shoulder=0.78)
    g = film_s_curve(g, contrast=1.12, toe=0.13, shoulder=0.80)
    b = film_s_curve(b, contrast=1.06, toe=0.15, shoulder=0.82)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Turquoise shadow drift
    sh = clamp(1.0 - luma * 2.0)
    r -= 0.08 * sh
    g += 0.10 * sh
    b += 0.12 * sh
    # Yellowed highlights
    hi = clamp((luma - 0.55) * 2.0)
    r += 0.12 * hi
    g += 0.10 * hi
    b -= 0.08 * hi
    r, g, b = adjust_saturation(r, g, b, 1.10)
    return r, g, b

# 19. Retro VHS (1989 Camcorder Magnetic Tape)
# Lifted charcoal blacks (toe 0.15), shifted NTSC phosphor green-cyan shadow tone
def grade_cam_retro_vhs(r, g, b):
    r = film_s_curve(r, contrast=1.18, toe=0.14, shoulder=0.84)
    g = film_s_curve(g, contrast=1.15, toe=0.13, shoulder=0.86)
    b = film_s_curve(b, contrast=1.10, toe=0.15, shoulder=0.88)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Magnetic tape color phase shift
    r = 0.94 * r + 0.08 * g
    g = 0.04 * r + 0.98 * g
    b = 0.06 * g + 0.88 * b
    # Shadow chroma bleed
    sh = clamp(1.0 - luma * 2.5)
    r += 0.06 * sh
    g -= 0.02 * sh
    b -= 0.04 * sh
    r, g, b = adjust_saturation(r, g, b, 1.05)
    return r, g, b

# 20. Analog Video (8mm Home Video Tape)
# Warm nostalgic amber/sepia home movie palette, soft roll-off
def grade_cam_analog_video(r, g, b):
    r = film_s_curve(r, contrast=1.14, toe=0.10, shoulder=0.82)
    g = film_s_curve(g, contrast=1.10, toe=0.09, shoulder=0.84)
    b = film_s_curve(b, contrast=1.02, toe=0.06, shoulder=0.88)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Nostalgic golden amber home video wash
    r = 1.06 * r + 0.06 * (1.0 - b)
    g = 1.03 * g + 0.03 * (1.0 - b)
    b = 0.82 * b
    r, g, b = adjust_saturation(r, g, b, 1.20)
    return r, g, b

# 21. 90s Camcorder (Handheld CCD Broadcast Video)
# Punchy 90s broadcast saturation, cool fluorescent tone, vivid blues & reds
def grade_cam_90s_camcorder(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.05, shoulder=0.92)
    g = film_s_curve(g, contrast=1.26, toe=0.05, shoulder=0.92)
    b = film_s_curve(b, contrast=1.30, toe=0.06, shoulder=0.95)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    # Cool video broadcast bias
    r -= 0.04
    g += 0.03
    b += 0.10
    r, g, b = adjust_saturation(r, g, b, 1.25)
    return r, g, b

# 22. B&W Silver Halide (Kodak Tri-X 400 Panchromatic Film)
# Punchy contrast (1.38), deep crushed rich blacks, luminous silver highlights
def grade_bw_silver(r, g, b):
    luma = 0.28 * r + 0.60 * g + 0.12 * b
    l = film_s_curve(luma, contrast=1.38, toe=0.02, shoulder=0.92)
    return l, l, l

ALL_GRADES = {
    "lut_analog_35mm_classic.png": grade_analog_35mm_classic,
    "lut_analog_warm_film.png":    grade_analog_warm_film,
    "lut_analog_soft_film.png":    grade_analog_soft_film,
    "lut_analog_vintage_color.png": grade_analog_vintage_color,
    "lut_analog_faded_film.png":   grade_analog_faded_film,
    "lut_analog_cinema_film.png":  grade_analog_cinema_film,
    "lut_ccd_classic.png":         grade_ccd_classic,
    "lut_ccd_flash.png":           grade_ccd_flash,
    "lut_ccd_cool_digital.png":    grade_ccd_cool_digital,
    "lut_ccd_y2k_digital.png":     grade_ccd_y2k_digital,
    "lut_ccd_retro_compact.png":   grade_ccd_retro_compact,
    "lut_disp_classic.png":        grade_disp_classic,
    "lut_disp_flash.png":          grade_disp_flash,
    "lut_disp_warm.png":           grade_disp_warm,
    "lut_disp_night.png":          grade_disp_night,
    "lut_inst_classic.png":        grade_inst_classic,
    "lut_inst_soft_polaroid.png":  grade_inst_soft_polaroid,
    "lut_inst_vintage.png":        grade_inst_vintage,
    "lut_cam_retro_vhs.png":       grade_cam_retro_vhs,
    "lut_cam_analog_video.png":    grade_cam_analog_video,
    "lut_cam_90s_camcorder.png":   grade_cam_90s_camcorder,
    "lut_bw.png":                  grade_bw_silver,
    # Also backward compatibility aliases
    "lut_dclassic.png":            grade_analog_35mm_classic,
    "lut_dexp.png":                grade_analog_vintage_color,
    "lut_nt16.png":                grade_analog_faded_film,
    "lut_ct2f.png":                grade_analog_soft_film,
    "lut_135ne.png":               grade_ccd_classic,
    "lut_dfuns.png":               grade_ccd_flash,
    "lut_d3d.png":                 grade_analog_cinema_film,
    "lut_instant.png":             grade_inst_classic,
    "lut_ir.png":                  grade_ccd_cool_digital,
    "lut_vhs.png":                 grade_cam_retro_vhs,
}

def generate_hald_clut(process_fn):
    # 512x512 Hald CLUT 8
    img = Image.new("RGBA", (512, 512))
    pixels = img.load()
    
    for b_idx in range(64):
        tile_x = (b_idx % 8) * 64
        tile_y = (b_idx // 8) * 64
        b_in = b_idx / 63.0
        
        for g_idx in range(64):
            g_in = g_idx / 63.0
            y = tile_y + g_idx
            
            for r_idx in range(64):
                r_in = r_idx / 63.0
                x = tile_x + r_idx
                
                r_out, g_out, b_out = process_fn(r_in, g_in, b_in)
                
                ir = max(0, min(255, int(round(clamp(r_out) * 255.0))))
                ig = max(0, min(255, int(round(clamp(g_out) * 255.0))))
                ib = max(0, min(255, int(round(clamp(b_out) * 255.0))))
                pixels[x, y] = (ir, ig, ib, 255)
                
    return img

if __name__ == "__main__":
    out_dir = r"C:\Users\User\Downloads\DazzProject\app\src\main\assets"
    print(f"Generating {len(ALL_GRADES)} bold authentic film LUTs into {out_dir}...")
    for name, fn in ALL_GRADES.items():
        p = os.path.join(out_dir, name)
        lut_img = generate_hald_clut(fn)
        lut_img.save(p, format="PNG")
        print(f"Generated {name} (512x512)")
    print("All LUTs successfully generated!")
