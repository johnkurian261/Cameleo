package com.craftcam.app.gl.shaders

object ShaderConstants {

    const val VERTEX_SHADER_VIEWFINDER = """#version 300 es
layout (location = 0) in vec4 aPosition;
layout (location = 1) in vec4 aTextureCoord;

uniform mat4 uTexMatrix;
out vec2 vTextureCoord;

void main() {
    gl_Position = aPosition;
    vTextureCoord = (uTexMatrix * aTextureCoord).xy;
}
"""

    const val FRAGMENT_SHADER_VIEWFINDER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES sTexture;
uniform sampler2D uLutTexture;
uniform bool uLutEnabled;
uniform vec2 uViewportSize;
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

// High-entropy 2D pseudo-random hash
float hash(vec2 p, float seed) {
    return fract(sin(dot(p, vec2(12.9898, 78.233)) + seed) * 43758.5453) - 0.5;
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
    
    vec2 uv0 = vec2(px0_x / 512.0, 1.0 - (px0_y / 512.0));
    vec2 uv1 = vec2(px1_x / 512.0, 1.0 - (px1_y / 512.0));
    
    vec3 col0 = texture(lut, uv0).rgb;
    vec3 col1 = texture(lut, uv1).rgb;
    
    return mix(col0, col1, blueFract);
}

void main() {
    vec2 uv = vTextureCoord;
    vec2 p = uv - 0.5;
    float r2 = dot(p, p);

    // 1. Lens Distortion (Barrel/Pincushion)
    if (abs(uBarrelDistortion) > 0.001) {
        uv = 0.5 + p * (1.0 + uBarrelDistortion * r2 * 0.4);
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }

    // 2. Transverse Chromatic Aberration
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

    // 2b. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
        
        // Live user micro-tuning offsets (when sliders adjusted)
        if (abs(uTemperature) > 0.001) {
            color.r += 0.08 * uTemperature;
            color.b -= 0.08 * uTemperature;
        }
        if (abs(uTint) > 0.001) {
            color.g += 0.06 * uTint;
            color.r -= 0.03 * uTint;
            color.b -= 0.03 * uTint;
        }
        if (abs(uContrast - 1.0) > 0.01) {
            color = (color - 0.5) * uContrast + 0.5;
        }
        if (abs(uSaturation - 1.0) > 0.01) {
            float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            color = mix(vec3(luma), color, uSaturation);
        }
    } else {
        // Fallback analytical color grading when LUT is unavailable
        color.r += 0.12 * uTemperature;
        color.b -= 0.12 * uTemperature;
        color.g += 0.10 * uTint;
        color.r -= 0.05 * uTint;
        color.b -= 0.05 * uTint;

        color = (color - 0.5) * uContrast + 0.5;
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        color = mix(vec3(luma), color, uSaturation);
        color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
        color = color * (1.0 - uFade) + uFade;

        float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
        color += uShadowTint * shadowWeight;

        if (luma > uHighlightRollOff) {
            float excess = luma - uHighlightRollOff;
            color -= vec3(excess * 0.25);
        }
    }

    // 9. Halation (Film red glow around high-contrast speculars)
    if (uHalationEnabled && luma > uHalationThreshold) {
        float haloAmount = (luma - uHalationThreshold) / (1.0 - uHalationThreshold);
        vec3 haloColor = vec3(1.0, 0.22, 0.04) * (haloAmount * uHalationIntensity);
        color += haloColor;
    }

    // 10. Light Leak Emulation
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float leakDist = length(uv - vec2(0.95, 0.05));
        float leakMask = smoothstep(0.85, 0.15, leakDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.08) * (leakMask * uLightLeakIntensity);
        // Screen blend
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 11. Lens Vignette
    if (uVignetteIntensity > 0.01) {
        vec2 screenP = (gl_FragCoord.xy / uViewportSize) - 0.5;
        screenP.x *= (uViewportSize.x / uViewportSize.y);
        float dist = length(screenP) * 1.8;
        float vignette = smoothstep(uVignetteRadius, uVignetteRadius - uVignetteIntensity, dist);
        color *= vignette;
    }

