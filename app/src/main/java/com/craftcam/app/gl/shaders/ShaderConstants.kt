package com.craftcam.app.gl.shaders

object ShaderConstants {

    const val VERTEX_SHADER_VIEWFINDER = """#version 300 es
layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec4 aTextureCoord;

out vec2 vQuadCoord;

void main() {
    gl_Position = aPosition;
    vQuadCoord = aTextureCoord.xy;
}
"""

    const val FRAGMENT_SHADER_VIEWFINDER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

uniform samplerExternalOES sTexture;
uniform sampler2D uLutTexture;
uniform bool uLutEnabled;
uniform mat4 uTexMatrix;
uniform vec2 uViewportSize;
in vec2 vQuadCoord;
out vec4 fragColor;

// Tonal and Color Grading
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uGamma;
uniform float uFade;
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

// Stochastic sine-free hash for authentic silver halide film grain (precision-safe)
float filmNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 1024.0) + vec2(fract(seed * 1.6180339887), fract(seed * 1.4142135623)) * 64.0;
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) - 0.5;
}

// Trilinear 3D Hald CLUT Sampling (512x512 = 8x8 tiles of 64x64 slices)
vec3 sampleHaldLut(sampler2D lut, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    float blueVal = rgb.b * 63.0;
    
    float slice0 = floor(blueVal);
    float slice1 = min(63.0, slice0 + 1.0);
    float blueFract = fract(blueVal);
    
    float s0_col = mod(slice0, 8.0);
    float s0_row = floor(slice0 / 8.0);
    
    float s1_col = mod(slice1, 8.0);
    float s1_row = floor(slice1 / 8.0);
    
    // Pixel coordinates in 512x512 texture
    float px0_x = s0_col * 64.0 + 0.5 + rgb.r * 63.0;
    float px1_x = s1_col * 64.0 + 0.5 + rgb.r * 63.0;
    
    // In image bitmap, row 0 is at top, but in OpenGL v=0 is bottom
    float px0_y = s0_row * 64.0 + 0.5 + rgb.g * 63.0;
    float px1_y = s1_row * 64.0 + 0.5 + rgb.g * 63.0;
    
    vec2 uv0 = vec2(px0_x / 512.0, px0_y / 512.0);
    vec2 uv1 = vec2(px1_x / 512.0, px1_y / 512.0);
    
    vec3 col0 = texture(lut, uv0).rgb;
    vec3 col1 = texture(lut, uv1).rgb;
    
    return mix(col0, col1, blueFract);
}

