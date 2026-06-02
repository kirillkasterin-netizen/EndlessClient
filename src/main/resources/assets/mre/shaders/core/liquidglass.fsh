#version 150

#moj_import <mre:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float CornerSmoothness;
uniform float GlobalAlpha;

uniform float FresnelPower;
uniform vec3 FresnelColor;
uniform float FresnelAlpha;
uniform float BaseAlpha;
uniform bool FresnelInvert;
uniform float FresnelMix;
uniform float EdgeWidth;
uniform float DistortStrength;
uniform float DistortUniformity;

uniform vec2 ScreenLocation;
uniform vec2 ScreenSize;

out vec4 OutColor;

float roundedBoxSDF(vec2 pos, vec2 size, vec4 radius, float smoothness) {
    radius.xy = (pos.x > 0.0) ? radius.xy : radius.wz;
    radius.x = (pos.y > 0.0) ? radius.x : radius.y;

    vec2 v = abs(pos) - size + radius.x;
    vec2 vClamped = max(v, 0.0);
    float len = pow(pow(vClamped.x, smoothness) + pow(vClamped.y, smoothness), 1.0 / smoothness);
    return min(max(v.x, v.y), 0.0) + len - radius.x;
}

void main() {
    vec2 center = Size * 0.5;
    vec2 boxHalfSize = center - 1.0;
    vec2 pos = (FragCoord * Size) - center;

    float distance = roundedBoxSDF(-pos, boxHalfSize, Radius, CornerSmoothness);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);

    // Глубина от внутренней кромки фигуры. Чем ближе к краю, тем сильнее эффект.
    float distToInnerEdge = max(-distance, 0.0);
    float edgeFactor = 1.0 - clamp(distToInnerEdge / max(EdgeWidth, 0.001), 0.0, 1.0);
    float base = FresnelInvert ? (1.0 - edgeFactor) : edgeFactor;
    float fresnel = pow(clamp(base, 0.0, 1.0), max(FresnelPower, 0.001));

    vec2 dir = length(pos) > 0.0001 ? normalize(pos) : vec2(0.0, 0.0);
    vec2 screenTexCoord = gl_FragCoord.xy / ScreenSize;

    // Эффект остаётся у кромки: uniformity только смягчает спад, но не тащит его в центр.
    float distortMask = mix(fresnel, sqrt(max(fresnel, 0.0)), DistortUniformity);
    float distortAmount = DistortStrength * distortMask;
    vec2 distortedTexCoord = screenTexCoord + dir * distortAmount;

    vec4 texColor = texture(Sampler0, distortedTexCoord) * FragColor;
    vec3 finalColor = mix(texColor.rgb, FresnelColor, fresnel * FresnelMix);
    float finalAlpha = mix(BaseAlpha, FresnelAlpha, fresnel) * alpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha * GlobalAlpha);
}
