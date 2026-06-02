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

void main() {
    vec2 uv = TexCoord;
    
    // Простое размытие 3x3
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec4 color = vec4(0.0);
    float total = 0.0;
    
    for (float x = -1.0; x <= 1.0; x++) {
        for (float y = -1.0; y <= 1.0; y++) {
            vec2 offset = vec2(x, y) * texelSize;
            color += texture(Sampler0, uv + offset);
            total += 1.0;
        }
    }
    color /= total;
    
    // Применяем цвет и альфа-канал
    color *= FragColor;
    color.a *= ralpha(Size, FragCoord, Radius, Smoothness);
    
    if (color.a == 0.0) {
        discard;
    }
    
    OutColor = color;
}
