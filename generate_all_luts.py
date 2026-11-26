import os, math
from PIL import Image

def clamp(v, low=0.0, high=1.0):
    return max(low, min(high, v))

def film_s_curve(x, contrast=1.2, toe=0.04, shoulder=0.90):
    x = clamp(x)
    if x < 0.5:
        s = 0.5 * math.pow(2.0 * x, contrast)
    else:
        s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), contrast)
    if s > shoulder:
        delta = s - shoulder
        s = shoulder + delta * 0.40
    return toe + s * (1.0 - toe)

def adjust_saturation(r, g, b, sat):
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return (
        clamp(luma + sat * (r - luma)),
        clamp(luma + sat * (g - luma)),
        clamp(luma + sat * (b - luma))
    )

# ─── 22 AUTHENTIC & DISTINCT FILM PROFILES ───────────────────────────────────

# 1. 35mm Classic (Kodak Portra 400 / D Classic)
# Creamy peachy skin tones, warm golden sunlight, subtle cyan shadow density, soft highlight roll-off
def grade_analog_35mm_classic(r, g, b):
    r = film_s_curve(r, contrast=1.20, toe=0.03, shoulder=0.82)
    g = film_s_curve(g, contrast=1.18, toe=0.03, shoulder=0.84)
    b = film_s_curve(b, contrast=1.14, toe=0.05, shoulder=0.88)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Shadows: delicate cyan/teal undertone
    sh = clamp(1.0 - luma * 2.4)
    r -= 0.06 * sh
    g += 0.02 * sh
    b += 0.08 * sh
    
    # Midtones / Skin: legendary Portra creamy peach warmth
    # Detect peachy skin reflectance
    if r > g and g > b:
        skin_w = clamp(1.0 - abs(luma - 0.55) * 2.2)
        r += 0.07 * skin_w
        g += 0.03 * skin_w
        b -= 0.04 * skin_w
    
    # Highlights: warm golden ivory roll-off
    hi = clamp((luma - 0.45) * 1.8)
    r += 0.08 * hi
    g += 0.04 * hi
    b -= 0.05 * hi
    
    # Mute harsh neon greens to rich olive-sage
    if g > r and g > b:
        g = g * 0.90 + r * 0.10
        r = r * 1.05
    
    return adjust_saturation(r, g, b, 1.18)

# 2. Warm Film (Kodak Gold 200)
# Heavy golden-hour sunlight wash across the entire image, warm honey amber tones
def grade_analog_warm_film(r, g, b):
    r = film_s_curve(r, contrast=1.22, toe=0.04, shoulder=0.80)
    g = film_s_curve(g, contrast=1.16, toe=0.03, shoulder=0.84)
    b = film_s_curve(b, contrast=1.08, toe=0.02, shoulder=0.90)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Amber/gold infusion
    r = clamp(1.12 * r + 0.08 * (1.0 - b))
    g = clamp(1.05 * g + 0.04 * (1.0 - b))
    b = clamp(0.72 * b)
    
    # Warm chocolate shadows
    sh = clamp(1.0 - luma * 2.2)
    r += 0.06 * sh
    g += 0.02 * sh
    b -= 0.03 * sh
    
    return adjust_saturation(r, g, b, 1.32)

# 3. Soft Film (Fuji Pro 400H / Provia 100F Slide)
# Ethereal, pastel, airy: soft mint greens, lavender-cyan skies, low contrast, creamy porcelain skin
def grade_analog_soft_film(r, g, b):
    # Gentle low contrast curve with lifted matte blacks
    r = film_s_curve(r, contrast=0.95, toe=0.08, shoulder=0.82)
    g = film_s_curve(g, contrast=0.94, toe=0.07, shoulder=0.84)
    b = film_s_curve(b, contrast=0.96, toe=0.09, shoulder=0.86)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Fuji mint green pop
    if g > r and g > b:
        g = clamp(g * 1.15)
        b = clamp(b * 1.08)
        r = clamp(r * 0.88)
    
    # Pastel cyan-lavender skies
    if b > r and b > g:
        b = clamp(b * 1.16)
        r = clamp(r * 1.04) # lavender bias
    
    # Delicate pink porcelain skin
    sh = clamp(1.0 - luma * 2.5)
    r += 0.04 * sh
    b += 0.06 * sh
    
    return adjust_saturation(r, g, b, 1.06)