void main() {
    vec2 p = vQuadCoord - 0.5;
    float r2 = dot(p, p);

    // 1. Lens Distortion (Barrel/Pincushion in canonical visual space)
    vec2 uv = vQuadCoord;
    if (abs(uBarrelDistortion) > 0.001) {
        uv = 0.5 + p * (1.0 + uBarrelDistortion * r2 * 0.4);
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    // 2. Transverse Chromatic Aberration in visual space, strictly clamped inside [0, 1]
    vec4 srcColor;
    if (uChromaticAberration > 0.0001) {
        vec2 caOffset = p * (uChromaticAberration * r2 * 0.2);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset, vec2(0.001), vec2(0.999));

        vec2 oesR = (uTexMatrix * vec4(uvR, 0.0, 1.0)).xy;
        vec2 oesG = (uTexMatrix * vec4(uv,  0.0, 1.0)).xy;
        vec2 oesB = (uTexMatrix * vec4(uvB, 0.0, 1.0)).xy;

        float r = texture(sTexture, oesR).r;
        float g = texture(sTexture, oesG).g;
        float b = texture(sTexture, oesB).b;
        srcColor = vec4(r, g, b, 1.0);
    } else {
        vec2 oesUV = (uTexMatrix * vec4(uv, 0.0, 1.0)).xy;
        srcColor = texture(sTexture, oesUV);
    }

    vec3 color = srcColor.rgb;

    // 2b. Optical Softness (Radial spherical aberration edge diffusion)
    if (uSoftness > 0.005) {
        float rSoftness = clamp(r2 * 2.8 * uSoftness, 0.0, 0.85);
        if (rSoftness > 0.01) {
            vec2 sStep = (2.5 / uViewportSize) * (1.0 + rSoftness * 2.0);
            vec3 sL = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(sStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sT = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(0.0, sStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 sB = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(0.0, sStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy).rgb;
            vec3 softSample = (sL + sR + sT + sB) * 0.25;
            color = mix(color, softSample, rSoftness * 0.50);
        }
    }

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // 2c. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
    }
    
    // 3. Preset & User Temperature / Tint (Warm/Cool Shift)
    if (abs(uTemperature) > 0.001) {
        color.r += 0.10 * uTemperature;
        color.b -= 0.10 * uTemperature;
    }
    if (abs(uTint) > 0.001) {
        color.g += 0.06 * uTint;
        color.r -= 0.03 * uTint;
        color.b -= 0.03 * uTint;
    }

    // 4. Preset & User Contrast
    if (abs(uContrast - 1.0) > 0.01) {
        color = clamp((color - 0.5) * uContrast + 0.5, 0.0, 1.0);
    }

    // 5. Preset & User Saturation
    luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    if (abs(uSaturation - 1.0) > 0.01) {
        color = mix(vec3(luma), color, uSaturation);
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 6. Gamma Response
    if (abs(uGamma - 1.0) > 0.01) {
        color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 7. Shadow Tint (Photochemical dye bias in toe)
    float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
    color += uShadowTint * shadowWeight;

    // 8. Filmic Highlight Rolloff (Soft-shoulder exponential compression preventing harsh digital clipping)
    if (uHighlightRollOff < 0.99) {
        float knee = max(0.01, 1.0 - uHighlightRollOff);
        vec3 over = max(vec3(0.0), color - vec3(uHighlightRollOff));
        color = min(color, vec3(uHighlightRollOff)) + knee * (vec3(1.0) - exp(-over / knee));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 9. Film Fade (Lifted blacks / aged paper floor)
    if (uFade > 0.001) {
        color = color * (1.0 - uFade) + uFade;
    }

    // 10. Localized Film Halation (Red-orange highlight glow spreading into adjacent emulsion)
    if (uHalationEnabled && uHalationIntensity > 0.01) {
        vec2 hStep = (12.0 / uViewportSize);
        vec4 tapL = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(hStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(hStep.x, 0.0), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapT = texture(sTexture, (uTexMatrix * vec4(clamp(uv - vec2(0.0, hStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapB = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(0.0, hStep.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);

        vec2 diag = hStep * 0.7071;
        vec4 tapTL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapTR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x, -diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapBL = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2(-diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);
        vec4 tapBR = texture(sTexture, (uTexMatrix * vec4(clamp(uv + vec2( diag.x,  diag.y), vec2(0.001), vec2(0.999)), 0.0, 1.0)).xy);

        float lL = dot(tapL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lR = dot(tapR.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lT = dot(tapT.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lB = dot(tapB.rgb, vec3(0.2126, 0.7152, 0.0722));

        float lTL = dot(tapTL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lTR = dot(tapTR.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lBL = dot(tapBL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lBR = dot(tapBR.rgb, vec3(0.2126, 0.7152, 0.0722));

        float kThresh = uHalationThreshold;
        float hCenter = max(0.0, luma - kThresh);
        float hCross = max(0.0, lL - kThresh) + max(0.0, lR - kThresh) + max(0.0, lT - kThresh) + max(0.0, lB - kThresh);
        float hDiag = max(0.0, lTL - kThresh) + max(0.0, lTR - kThresh) + max(0.0, lBL - kThresh) + max(0.0, lBR - kThresh);

        float spreadHighlight = (hCenter * 0.30 + hCross * 0.10 + hDiag * 0.075) / max(0.01, 1.0 - kThresh);
        if (spreadHighlight > 0.001) {
            vec3 haloColor = vec3(1.0, 0.28, 0.05) * (spreadHighlight * uHalationIntensity * 0.40);
            color += haloColor;
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float edgeDist = length(vec2(max(0.0, uv.x - 0.75) * 2.0, max(0.0, 0.25 - uv.y) * 2.0));
        float leakMask = smoothstep(0.40, 0.0, edgeDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.12) * (leakMask * uLightLeakIntensity * 0.35);
        // Screen blend
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Lens Vignette (matches ImageProcessor.kt quadratic falloff in canonical frame space)
    if (uVignetteIntensity > 0.01) {
        float dist = length(p) / 0.7071068;
        float dn = dist / uVignetteRadius;
        float vignette = clamp(1.0 - uVignetteIntensity * dn * dn, 0.25, 1.0);
        color *= vignette;
    }

    // 13. Authentic Multi-Octave Tri-Pack Film Grain (Channel dye clumping)
    if (uGrainIntensity > 0.005) {
        // Asymmetric film density luminance envelope:
        // Shadows retain ~28% grain, midtones peak at 100% around luma=0.42, highlights roll off to 12%
        float sRise = smoothstep(0.0, 0.42, luma);
        float hFall = smoothstep(0.42, 1.0, luma);
        float grainWeight = (0.28 + 0.72 * sRise) * (1.0 - 0.88 * hFall);

        // Flattering skin protection: gently attenuate grain on smooth skin tones
        if (color.r > color.g && color.g > color.b && color.r > 0.25 && color.r < 0.90) {
            float skinWeight = clamp((color.r - color.b) * 2.0, 0.0, 1.0);
            grainWeight *= (1.0 - 0.35 * skinWeight);
        }

        // Screen-pixel calibrated coordinate: fine crystals
        vec2 grainCoord = vQuadCoord * uViewportSize.xy * (uGrainScale * 0.45);
        
        // Multi-octave synthesis for R, G, B channels with dye cloud separation
        float fineG = filmNoise(grainCoord, uRandomSeed + 0.18);
        float clumpG = filmNoise(grainCoord * 0.45, uRandomSeed + 0.42);
        float noiseG = fineG * 0.85 + clumpG * 0.15;

        float fineR = filmNoise(grainCoord, uRandomSeed + 0.31);
        float clumpR = filmNoise(grainCoord * 0.45, uRandomSeed + 0.67);
        float noiseR = fineR * 0.75 + clumpR * 0.25;

        float fineB = filmNoise(grainCoord, uRandomSeed + 0.53);
        float clumpB = filmNoise(grainCoord * 0.45, uRandomSeed + 0.89);
        float noiseB = fineB * 0.65 + clumpB * 0.35;

        vec3 grainDelta;
        if (uIsColorNoise) {
            grainDelta = vec3(noiseR, noiseG, noiseB);
        } else {
            // Monochromatic base with subtle 18% dye cloud variance for natural analog look
            grainDelta = mix(vec3(noiseG), vec3(noiseR, noiseG, noiseB), 0.18);
        }

        color += grainDelta * (grainWeight * uGrainIntensity * 0.42);
    }

    // 14. Retro Camcorder Scanlines (subtle authentic tape texture)
    if (uScanlineIntensity > 0.01) {
        float scanline = sin(vQuadCoord.y * 600.0) * 0.5 + 0.5;
        color *= mix(1.0, 0.92 + 0.08 * scanline, uScanlineIntensity);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    const val VERTEX_SHADER_STILL = """#version 300 es
layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec2 aTextureCoord;

out vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = aTextureCoord;
}
"""

    const val FRAGMENT_SHADER_STILL = """#version 300 es
precision highp float;

uniform sampler2D sTexture;
uniform sampler2D uLutTexture;
uniform bool uLutEnabled;
in vec2 vTextureCoord;
out vec4 fragColor;

// Tonal and Color Grading
uniform float uContrast;
uniform float uSaturation;
uniform float uTemperature;
uniform float uTint;
uniform float uGamma;
uniform float uFade;
uniform float uHighlightRollOff;
uniform vec3 uShadowTint;

// Lens and Optical Effects
uniform float uVignetteIntensity;
uniform float uVignetteRadius;
uniform float uChromaticAberration;
uniform float uBarrelDistortion;
uniform float uSoftness;

// Film Grain
uniform float uGrainIntensity;
uniform float uGrainScale;
uniform bool uIsColorNoise;
uniform float uRandomSeed;

// Halation and Light Leaks
uniform bool uHalationEnabled;
uniform float uHalationThreshold;
uniform float uHalationIntensity;
uniform bool uLightLeakEnabled;
uniform float uLightLeakIntensity;
uniform float uScanlineIntensity;

// Stochastic sine-free hash for authentic silver halide film grain (precision-safe)
float filmNoise(vec2 coord, float seed) {
    vec2 p = mod(coord, 1024.0) + vec2(fract(seed * 1.6180339887), fract(seed * 1.4142135623)) * 64.0;
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) - 0.5;
}

vec3 sampleHaldLut(sampler2D lut, vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    float blueVal = rgb.b * 63.0;
    
    float slice0 = floor(blueVal);
    float slice1 = min(63.0, slice0 + 1.0);
    float blueFract = fract(blueVal);
    
    float s0_col = mod(slice0, 8.0);
    float s0_row = floor(slice0 / 8.0);
    
    float s1_col = mod(slice1, 8.0);
    float s1_row = floor(slice1 / 8.0);
    
    float px0_x = s0_col * 64.0 + 0.5 + rgb.r * 63.0;
    float px1_x = s1_col * 64.0 + 0.5 + rgb.r * 63.0;
    
    float px0_y = s0_row * 64.0 + 0.5 + rgb.g * 63.0;
    float px1_y = s1_row * 64.0 + 0.5 + rgb.g * 63.0;
    
    vec2 uv0 = vec2(px0_x / 512.0, px0_y / 512.0);
    vec2 uv1 = vec2(px1_x / 512.0, px1_y / 512.0);
    
    vec3 col0 = texture(lut, uv0).rgb;
    vec3 col1 = texture(lut, uv1).rgb;
    
    return mix(col0, col1, blueFract);
}

void main() {
    vec2 uv = vTextureCoord;
    vec2 p = uv - 0.5;
    float r2 = dot(p, p);

    // 1. Lens Distortion
    if (abs(uBarrelDistortion) > 0.001) {
        uv = 0.5 + p * (1.0 + uBarrelDistortion * r2 * 0.4);
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    // 2. Chromatic Aberration
    vec4 srcColor;
    if (uChromaticAberration > 0.0001) {
        vec2 caOffset = p * (uChromaticAberration * r2 * 0.5);
        vec2 uvR = clamp(uv + caOffset, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(uv - caOffset, vec2(0.001), vec2(0.999));
        float r = texture(sTexture, uvR).r;
        float g = texture(sTexture, uv).g;
        float b = texture(sTexture, uvB).b;
        srcColor = vec4(r, g, b, 1.0);
    } else {
        srcColor = texture(sTexture, uv);
    }

    vec3 color = srcColor.rgb;

    // 2b. Optical Softness (Radial spherical aberration edge diffusion)
    if (uSoftness > 0.005) {
        float rSoftness = clamp(r2 * 2.8 * uSoftness, 0.0, 0.85);
        if (rSoftness > 0.01) {
            vec2 sStep = vec2(0.0018) * (1.0 + rSoftness * 2.0);
            vec3 sL = texture(sTexture, uv - vec2(sStep.x, 0.0)).rgb;
            vec3 sR = texture(sTexture, uv + vec2(sStep.x, 0.0)).rgb;
            vec3 sT = texture(sTexture, uv - vec2(0.0, sStep.y)).rgb;
            vec3 sB = texture(sTexture, uv + vec2(0.0, sStep.y)).rgb;
            vec3 softSample = (sL + sR + sT + sB) * 0.25;
            color = mix(color, softSample, rSoftness * 0.50);
        }
    }

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // 2c. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
    }

    // 3. White Balance
    color.r += 0.10 * uTemperature;
    color.b -= 0.10 * uTemperature;
    color.g += 0.06 * uTint;
    color.r -= 0.03 * uTint;
    color.b -= 0.03 * uTint;

    // 4. Contrast
    if (abs(uContrast - 1.0) > 0.01) {
        color = clamp((color - 0.5) * uContrast + 0.5, 0.0, 1.0);
    }

    // 5. Saturation
    luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    if (abs(uSaturation - 1.0) > 0.01) {
        color = mix(vec3(luma), color, uSaturation);
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 6. Gamma
    if (abs(uGamma - 1.0) > 0.01) {
        color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 7. Shadow Tint
    float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
    color += uShadowTint * shadowWeight;

    // 8. Filmic Highlight Rolloff (Soft-shoulder exponential compression preventing harsh digital clipping)
    if (uHighlightRollOff < 0.99) {
        float knee = max(0.01, 1.0 - uHighlightRollOff);
        vec3 over = max(vec3(0.0), color - vec3(uHighlightRollOff));
        color = min(color, vec3(uHighlightRollOff)) + knee * (vec3(1.0) - exp(-over / knee));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    // 9. Film Fade
    if (uFade > 0.001) {
        color = color * (1.0 - uFade) + uFade;
    }

    // 10. Localized Film Halation (Red-orange highlight glow spreading into adjacent emulsion)
    if (uHalationEnabled && uHalationIntensity > 0.01) {
        vec2 hStep = vec2(0.0035);
        vec4 tapL = texture(sTexture, uv - vec2(hStep.x, 0.0));
        vec4 tapR = texture(sTexture, uv + vec2(hStep.x, 0.0));
        vec4 tapT = texture(sTexture, uv - vec2(0.0, hStep.y));
        vec4 tapB = texture(sTexture, uv + vec2(0.0, hStep.y));

        vec2 diag = hStep * 0.7071;
        vec4 tapTL = texture(sTexture, uv + vec2(-diag.x, -diag.y));
        vec4 tapTR = texture(sTexture, uv + vec2( diag.x, -diag.y));
        vec4 tapBL = texture(sTexture, uv + vec2(-diag.x,  diag.y));
        vec4 tapBR = texture(sTexture, uv + vec2( diag.x,  diag.y));

        float lL = dot(tapL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lR = dot(tapR.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lT = dot(tapT.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lB = dot(tapB.rgb, vec3(0.2126, 0.7152, 0.0722));

        float lTL = dot(tapTL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lTR = dot(tapTR.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lBL = dot(tapBL.rgb, vec3(0.2126, 0.7152, 0.0722));
        float lBR = dot(tapBR.rgb, vec3(0.2126, 0.7152, 0.0722));

        float kThresh = uHalationThreshold;
        float hCenter = max(0.0, luma - kThresh);
        float hCross = max(0.0, lL - kThresh) + max(0.0, lR - kThresh) + max(0.0, lT - kThresh) + max(0.0, lB - kThresh);
        float hDiag = max(0.0, lTL - kThresh) + max(0.0, lTR - kThresh) + max(0.0, lBL - kThresh) + max(0.0, lBR - kThresh);

        float spreadHighlight = (hCenter * 0.30 + hCross * 0.10 + hDiag * 0.075) / max(0.01, 1.0 - kThresh);
        if (spreadHighlight > 0.001) {
            vec3 haloColor = vec3(1.0, 0.28, 0.05) * (spreadHighlight * uHalationIntensity * 0.40);
            color += haloColor;
        }
    }

    // 11. Subtle Edge Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float leakDist = length(uv - vec2(0.95, 0.05));
        float leakMask = smoothstep(0.45, 0.05, leakDist);
        vec3 leakColor = vec3(1.0, 0.50, 0.15) * (leakMask * uLightLeakIntensity * 0.50);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 12. Lens Vignette (matches ImageProcessor.kt quadratic falloff)
    if (uVignetteIntensity > 0.01) {
        float dist = length(p) / 0.7071068;
        float dn = dist / uVignetteRadius;
        float vignette = clamp(1.0 - uVignetteIntensity * dn * dn, 0.25, 1.0);
        color *= vignette;
    }

    // 13. Authentic Multi-Octave Tri-Pack Film Grain (Channel dye clumping)
    if (uGrainIntensity > 0.005) {
        float sRise = smoothstep(0.0, 0.42, luma);
        float hFall = smoothstep(0.42, 1.0, luma);
        float grainWeight = (0.28 + 0.72 * sRise) * (1.0 - 0.88 * hFall);

        if (color.r > color.g && color.g > color.b && color.r > 0.25 && color.r < 0.90) {
            float skinWeight = clamp((color.r - color.b) * 2.0, 0.0, 1.0);
            grainWeight *= (1.0 - 0.35 * skinWeight);
        }

        vec2 grainCoord = uv * 1400.0 * (uGrainScale * 0.45);
        float fineG = filmNoise(grainCoord, uRandomSeed + 0.18);
        float clumpG = filmNoise(grainCoord * 0.45, uRandomSeed + 0.42);
        float noiseG = fineG * 0.85 + clumpG * 0.15;

        float fineR = filmNoise(grainCoord, uRandomSeed + 0.31);
        float clumpR = filmNoise(grainCoord * 0.45, uRandomSeed + 0.67);
        float noiseR = fineR * 0.75 + clumpR * 0.25;

        float fineB = filmNoise(grainCoord, uRandomSeed + 0.53);
        float clumpB = filmNoise(grainCoord * 0.45, uRandomSeed + 0.89);
        float noiseB = fineB * 0.65 + clumpB * 0.35;

        vec3 grainDelta;
        if (uIsColorNoise) {
            grainDelta = vec3(noiseR, noiseG, noiseB);
        } else {
            grainDelta = mix(vec3(noiseG), vec3(noiseR, noiseG, noiseB), 0.18);
        }

        color += grainDelta * (grainWeight * uGrainIntensity * 0.42);
    }

    // 13. Retro Camcorder Scanlines
    if (uScanlineIntensity > 0.01) {
        float scanline = sin(uv.y * 1000.0) * 0.5 + 0.5;
        color *= mix(1.0, 0.82 + 0.18 * scanline, uScanlineIntensity);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    fun getLutAssetForPreset(presetId: String): String = when (presetId) {
        "analog_35mm_classic"  -> "lut_analog_35mm_classic.png"
        "analog_warm_film"     -> "lut_analog_warm_film.png"
        "analog_soft_film"     -> "lut_analog_soft_film.png"
        "analog_vintage_color" -> "lut_analog_vintage_color.png"
        "analog_faded_film"    -> "lut_analog_faded_film.png"
        "analog_cinema_film"   -> "lut_analog_cinema_film.png"
        "ccd_classic"          -> "lut_ccd_classic.png"
        "ccd_flash"            -> "lut_ccd_flash.png"
        "ccd_cool_digital"     -> "lut_ccd_cool_digital.png"
        "ccd_y2k_digital"      -> "lut_ccd_y2k_digital.png"
        "ccd_retro_compact"    -> "lut_ccd_retro_compact.png"
        "disp_classic"         -> "lut_disp_classic.png"
        "disp_flash"           -> "lut_disp_flash.png"
        "disp_warm"            -> "lut_disp_warm.png"
        "disp_night"           -> "lut_disp_night.png"
        "inst_classic"         -> "lut_inst_classic.png"
        "inst_soft_polaroid"   -> "lut_inst_soft_polaroid.png"
        "inst_vintage"         -> "lut_inst_vintage.png"
        "cam_retro_vhs"        -> "lut_cam_retro_vhs.png"
        "cam_analog_video"     -> "lut_cam_analog_video.png"
        "cam_90s_camcorder"    -> "lut_cam_90s_camcorder.png"
        else                   -> "lut_analog_35mm_classic.png"
    }
}

