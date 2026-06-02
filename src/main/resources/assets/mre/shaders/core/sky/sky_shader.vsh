#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 TexCoord;
out vec4 FragColor;

// Position is given as NDC (-1..1). We pin gl_Position.z = w so the resulting
// NDC z is +1 (the far plane). With GL_LEQUAL depth-test the quad will only
// pass where the depth buffer has not been written by world geometry.
void main() {
    gl_Position = vec4(Position.xy, 1.0, 1.0);
    TexCoord = Position.xy * 0.5 + 0.5;
    FragColor = Color;
}