# 4. Vintage Color (1970s Kodachrome 64 / D Exp)
# High contrast, rich tomato reds, golden mustard yellows, deep earthy olive greens
def grade_analog_vintage_color(r, g, b):
    r = film_s_curve(r, contrast=1.38, toe=0.07, shoulder=0.80)
    g = film_s_curve(g, contrast=1.30, toe=0.06, shoulder=0.82)
    b = film_s_curve(b, contrast=1.16, toe=0.04, shoulder=0.86)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # 70s Earthy dye matrices
    r_new = clamp(0.92 * r + 0.16 * g)
    g_new = clamp(0.06 * r + 0.94 * g)
    b_new = clamp(0.10 * r + 0.70 * b)
    r, g, b = r_new, g_new, b_new
    
    # Mustard shadow warmth
    sh = clamp(1.0 - luma * 2.5)
    r += 0.08 * sh
    g += 0.05 * sh
    b -= 0.04 * sh
    
    return adjust_saturation(r, g, b, 1.35)

# 5. Faded Film (NT16 / 1968 Faded Paper Negative)
# Heavy milky lifted blacks (toe 0.18), dusty desaturated pastels, tea-stained nostalgic haze
def grade_analog_faded_film(r, g, b):
    r = film_s_curve(r, contrast=0.84, toe=0.18, shoulder=0.76)
    g = film_s_curve(g, contrast=0.82, toe=0.17, shoulder=0.78)
    b = film_s_curve(b, contrast=0.80, toe=0.16, shoulder=0.80)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Sepia / tea-stained midtones
    mid = clamp(1.0 - abs(luma - 0.5) * 2.0)
    r += 0.10 * mid
    g += 0.06 * mid
    b -= 0.06 * mid
    
    return adjust_saturation(r, g, b, 0.72)

# 6. Cinema Film (Kodak Vision3 500T / D3D)
# Balanced motion picture negative: rich cinematic contrast, deep neutral black floor, subtle cool shadow separation, natural red-orange skin tones without yellow or blue casts
def grade_analog_cinema_film(r, g, b):
    r = film_s_curve(r, contrast=1.20, toe=0.002, shoulder=0.96)
    g = film_s_curve(g, contrast=1.20, toe=0.002, shoulder=0.96)
    b = film_s_curve(b, contrast=1.20, toe=0.002, shoulder=0.96)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Shadow transition teal tint: preserves true black floor while giving subtle filmic cool shadow separation
    black_anchor = clamp((luma - 0.01) / 0.05)
    sh = clamp(1.0 - (luma - 0.05) * 3.0) * black_anchor
    r -= 0.015 * sh
    g += 0.010 * sh
    b += 0.025 * sh
    
    # Balanced warm highlights: preserves red-orange vascular tone without crushing blue or over-boosting green
    hi = clamp((luma - 0.45) * 1.8)
    r += 0.06 * hi
    g += 0.015 * hi
    b -= 0.025 * hi
    
    return adjust_saturation(r, g, b, 1.05)

# 7. CCD Classic (135 NE / 2003 Compact Digicam)
# High digital contrast, vivid electric cyan/blue skies, cool metallic shadows, punchy primaries
def grade_ccd_classic(r, g, b):
    r = film_s_curve(r, contrast=1.36, toe=0.01, shoulder=0.96)
    g = film_s_curve(g, contrast=1.34, toe=0.01, shoulder=0.96)
    b = film_s_curve(b, contrast=1.38, toe=0.02, shoulder=0.98)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Cool metallic shadow bias
    sh = clamp(1.0 - luma * 3.0)
    r -= 0.05 * sh
    b += 0.10 * sh
    
    # Saturated digicam sky
    if b > r:
        b = clamp(b * 1.28)
        g = clamp(g * 1.05)
    
    return adjust_saturation(r, g, b, 1.38)

# 8. CCD Flash (Night Strobe Pop)
# Harsh xenon strobe pop: blown luminous foreground skin, deep black background falloff
def grade_ccd_flash(r, g, b):
    r = film_s_curve(r, contrast=1.46, toe=0.01, shoulder=0.95)
    g = film_s_curve(g, contrast=1.44, toe=0.01, shoulder=0.95)
    b = film_s_curve(b, contrast=1.38, toe=0.01, shoulder=0.92)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Direct xenon flash pop
    if luma > 0.32:
        pop = (luma - 0.32) * 1.8
        r += 0.14 * pop
        g += 0.08 * pop
        b += 0.02 * pop
    
    # Crushed background shadows
    sh = clamp(1.0 - luma * 3.0)
    r += 0.04 * sh
    
    return adjust_saturation(r, g, b, 1.34)

