#version 150

uniform sampler2D Sampler0;
uniform float HdrMode;  // 0 = SDR, 1 = PQ (HDR10), 2 = HLG

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

// PQ (SMPTE ST 2084) EOTF - HDR10 标准
vec3 pqToLinear(vec3 pq) {
    const float m1 = 0.1593017578125;
    const float m2 = 78.84375;
    const float c1 = 0.8359375;
    const float c2 = 18.8515625;
    const float c3 = 18.6875;
    
    vec3 p = pow(max(pq, vec3(0.0)), vec3(1.0 / m2));
    vec3 num = max(p - c1, vec3(0.0));
    vec3 den = c2 - c3 * p;
    return pow(num / den, vec3(1.0 / m1)) * 10000.0;
}

// HLG OETF 逆变换
vec3 hlgToLinear(vec3 hlg) {
    const float a = 0.17883277;
    const float b = 0.28466892;
    const float c = 0.55991073;
    
    vec3 linear;
    for (int i = 0; i < 3; i++) {
        float v = hlg[i];
        if (v <= 0.5) {
            linear[i] = (v * v) / 3.0;
        } else {
            linear[i] = (exp((v - c) / a) + b) / 12.0;
        }
    }
    return linear * 1000.0;
}

// ACES 电影色调映射
vec3 acesFilm(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// 线性到 sRGB gamma
vec3 linearToSrgb(vec3 linear) {
    vec3 result;
    for (int i = 0; i < 3; i++) {
        float v = linear[i];
        if (v <= 0.0031308) {
            result[i] = v * 12.92;
        } else {
            result[i] = 1.055 * pow(v, 1.0 / 2.4) - 0.055;
        }
    }
    return result;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    vec3 rgb = color.rgb;
    
    if (HdrMode > 0.5) {
        vec3 linearHdr;
        
        if (HdrMode < 1.5) {
            // PQ (HDR10)
            linearHdr = pqToLinear(rgb);
        } else {
            // HLG
            linearHdr = hlgToLinear(rgb);
        }
        
        // 归一化到 SDR 范围
        linearHdr = linearHdr / 100.0;
        
        // ACES 色调映射
        vec3 mapped = acesFilm(linearHdr);
        
        // 转换到 sRGB
        rgb = linearToSrgb(mapped);
    }
    
    fragColor = vec4(rgb, color.a) * vertexColor;
}
