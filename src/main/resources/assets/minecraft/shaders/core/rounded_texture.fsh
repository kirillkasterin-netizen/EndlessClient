#version 150

uniform sampler2D Sampler0;
uniform vec2 size;
uniform vec2 location;
uniform vec4 radius;
uniform vec2 screenSize;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, vec4 radius) {
    radius.xy = (center.x > 0.0) ? radius.xy : radius.zw;
    radius.x  = (center.y > 0.0) ? radius.x : radius.y;

    vec2 q = abs(center) - size + radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius.x;
}

void main() {

    float distance = roundedBoxSDF(gl_FragCoord.xy - location - (size / 2.0), size / 2.0, radius);
    

    float smoothedAlpha = 1.0 - smoothstep(-1.0, 1.0, distance);
    

    vec2 uv = gl_FragCoord.xy / screenSize;
    

    vec4 texColor = texture(Sampler0, uv);
    

    fragColor = vec4(texColor.rgb, smoothedAlpha);
}