# 8b. D FunS (Single-Use 35mm Disposable Film Camera)
# Authentic colour negative film response: organic red-orange skin, soft highlight shoulder compression, preserved blue channel
def grade_dfuns_disposable_film(r, g, b):
    r = film_s_curve(r, contrast=1.22, toe=0.035, shoulder=0.88)
    g = film_s_curve(g, contrast=1.20, toe=0.030, shoulder=0.88)
    b = film_s_curve(b, contrast=1.16, toe=0.038, shoulder=0.89)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Soft warm flash/daylight illumination (subtle red-orange dye density, preserving blue!)
    if luma > 0.45:
        hi = clamp((luma - 0.45) * 1.5)
        r += 0.05 * hi
        g += 0.015 * hi
        b += 0.010 * hi
        
    # Gentle negative film shadow warmth (subtle orange base density lift)
    sh = clamp(1.0 - luma * 2.8)
    r += 0.025 * sh
    g += 0.010 * sh
    b += 0.005 * sh
    
    return adjust_saturation(r, g, b, 1.10)

# 9. CCD Cool Digital (IR / Aerochrome False-Color)
# Surreal false-color infrared: green foliage flips to vivid scarlet/pink, skies deepen to midnight indigo
def grade_ccd_cool_digital(r, g, b):
    r = film_s_curve(r, contrast=1.36, toe=0.02, shoulder=0.92)
    g = film_s_curve(g, contrast=1.32, toe=0.02, shoulder=0.92)
    b = film_s_curve(b, contrast=1.38, toe=0.03, shoulder=0.95)
    
    # Aerochrome IR effect: foliage turns crimson/magenta
    if g > r * 0.75 and g > b * 0.75:
        orig_g = g
        r = clamp(orig_g * 1.80 + 0.05)
        g = clamp(orig_g * 0.20)
        b = clamp(b * 0.60 + orig_g * 0.40)
    else:
        # Cyber midnight indigo skies
        r = clamp(r * 0.85 - 0.04)
        g = clamp(g * 0.95 + 0.02)
        b = clamp(b * 1.22 + 0.10)
    
    return adjust_saturation(r, g, b, 1.40)

# 10. CCD Y2K Digital (Classic U / MySpace Glamour)
# Saturated magenta-pink highlights, bright glamour skin glow, crunchy early-digital contrast
def grade_ccd_y2k_digital(r, g, b):
    r = film_s_curve(r, contrast=1.30, toe=0.02, shoulder=0.96)
    g = film_s_curve(g, contrast=1.22, toe=0.02, shoulder=0.95)
    b = film_s_curve(b, contrast=1.32, toe=0.03, shoulder=0.98)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Pink-magenta glamour highlight glow
    hi = clamp((luma - 0.28) * 1.8)
    r += 0.18 * hi
    g -= 0.05 * hi
    b += 0.14 * hi
    
    return adjust_saturation(r, g, b, 1.42)

# 11. CCD Retro Compact (DQS / Tourist Point & Shoot)
# Punchy primary saturation, crisp clarity, clean whites, everyday snapshot look
def grade_ccd_retro_compact(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.02, shoulder=0.94)
    g = film_s_curve(g, contrast=1.26, toe=0.02, shoulder=0.94)
    b = film_s_curve(b, contrast=1.28, toe=0.02, shoulder=0.94)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Warm punchy primaries
    r = clamp(1.06 * r + 0.04 * (1.0 - luma))
    g = clamp(1.02 * g)
    b = clamp(0.92 * b)
    
    return adjust_saturation(r, g, b, 1.38)

# 12. Disposable Classic (Golf / Single-Use 27 Exp)
# Warm party snapshot: warm amber tones, plastic lens warmth, punchy disposable contrast
def grade_disp_classic(r, g, b):
    r = film_s_curve(r, contrast=1.36, toe=0.05, shoulder=0.85)
    g = film_s_curve(g, contrast=1.30, toe=0.04, shoulder=0.86)
    b = film_s_curve(b, contrast=1.20, toe=0.03, shoulder=0.90)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Warm amber disposable dye cast
    mid = clamp(1.0 - abs(luma - 0.5) * 2.2)
    r += 0.14 * mid
    g += 0.07 * mid
    b -= 0.12 * mid
    
    return adjust_saturation(r, g, b, 1.32)

