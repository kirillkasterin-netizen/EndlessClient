#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

out vec4 OutColor;

void main() {
    vec4 tex = texture(Sampler0, TexCoord);
    if (tex.a <= 0.001) discard;

    // Берём только альфу из текстуры, RGB — из FragColor (цвет темы),
    // позволяя перекрашивать чёрные/любые силуэты в выбранный цвет.
    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * tex.a);
    OutColor = finalColor * ColorModulator;
}
