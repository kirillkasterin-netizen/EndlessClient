#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

// Noise function для звезд
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy * 0.01;
    
    // Космический фон с туманностями
    vec2 nebulaUV = uv * 2.0;
    float nebula1 = noise(nebulaUV + Time * 0.1);
    float nebula2 = noise(nebulaUV * 1.5 - Time * 0.15);
    float nebula3 = noise(nebulaUV * 0.8 + Time * 0.08);
    
    vec3 nebulaColor1 = vec3(0.4, 0.1, 0.8) * nebula1;
    vec3 nebulaColor2 = vec3(0.1, 0.3, 0.9) * nebula2;
    vec3 nebulaColor3 = vec3(0.6, 0.2, 0.9) * nebula3;
    
    vec3 nebula = nebulaColor1 + nebulaColor2 * 0.5 + nebulaColor3 * 0.3;
    
    // Звезды
    vec2 starUV = uv * 20.0;
    float stars = hash(floor(starUV));
    stars = pow(stars, 15.0) * (sin(Time * 3.0 + stars * 10.0) * 0.5 + 0.5);
    
    // Пульсация
    float pulse = sin(Time * 2.0) * 0.2 + 0.8;
    
    vec3 finalColor = (nebula + stars) * pulse;
    float alpha = (0.6 + pulse * 0.4);
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}