# 13. Disposable Flash (CPM35 Flash Disposable)
# Harsh xenon strobe, warm skin glow, saturated party snapshot
# 13. Flash Disposable (CPM 35 / Color Print Motion 35 - Y2K Golden Digital Compact)
# Golden nostalgic compact camera response: radiant amber flash, golden skin, rich reds,
# protected blue channel preventing mustard/curry yellow cast on skin tones
def grade_disp_flash(r, g, b):
    # DigicamColorEngine CPM35 authoritative color science baseline (illuminant: GOLDEN_DAYLIGHT)
    # Calibrated with complementary shader inversion (contrast 1.04, sat 1.02) for 0.00 Delta E parity
    def srgb_to_lin(c):
        c = clamp(c)
        return c / 12.92 if c <= 0.04045 else math.pow((c + 0.055) / 1.055, 2.4)
    
    def lin_to_srgb(c):
        c = clamp(c)
        srgb = 12.92 * c if c <= 0.0031308 else 1.055 * math.pow(max(0.0, c), 1.0 / 2.4) - 0.055
        return clamp(srgb)
    
    # 1. Linear Color Space Partial White Balance (CPM35 daylight gains with retention ratio 0.45)
    r_lin = srgb_to_lin(r) * math.exp(0.45 * math.log(1.0815))
    g_lin = srgb_to_lin(g) * math.exp(0.45 * math.log(0.9604))
    b_lin = srgb_to_lin(b) * math.exp(0.45 * math.log(0.7912))
    
    r_s = lin_to_srgb(r_lin)
    g_s = lin_to_srgb(g_lin)
    b_s = lin_to_srgb(b_lin)
    
    # 2. Continuous Hue-Chroma-Luminance Red Protection (Ref 2 scarlet preservation)
    max_c = max(r_s, g_s, b_s)
    min_c = min(r_s, g_s, b_s)
    chroma = max_c - min_c
    sat = chroma / max_c if max_c > 0.001 else 0.0
    if sat > 0.30 and r_s > g_s and r_s > b_s:
        delta = max_c - min_c
        raw_hue = ((g_s - b_s) / delta) * 60.0 if delta > 0.001 else 0.0
        if raw_hue < 0.0:
            raw_hue += 360.0
        dist_from_red = min(raw_hue, 360.0 - raw_hue)
        hue_factor = math.exp(-(dist_from_red * dist_from_red) / (2.0 * 18.0 * 18.0))
        sat_gating = clamp((sat - 0.35) / 0.30)
        red_boost = 1.10 * 1.08
        effective_boost = 1.0 + (red_boost - 1.0) * hue_factor * sat_gating
        r_s = clamp(r_s * effective_boost)
    
    # 3. CPM35 Compact Digicam S-Curve Tone Response with Asymptotic Soft Knee
    toe = 0.018 * 0.95
    contrast = 1.12
    shoulder = 0.94
    
    def digicam_tone_curve(x):
        x = clamp(x)
        x_toe = toe + (1.0 - toe) * math.pow(x, 1.06)
        if x_toe < 0.45:
            x_cont = 0.45 * math.pow(x_toe / 0.45, contrast)
        else:
            x_cont = 1.0 - 0.55 * math.pow((1.0 - x_toe) / 0.55, contrast)
        if x_cont > shoulder:
            excess = x_cont - shoulder
            headroom = 1.0 - shoulder
            return shoulder + headroom * (excess / (excess + headroom * 0.45))
        return x_cont
    
    r_t = digicam_tone_curve(r_s)
    g_t = digicam_tone_curve(g_s)
    b_t = digicam_tone_curve(b_s)
    
    # 4. Golden Digicam Saturation (1.134)
    luma_t = 0.2126 * r_t + 0.7152 * g_t + 0.0722 * b_t
    r_sat = luma_t + 1.134 * (r_t - luma_t)
    g_sat = luma_t + 1.134 * (g_t - luma_t)
    b_sat = luma_t + 1.134 * (b_t - luma_t)
    
    # 5. Subtle Amber-Warm Shadow Tint
    sh = clamp(1.0 - luma_t * 3.0)
    target_r = clamp(r_sat + 0.015 * sh)
    target_g = clamp(g_sat + 0.010 * sh)
    target_b = clamp(b_sat + 0.005 * sh)
    
    # 6. Complementary Inversion for Shader Base Contrast (1.04) and Saturation (1.02)
    luma_fin = 0.2126 * target_r + 0.7152 * target_g + 0.0722 * target_b
    inv_sat_r = luma_fin + (target_r - luma_fin) / 1.02
    inv_sat_g = luma_fin + (target_g - luma_fin) / 1.02
    inv_sat_b = luma_fin + (target_b - luma_fin) / 1.02
    
    lut_r = (inv_sat_r - 0.26) / 1.04 + 0.26
    lut_g = (inv_sat_g - 0.26) / 1.04 + 0.26
    lut_b = (inv_sat_b - 0.26) / 1.04 + 0.26
    
    return clamp(lut_r), clamp(lut_g), clamp(lut_b)

