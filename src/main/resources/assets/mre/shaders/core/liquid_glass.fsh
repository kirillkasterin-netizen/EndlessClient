#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;
in vec2 ScreenPos;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform vec2 ScreenSize;
uniform vec2 MousePos;
uniform float Time;

out vec4 OutColor;

// --- Adapted in spirit from https://www.shadertoy.com/view/3cdXDX ---
// Liquid Glass: animated wobble + edge refraction + chromatic aberration
// + frosted micro-blur + rim highlight + soft inner gloss.

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 uv = TexCoord;
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 texelSize = 1.0 / texSize;

    float t = Time * 0.65;

    // Animated liquid wobble of the sample coords.
    vec2 wobble;
    wobble.x = sin(uv.y * 14.0 + t * 1.30) * 0.0022;
    wobble.y = cos(uv.x * 12.0 + t * 1.10) * 0.0022;
    float n = vnoise(uv * 8.0 + t * 0.45);
    wobble += (n - 0.5) * 0.0028;

    // Edge-aware refraction.
    vec2 d = min(uv, 1.0 - uv);
    float edge = min(d.x, d.y);
    float edgeFade = smoothstep(0.32, 0.0, edge);
    float refrAmt = edgeFade * 0.014 + 0.0015;
    vec2 fromCenter = uv - 0.5;
    vec2 refrDir = normalize(fromCenter + vec2(1e-5));
    vec2 refrOffset = refrDir * refrAmt;

    vec2 sUV = uv + wobble + refrOffset;

    // Chromatic aberration near edges only.
    float caStrength = edgeFade * 0.0040;
    vec3 col;
    col.r = texture(Sampler0, sUV + refrDir * caStrength).r;
    col.g = texture(Sampler0, sUV).g;
    col.b = texture(Sampler0, sUV - refrDir * caStrength).b;
    float a = texture(Sampler0, sUV).a;

    // Frosted micro-blur (3x3) mixed in.
    vec3 blur = vec3(0.0);
    float wsum = 0.0;
    for (float bx = -1.0; bx <= 1.0; bx++) {
        for (float by = -1.0; by <= 1.0; by++) {
            float w = 1.0 - 0.35 * (abs(bx) + abs(by));
            blur += texture(Sampler0, sUV + vec2(bx, by) * texelSize * 1.5).rgb * w;
            wsum += w;
        }
    }
    blur /= max(wsum, 1e-4);
    col = mix(col, blur, 0.35);

    // Edge rim highlight + soft inner shadow in pixels.
    float edgePx = edge * min(Size.x, Size.y);
    float rim = exp(-edgePx / 5.0);
    float inner = smoothstep(8.0, 30.0, edgePx);
    col += rim * 0.18;
    col *= 0.92 + 0.08 * inner;

    // Specular gloss spot in upper-left quadrant.
    vec2 gp = uv - vec2(0.25, 0.20);
    float gloss = exp(-dot(gp, gp) * 9.0) * 0.20;
    col += gloss;

    // Subtle cool tint.
    col *= vec3(1.00, 1.02, 1.05);

    vec4 result = vec4(col, max(a, 0.001));
    result *= FragColor;
    result.a *= ralpha(Size, FragCoord, Radius, Smoothness);

    if (result.a <= 0.0) discard;

    OutColor = result;
}
