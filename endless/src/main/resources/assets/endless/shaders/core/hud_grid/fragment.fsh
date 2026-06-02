#version 150

#moj_import <endless:common.glsl>

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform vec4 ColorModulator;
uniform float Cell;
uniform float LineWidth;
uniform vec4 GridColor;
uniform float Time;

out vec4 OutColor;

void main() {
    // SDF-альфа для скруглений
    vec2 center = Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius);
    float maskAlpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    if (maskAlpha <= 0.0) discard;

    // Координаты в пикселях относительно прямоугольника
    vec2 px = FragCoord * Size;

    // Волновое смещение сетки (двигается по диагонали)
    float wave = sin((px.x + px.y) * 0.045 + Time * 1.2) * 4.0;
    vec2 shifted = px + vec2(wave, -wave * 0.6);

    // Сетка
    vec2 cellPos = mod(shifted, Cell);
    float halfLine = LineWidth * 0.5;
    float distX = min(cellPos.x, Cell - cellPos.x);
    float distY = min(cellPos.y, Cell - cellPos.y);
    float dGrid = min(distX, distY);
    float lineAlpha = 1.0 - smoothstep(halfLine - 0.5, halfLine + 0.5, dGrid);
    if (lineAlpha <= 0.001) discard;

    // Локальное окно «света»: сетка видна только внутри пятна, движущегося по плоскости
    vec2 spotCenter = Size * 0.5
                    + vec2(sin(Time * 0.45), cos(Time * 0.55)) * Size * 0.18;
    float spotRadius = max(Size.x, Size.y) * 0.45;
    float spot = 1.0 - smoothstep(spotRadius * 0.55, spotRadius, length(px - spotCenter));

    // Лёгкая бегущая полоска поверх (затемнение и подсветка участка)
    float band = 0.4 + 0.6 * smoothstep(-0.3, 1.3, sin((px.y + Time * 30.0) * 0.025));

    float final = lineAlpha * maskAlpha * spot * band;
    if (final <= 0.001) discard;

    vec4 col = vec4(GridColor.rgb, GridColor.a * final);
    OutColor = col * ColorModulator;
}