# 14. Disposable Warm (135 SR / Golden Summer Disposable)
# Deep honey-orange saturation, sunset beach memories, copper glow
def grade_disp_warm(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.06, shoulder=0.82)
    g = film_s_curve(g, contrast=1.20, toe=0.05, shoulder=0.85)
    b = film_s_curve(b, contrast=1.10, toe=0.03, shoulder=0.90)
    
    # Heavy honey/amber infusion
    r = clamp(1.14 * r + 0.10 * g)
    g = clamp(1.05 * g + 0.04 * r)
    b = clamp(0.68 * b)
    
    return adjust_saturation(r, g, b, 1.40)

# 15. Disposable Night (D Half / Gritty Half-Frame)
# Moody, gritty urban night street film: desaturated greens, dark olive shadows, moody atmosphere
def grade_disp_night(r, g, b):
    r = film_s_curve(r, contrast=1.18, toe=0.09, shoulder=0.80)
    g = film_s_curve(g, contrast=1.22, toe=0.10, shoulder=0.84)
    b = film_s_curve(b, contrast=1.14, toe=0.08, shoulder=0.85)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Moody greenish-cyan night tone
    sh = clamp(1.0 - luma * 2.2)
    r -= 0.08 * sh
    g += 0.12 * sh
    b += 0.06 * sh
    
    return adjust_saturation(r, g, b, 0.80)

# 16. Instant Classic (Polaroid 600 / White Frame Peel-Apart)
# Milky black point (toe 0.14), chemical greenish-cyan shadows, creamy peachy highlights
def grade_inst_classic(r, g, b):
    r = film_s_curve(r, contrast=1.08, toe=0.14, shoulder=0.78)
    g = film_s_curve(g, contrast=1.06, toe=0.13, shoulder=0.80)
    b = film_s_curve(b, contrast=1.02, toe=0.15, shoulder=0.82)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Chemical greenish-cyan shadow drift
    sh = clamp(1.0 - luma * 2.5)
    r -= 0.06 * sh
    g += 0.10 * sh
    b += 0.08 * sh
    
    # Creamy peach highlights
    hi = clamp((luma - 0.48) * 1.8)
    r += 0.12 * hi
    g += 0.06 * hi
    b -= 0.04 * hi
    
    return adjust_saturation(r, g, b, 1.08)

# 17. Soft Polaroid (Square Cream / TLR)
# Dreamy warm ivory tones, very soft contrast, delicate pastel glow, square white frame
def grade_inst_soft_polaroid(r, g, b):
    r = film_s_curve(r, contrast=0.92, toe=0.16, shoulder=0.74)
    g = film_s_curve(g, contrast=0.90, toe=0.15, shoulder=0.76)
    b = film_s_curve(b, contrast=0.86, toe=0.16, shoulder=0.78)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Luminous ivory cream glow
    r = clamp(r + 0.10 * (1.0 - luma * 0.4))
    g = clamp(g + 0.08 * (1.0 - luma * 0.4))
    b = clamp(b + 0.02 * (1.0 - luma * 0.4))
    
    return adjust_saturation(r, g, b, 0.88)

