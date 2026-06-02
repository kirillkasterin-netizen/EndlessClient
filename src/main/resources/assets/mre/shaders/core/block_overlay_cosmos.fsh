#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash23(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash23(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash23(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash23(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash23(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash23(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash23(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash23(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash23(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);

    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec3(1.7, -2.1, 2.5);
        amplitude *= 0.52;
    }
    return value;
}

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    vec3 local = vec3(uv, 0.0);
    vec3 cell = vec3(0.0, 0.0, 0.0);
    float seed = 0.37;

    float t = Time * 0.45;
    vec3 field = local * (4.0 + seed * 1.4);
    vec3 p1 = field + vec3(t * 0.18, -t * 0.10, t * 0.22) + seed * 5.0;
    vec3 p2 = field.yzx * 1.7 + vec3(-t * 0.24, t * 0.16, -t * 0.13) + 9.0;
    vec3 p3 = field.zxy * 2.4 + vec3(t * 0.12, t * 0.19, -t * 0.15) + seed * 13.0;

    float nebulaA = fbm(p1);
    float nebulaB = fbm(p2);
    float nebulaC = fbm(p3);
    float bands = sin((local.x + local.y - local.z) * 10.0 + t * 2.2 + nebulaB * 3.5) * 0.5 + 0.5;
    float gas = smoothstep(0.35, 0.92, nebulaA * 0.75 + nebulaB * 0.35 + bands * 0.25);

    vec3 deepSpace = vec3(0.03, 0.05, 0.12);
    vec3 violetDust = vec3(0.24, 0.10, 0.62);
    vec3 cyanMist = vec3(0.06, 0.62, 0.82);
    vec3 roseGlow = vec3(0.76, 0.22, 0.48);

    vec3 nebulaColor = mix(deepSpace, violetDust, gas);
    nebulaColor = mix(nebulaColor, cyanMist, smoothstep(0.45, 1.0, nebulaB));
    nebulaColor += roseGlow * pow(max(nebulaC - 0.72, 0.0), 2.6) * 1.15;

    vec3 starGrid = floor((local + 0.5) * 7.0 + seed * 19.0);
    float starSeed = hash23(starGrid + cell * 0.31);
    float starMask = smoothstep(0.992, 0.9994, starSeed);
    float flicker = 0.80 + 0.20 * sin(Time * (2.0 + seed * 1.5) + starSeed * 30.0);
    vec3 stars = vec3(0.75, 0.84, 1.0) * starMask * flicker * 0.75;

    float rim = pow(1.0 - clamp(length(local) * 1.45, 0.0, 1.0), 1.6);
    vec3 finalColor = nebulaColor + stars + cyanMist * rim * 0.10;

    float alpha = clamp(0.24 + gas * 0.52 + starMask * 0.12, 0.0, 0.88) * vertexColor.a;
    fragColor = vec4(finalColor * vertexColor.rgb, alpha);
}
