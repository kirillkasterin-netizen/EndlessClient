#version 150

uniform sampler2D Sampler0;
uniform vec2 location;
uniform vec2 texSize;
uniform vec2 size;
uniform vec4 round;
uniform vec4 color;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {
    float dis = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, round);
    float alpha = 1.0 - smoothstep(-1.0, 1.0, dis);

    vec4 c = texture(Sampler0, (gl_FragCoord.xy * 0.5 + 0.5) / texSize);

    fragColor = vec4(c.rgb + color.rgb, c.a * alpha * color.a);
}