# 18. Vintage Instant (Aged SX-70 Instant)
# Distinct aged chemistry: strong turquoise shadow shift, faded yellowed highlights
def grade_inst_vintage(r, g, b):
    r = film_s_curve(r, contrast=1.12, toe=0.16, shoulder=0.76)
    g = film_s_curve(g, contrast=1.10, toe=0.15, shoulder=0.78)
    b = film_s_curve(b, contrast=1.04, toe=0.17, shoulder=0.80)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Turquoise shadow drift
    sh = clamp(1.0 - luma * 2.2)
    r -= 0.10 * sh
    g += 0.12 * sh
    b += 0.15 * sh
    
    # Yellowed aged highlights
    hi = clamp((luma - 0.50) * 2.0)
    r += 0.14 * hi
    g += 0.12 * hi
    b -= 0.10 * hi
    
    return adjust_saturation(r, g, b, 1.10)

# 19. Retro VHS (1989 Camcorder Magnetic Tape)
# Magnetic tape phase drift, NTSC green-cyan shadow tone, lifted tape blacks
def grade_cam_retro_vhs(r, g, b):
    r = film_s_curve(r, contrast=1.16, toe=0.15, shoulder=0.82)
    g = film_s_curve(g, contrast=1.14, toe=0.14, shoulder=0.84)
    b = film_s_curve(b, contrast=1.08, toe=0.16, shoulder=0.86)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Magnetic tape color phase shift
    r_new = clamp(0.92 * r + 0.10 * g)
    g_new = clamp(0.04 * r + 0.98 * g)
    b_new = clamp(0.08 * g + 0.86 * b)
    r, g, b = r_new, g_new, b_new
    
    # Shadow chroma bleed
    sh = clamp(1.0 - luma * 2.5)
    r += 0.07 * sh
    g -= 0.02 * sh
    b -= 0.05 * sh
    
    return adjust_saturation(r, g, b, 1.05)

# 20. Analog Video (8mm Home Video Tape)
# Super 8 warm nostalgic home movie palette, rounded film gate, soft organic roll-off
def grade_cam_analog_video(r, g, b):
    r = film_s_curve(r, contrast=1.12, toe=0.12, shoulder=0.80)
    g = film_s_curve(g, contrast=1.08, toe=0.10, shoulder=0.82)
    b = film_s_curve(b, contrast=1.00, toe=0.08, shoulder=0.86)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Nostalgic golden amber home video wash
    r = clamp(1.08 * r + 0.08 * (1.0 - b))
    g = clamp(1.04 * g + 0.04 * (1.0 - b))
    b = clamp(0.78 * b)
    
    return adjust_saturation(r, g, b, 1.22)

# 21. 90s Camcorder (Handheld CCD Broadcast Video)
# Cool fluorescent broadcast television bias, punchy video saturation, sharp green LCD timecode
def grade_cam_90s_camcorder(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.05, shoulder=0.90)
    g = film_s_curve(g, contrast=1.26, toe=0.05, shoulder=0.90)
    b = film_s_curve(b, contrast=1.30, toe=0.06, shoulder=0.94)
    
    # Cool video broadcast bias
    r = clamp(r - 0.04)
    g = clamp(g + 0.03)
    b = clamp(b + 0.12)
    
    return adjust_saturation(r, g, b, 1.26)

# 22. B&W Silver Halide (Kodak Tri-X 400 Panchromatic Film)
# Punchy contrast (1.42), deep crushed velvety blacks, luminous silver highlights
def grade_bw_silver(r, g, b):
    luma = 0.28 * r + 0.60 * g + 0.12 * b
    l = film_s_curve(luma, contrast=1.42, toe=0.02, shoulder=0.92)
    return l, l, l

