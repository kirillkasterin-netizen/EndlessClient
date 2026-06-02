#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform vec4 ColorModulator;
uniform vec4 Color;
uniform vec4 PatternColor;
uniform float Time;
uniform float Scale;

out vec4 OutColor;

#define TAU 6.28318530718
#define MAX_ITER 5

void main() {
    float time = Time * 0.5 + 23.0;
    vec2 uv = TexCoord * Scale;
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 i = vec2(p);
    float c = 1.0;
    float inten = 0.005;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = time * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
    }

    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);
    float pattern = clamp(pow(abs(c), 8.0), 0.0, 1.0);

    vec3 finalRGB = mix(Color.rgb, PatternColor.rgb, pattern);
    OutColor = vec4(finalRGB, Color.a) * ColorModulator;
}
