#version 150

#moj_import <endless:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform vec4 ColorModulator;
uniform float Time;
uniform float Density;
uniform vec4 DotColor;

out vec4 OutColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    // SDF-альфа для скруглений
    vec2 center = Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius);
    float maskAlpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    if (maskAlpha <= 0.0) discard;

    // Сетка точек: ячейки + случайные позиции
    vec2 uv = FragCoord * Size / max(Size.y, 1.0) * Density;
    // лёгкий drift
    uv += vec2(Time * 0.05, Time * 0.025);

    vec2 cellId = floor(uv);
    vec2 cellUv = fract(uv) - 0.5;

    float rnd = hash21(cellId);
    vec2 jitter = vec2(rnd, hash21(cellId + 11.7)) - 0.5;
    float dotRadius = mix(0.05, 0.14, fract(rnd * 17.13));

    float d = length(cellUv - jitter * 0.6);
    float dot = smoothstep(dotRadius, dotRadius * 0.55, d);

    // мягкое мерцание
    float blink = 0.5 + 0.5 * sin(Time * 1.4 + rnd * 6.2831);
    blink = mix(0.25, 1.0, blink);

    float intensity = dot * blink;
    if (intensity <= 0.001) discard;

    vec4 col = vec4(DotColor.rgb, DotColor.a * intensity * maskAlpha);
    OutColor = col * ColorModulator;
}