# 23. FXNR (Warm Negative Film Emulation - Warm Amber Bias, Golden Highlights, Brown Shadows, Creamy Whites)
def grade_analog_fxn_r(r, g, b):
    # 1. Filmic S-curve with gentle, soft contrast and lifted toe (warm brown shadows)
    r_c = film_s_curve(r, contrast=1.06, toe=0.048, shoulder=0.80)
    g_c = film_s_curve(g, contrast=1.05, toe=0.040, shoulder=0.82)
    b_c = film_s_curve(b, contrast=1.03, toe=0.030, shoulder=0.85)
    
    luma = 0.2126 * r_c + 0.7152 * g_c + 0.0722 * b_c
    
    # 2. Golden Yellow Highlights & Specular Bloom
    highlight_weight = clamp((luma - 0.50) * 2.1)
    r_c += 0.078 * highlight_weight
    g_c += 0.046 * highlight_weight
    b_c -= 0.072 * highlight_weight
    
    # 3. Deep Brown / Burnt Red Shadows (lifted black point, rich mahogany toe)
    shadow_weight = clamp((0.44 - luma) * 2.3)
    r_c += 0.054 * shadow_weight
    g_c += 0.024 * shadow_weight
    b_c += 0.008 * shadow_weight
    
    # 4. Color Separation & Selective Spectral Warping:
    # A. Rich Reds & Burnt Oranges (dominant in warm midtones)
    if r_c > g_c:
        # Check human skin tone protection
        if g_c > b_c and r_c > 0.22 and r_c < 0.90:
            skin_factor = clamp(1.0 - abs(luma - 0.54) * 2.2) * clamp((r_c - b_c) * 2.0)
            # Retain healthy peach/rose undertone, dampening harsh radioactive orange
            r_c += 0.012 * skin_factor
            g_c += 0.022 * skin_factor
            b_c += 0.015 * skin_factor
        else:
            # Burnt orange / terracotta / red boost in wood, brick, textiles
            r_c = clamp(r_c * 1.09)
            g_c = clamp(g_c * 1.02)
    
    # B. Muted Olive Greens (de-neonized foliage)
    if g_c > r_c and g_c > b_c:
        g_c = clamp(g_c * 0.92)
        r_c = clamp(r_c + 0.052)  # shift green toward warm olive/amber
        b_c = clamp(b_c * 0.82)
    
    # C. Strongly Subdued Blue/Cyan (cool tones restrained into slate/charcoal)
    if b_c > r_c or b_c > g_c:
        b_c = clamp(b_c * 0.76)   # strongly subdued cool tones
        r_c = clamp(r_c + 0.048)  # warm atmospheric pull
        g_c = clamp(g_c + 0.018)
    
    # 5. Creamy Ivory Highlight Rolloff (prevent harsh digital clipping)
    if luma > 0.78:
        r_c = min(0.985, r_c)
        g_c = min(0.965, g_c)
        b_c = min(0.920, b_c)
    
    # 6. Global Saturation Adjustment: restrained, sophisticated analog negative look
    r_out, g_out, b_out = adjust_saturation(r_c, g_c, b_c, 0.97)
    
    return clamp(r_out), clamp(g_out), clamp(b_out)

# 24. CCD R (Olympus Camedia C-70 Zoom 2000s Compact CCD Digital Camera)
def grade_ccd_r(r, g, b):
    r = film_s_curve(r, contrast=1.28, toe=0.01, shoulder=0.94)
    g = film_s_curve(g, contrast=1.26, toe=0.01, shoulder=0.94)
    b = film_s_curve(b, contrast=1.30, toe=0.02, shoulder=0.96)
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    
    # Deep clean blacks
    sh = clamp(1.0 - luma * 3.0)
    r -= 0.03 * sh
    g -= 0.02 * sh
    b += 0.06 * sh
    
    # Iconic CCD electric cyan/blue skies
    if b > r:
        b = clamp(b * 1.25)
        g = clamp(g * 1.04)
        r = clamp(r * 0.94)
    
    # Sun-kissed digital skin warmth
    if r > g and g > b:
        r = clamp(r * 1.06)
        g = clamp(g * 1.02)
    
    return adjust_saturation(r, g, b, 1.30)

