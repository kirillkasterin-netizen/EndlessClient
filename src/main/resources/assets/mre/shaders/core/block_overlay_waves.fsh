#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(234.34, 435.35));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rot = mat2(1.7, -1.2, 1.2, 1.7);

    for (int i = 0; i < 4; i++) {
        value += noise(p) * amplitude;
        p = rot * p * 1.35 + 2.4;
        amplitude *= 0.55;
    }

    return value;
}

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    float t = Time * 1.15;

    vec2 drift1 = vec2(t * 0.42, -t * 0.18);
    vec2 drift2 = vec2(-t * 0.24, t * 0.36);

    float layerA = sin((uv.x * 5.0 + uv.y * 1.2) - t * 1.7);
    float layerB = sin((-uv.x * 2.1 + uv.y * 7.4) + t * 2.3);
    float layerC = cos(length(uv * vec2(1.15, 0.85)) * 9.0 - t * 3.6);

    float turbulence = fbm(uv * 2.6 + drift1) * 2.0 - 1.0;
    float interference = fbm((uv.yx + vec2(2.0, -1.4)) * 3.1 + drift2) * 2.0 - 1.0;

    float waveField = layerA * 0.34 + layerB * 0.27 + layerC * 0.19 + turbulence * 0.13 + interference * 0.07;
    float normalized = waveField * 0.5 + 0.5;

    float ridge = smoothstep(0.60, 0.88, normalized);
    float foam = smoothstep(0.80, 0.98, normalized + turbulence * 0.16);
    float deepMask = smoothstep(0.05, 0.55, normalized);

    vec3 deep = vec3(0.015, 0.075, 0.165);
    vec3 mid = vec3(0.020, 0.255, 0.430);
    vec3 crest = vec3(0.100, 0.690, 0.820);
    vec3 foamColor = vec3(0.800, 0.965, 1.000);

    vec3 color = mix(deep, mid, deepMask);
    color = mix(color, crest, ridge * 0.82);
    color += foamColor * foam * 0.48;

    float caustic = max(0.0, sin((uv.x + turbulence * 0.35) * 16.0 + t * 2.8) * cos((uv.y - interference * 0.28) * 13.0 - t * 2.1));
    color += vec3(0.05, 0.16, 0.18) * pow(caustic, 5.5) * 0.55;

    float vignette = 1.0 - smoothstep(0.45, 1.25, length(uv));
    color += crest * vignette * 0.08;

    float alpha = (0.26 + ridge * 0.24 + foam * 0.16 + vignette * 0.06) * vertexColor.a;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.78));
}
