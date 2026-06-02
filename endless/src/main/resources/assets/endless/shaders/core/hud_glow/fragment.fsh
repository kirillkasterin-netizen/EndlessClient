#version 150

#moj_import <endless:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Spread;
uniform float Intensity;
uniform vec4 ColorModulator;
uniform vec4 TopLeftColor;
uniform vec4 TopRightColor;
uniform vec4 BottomLeftColor;
uniform vec4 BottomRightColor;

out vec4 OutColor;

vec4 sampleGradient(vec2 uv) {
    vec4 top    = mix(TopLeftColor,    TopRightColor,    uv.x);
    vec4 bottom = mix(BottomLeftColor, BottomRightColor, uv.x);
    return mix(top, bottom, uv.y);
}

void main() {
    vec2 center = Size * 0.5;
    vec2 pos = (FragCoord * Size) - center;

    // SDF расстояние от точки до прямоугольника со скруглениями
    float d = roundedBoxSDF(pos, center - Spread, Radius);

    // Внутри хулла свечения нет, чтобы не подсвечивать сам элемент
    if (d <= 0.0) discard;

    float t = d / max(Spread, 0.001);
    // Гауссиан + лёгкое экспоненциальное хало = мягкий blur
    float core = exp(-t * t * 3.2);
    float halo = exp(-t * 1.6) * 0.45;
    float falloff = clamp(core + halo, 0.0, 1.0);
    float alpha = falloff * Intensity;

    if (alpha <= 0.001) discard;

    vec4 grad = sampleGradient(FragCoord);
    OutColor = vec4(grad.rgb, grad.a * alpha) * ColorModulator;
}
