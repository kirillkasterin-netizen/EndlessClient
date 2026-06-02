#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform vec4 ColorModulator;

out vec4 OutColor;

void main() {
    // UV centred at (0.5, 0.5); convert to range [-1, 1]
    vec2 uv = (TexCoord - 0.5) * 2.0;
    float dist = length(uv);

    float coreGlow  = exp(-dist * dist * 4.0);
    float outerGlow = exp(-dist * dist * 0.8) * 0.3;
    float softEdge  = smoothstep(1.2, 0.0, dist);

    float glow  = (coreGlow * 0.7 + outerGlow) * softEdge;
    float alpha = glow * FragColor.a;

    if (alpha < 0.005) discard;

    OutColor = vec4(FragColor.rgb, alpha) * ColorModulator;
}