# 25. GR D (Ricoh GR Digital 2005 8.1MP CCD Street Snap Camera)
# Authentic 2005 Ricoh GR Digital: 100% neutral gray axis preservation,
# calibrated complementary tone curve (target 1.14 / shader 1.04),
# pure black anchor, zero shadow crush, and selective CCD street primary color fidelity.
def grade_ccd_grd(r, g, b):
    # 1. Authentic Ricoh GR Digital (2005) Tone Curve
    # Complementary contrast 1.10 (with shader contrast 1.04 -> effective 1.144)
    # Identical curves for R, G, B ensures 100% neutral gray axis preservation
    def gr_curve(x):
        x = clamp(x)
        if x < 0.5:
            s = 0.5 * math.pow(2.0 * x, 1.10)
        else:
            s = 1.0 - 0.5 * math.pow(2.0 * (1.0 - x), 1.10)
        if s > 0.94:
            s = 0.94 + (s - 0.94) * 0.50
        return s / 0.97
        
    r_c = clamp(gr_curve(r))
    g_c = clamp(gr_curve(g))
    b_c = clamp(gr_curve(b))
    
    luma = 0.2126 * r_c + 0.7152 * g_c + 0.0722 * b_c
    chroma = max(r_c, g_c, b_c) - min(r_c, g_c, b_c)
    
    # 2. Strict Neutral Gray Axis Protection
    # If chroma is near zero (neutral gray / monochrome / asphalt / concrete / clouds),
    # strictly avoid any chromatic distortion or color drift.
    if chroma > 0.015:
        chroma_factor = clamp((chroma - 0.015) / 0.08)
        
        # 3. Authentic 2005 CCD Primary Color Separation
        # A. Sky and Denim Blues: Deep, pure CCD blues without greenish cyan cast
        if b_c > r_c and b_c > g_c:
            blue_dominance = clamp((b_c - max(r_c, g_c)) * 2.5)
            b_c = clamp(b_c * (1.0 + 0.06 * blue_dominance * chroma_factor))
            g_c = clamp(g_c * (1.0 - 0.01 * blue_dominance * chroma_factor))
            r_c = clamp(r_c * (1.0 - 0.02 * blue_dominance * chroma_factor))
            
        # B. Rich Urban Reds / Cadmium (traffic signs, tail lamps, bricks)
        elif r_c > g_c and r_c > b_c:
            if g_c > b_c and 0.20 < luma < 0.85 and (r_c - b_c) > 0.08:
                skin_weight = clamp(1.0 - abs(luma - 0.55) * 2.5) * chroma_factor
                r_c = clamp(r_c + 0.010 * skin_weight)
                g_c = clamp(g_c + 0.006 * skin_weight)
            else:
                red_dominance = clamp((r_c - max(g_c, b_c)) * 2.0)
                r_c = clamp(r_c * (1.0 + 0.05 * red_dominance * chroma_factor))
                
        # C. Foliage and Architectural Greens
        elif g_c > r_c and g_c > b_c:
            green_dominance = clamp((g_c - max(r_c, b_c)) * 2.0)
            g_c = clamp(g_c * (1.0 + 0.03 * green_dominance * chroma_factor))
            b_c = clamp(b_c * (1.0 - 0.02 * green_dominance * chroma_factor))

    # 4. Subtle Global Saturation (complementary to shader 1.02)
    r_out, g_out, b_out = adjust_saturation(r_c, g_c, b_c, 1.039)
    return clamp(r_out), clamp(g_out), clamp(b_out)

ALL_GRADES = {
    "lut_ccd_grd.png":             grade_ccd_grd,
    "lut_analog_35mm_classic.png": grade_analog_35mm_classic,
    "lut_analog_warm_film.png":    grade_ccd_grd,
    "lut_analog_soft_film.png":    grade_analog_soft_film,
    "lut_analog_vintage_color.png": grade_analog_vintage_color,
    "lut_analog_faded_film.png":   grade_analog_faded_film,
    "lut_analog_cinema_film.png":  grade_analog_cinema_film,
    "lut_fxn_r.png":               grade_analog_fxn_r,
    "lut_analog_fxn_r.png":        grade_analog_fxn_r,
    "lut_ccd_r.png":               grade_ccd_r,
    "lut_ccdr.png":                grade_ccd_r,
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
    # Compatibility aliases
    "lut_dclassic.png":            grade_analog_35mm_classic,
    "lut_dexp.png":                grade_analog_vintage_color,
    "lut_nt16.png":                grade_analog_fxn_r,
    "lut_ct2f.png":                grade_analog_soft_film,
    "lut_135ne.png":               grade_ccd_classic,
    "lut_dfuns.png":               grade_dfuns_disposable_film,
    "lut_dfuns_v1.png":            grade_dfuns_disposable_film,
    "lut_d3d.png":                 grade_analog_cinema_film,
    "lut_instant.png":             grade_inst_classic,
    "lut_ir.png":                  grade_ccd_cool_digital,
    "lut_vhs.png":                 grade_cam_retro_vhs,
}

def generate_hald_clut(process_fn):
    # 512x512 Hald CLUT 8 (64x64x64 grid)
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
    print(f"Generating {len(ALL_GRADES)} distinctive film LUTs into {out_dir}...")
    for name, fn in ALL_GRADES.items():
        p = os.path.join(out_dir, name)
        lut_img = generate_hald_clut(fn)
        lut_img.save(p, format="PNG")
        print(f"Generated {name} (512x512)")
    print("All LUTs successfully generated!")