    // 12. Analog Film Grain (Mid-tone density weighted)
    if (uGrainIntensity > 0.005) {
        float grainWeight = clamp(1.0 - 4.0 * pow(luma - 0.5, 2.0), 0.0, 1.0);
        vec2 grainUV = uv * (uGrainScale * 600.0);
        if (uIsColorNoise) {
            vec3 noise = vec3(
                hash(grainUV, uRandomSeed + 1.1),
                hash(grainUV, uRandomSeed + 2.7),
                hash(grainUV, uRandomSeed + 4.3)
            );
            color += noise * (grainWeight * uGrainIntensity);
        } else {
            float noise = hash(grainUV, uRandomSeed);
            color += vec3(noise * grainWeight * uGrainIntensity);
        }
    }

    // 13. Retro Camcorder Scanlines
    if (uScanlineIntensity > 0.01) {
        float scanline = sin(gl_FragCoord.y * 1.5) * 0.5 + 0.5;
        color *= mix(1.0, 0.82 + 0.18 * scanline, uScanlineIntensity);
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

float hash(vec2 p, float seed) {
    return fract(sin(dot(p, vec2(12.9898, 78.233)) + seed) * 43758.5453) - 0.5;
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
    
    vec2 uv0 = vec2(px0_x / 512.0, 1.0 - (px0_y / 512.0));
    vec2 uv1 = vec2(px1_x / 512.0, 1.0 - (px1_y / 512.0));
    
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

    // 2b. Photochemical 3D Film Emulsion LUT
    if (uLutEnabled) {
        color = sampleHaldLut(uLutTexture, color);
    }

    // 3. White Balance
    color.r += 0.12 * uTemperature;
    color.b -= 0.12 * uTemperature;
    color.g += 0.10 * uTint;
    color.r -= 0.05 * uTint;
    color.b -= 0.05 * uTint;

    // 4. Contrast
    color = (color - 0.5) * uContrast + 0.5;

    // 5. Saturation
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, uSaturation);

    // 6. Gamma
    color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));

    // 7. Film Fade
    color = color * (1.0 - uFade) + uFade;

    // 8. Shadow Tint and Highlight Roll-off
    float shadowWeight = clamp(1.0 - luma * 3.0, 0.0, 1.0);
    color += uShadowTint * shadowWeight;
    if (luma > uHighlightRollOff) {
        float excess = luma - uHighlightRollOff;
        color -= vec3(excess * 0.25);
    }

    // 9. Halation
    if (uHalationEnabled && luma > uHalationThreshold) {
        float haloAmount = (luma - uHalationThreshold) / (1.0 - uHalationThreshold);
        vec3 haloColor = vec3(1.0, 0.22, 0.04) * (haloAmount * uHalationIntensity);
        color += haloColor;
    }

    // 10. Light Leak
    if (uLightLeakEnabled && uLightLeakIntensity > 0.01) {
        float leakDist = length(uv - vec2(0.95, 0.05));
        float leakMask = smoothstep(0.85, 0.15, leakDist);
        vec3 leakColor = vec3(1.0, 0.45, 0.08) * (leakMask * uLightLeakIntensity);
        color = 1.0 - (1.0 - color) * (1.0 - leakColor);
    }

    // 11. Lens Vignette
    if (uVignetteIntensity > 0.01) {
        float dist = length(p);
        float vignette = smoothstep(uVignetteRadius, uVignetteRadius - uVignetteIntensity, dist);
        color *= vignette;
    }

    // 12. Photographic Grain
    if (uGrainIntensity > 0.005) {
        float grainWeight = clamp(1.0 - 4.0 * pow(luma - 0.5, 2.0), 0.0, 1.0);
        vec2 grainUV = uv * (uGrainScale * 1200.0);
        if (uIsColorNoise) {
            vec3 noise = vec3(
                hash(grainUV, uRandomSeed + 1.1),
                hash(grainUV, uRandomSeed + 2.7),
                hash(grainUV, uRandomSeed + 4.3)
            );
            color += noise * (grainWeight * uGrainIntensity);
        } else {
            float noise = hash(grainUV, uRandomSeed);
            color += vec3(noise * grainWeight * uGrainIntensity);
        }
    }

    // 13. Retro Camcorder Scanlines
    if (uScanlineIntensity > 0.01) {
        float scanline = sin(uv.y * 1000.0) * 0.5 + 0.5;
        color *= mix(1.0, 0.82 + 0.18 * scanline, uScanlineIntensity);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""
}

