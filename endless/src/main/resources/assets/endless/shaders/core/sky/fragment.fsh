#version 150

uniform float iTime;
uniform vec2  iResolution;
uniform float iSpeed;
uniform vec3  iColor;
uniform vec3  iColor2;
uniform int   iMode;

// Camera state — used to build a world-space view direction from each pixel.
uniform float iYaw;     // radians
uniform float iPitch;   // radians
uniform float iFov;     // radians, vertical FOV
uniform float iAspect;  // width / height

in  vec2 TexCoord;
out vec4 OutColor;

// ── 3D hash / noise ─────────────────────────────────────────────────────
// 3D noise gives seamless results on a sphere — no meridian seam.
float hash31(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0,0,0));
    float n100 = hash31(i + vec3(1,0,0));
    float n010 = hash31(i + vec3(0,1,0));
    float n110 = hash31(i + vec3(1,1,0));
    float n001 = hash31(i + vec3(0,0,1));
    float n101 = hash31(i + vec3(1,0,1));
    float n011 = hash31(i + vec3(0,1,1));
    float n111 = hash31(i + vec3(1,1,1));
    return mix(
        mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
        mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y),
        u.z
    );
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise3(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

// ── 3D star field ───────────────────────────────────────────────────────
// Quantises the unit direction onto a small grid; high hash → bright star.
float stars3(vec3 d, float density, float twinkle) {
    vec3 s = d * density;
    vec3 cell = floor(s);
    vec3 f = fract(s) - 0.5;
    float r = hash31(cell);
    float bright = step(0.985, r);
    float dist = length(f);
    float pulse = 0.5 + 0.5 * sin(iTime * 2.0 + r * 31.4);
    return bright * smoothstep(0.18, 0.0, dist) * mix(0.6, 1.0, pulse * twinkle);
}

// ── Modes (all driven by 3D direction → seamless on a sphere) ───────────
vec3 modeAurora(vec3 d, float t) {
    // Vertical bands using elevation; horizontal turbulence by horizontal noise.
    float elev = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    float n1 = fbm3(d * 1.5 + vec3(t * 0.20, 0.0, 0.0));
    float n2 = fbm3(d * 2.5 + vec3(0.0, t * 0.10, 0.0));
    float bands = sin(elev * 6.0 + n1 * 3.0 + t * 0.6) * 0.5 + 0.5;
    bands = pow(bands, 1.6);
    float core = smoothstep(0.45, 0.95, bands * (0.6 + n2 * 0.6));
    vec3 base = mix(iColor2 * 0.25, iColor, core);
    base += iColor * pow(core, 4.0) * 1.4;
    base += stars3(d, 80.0, 0.8) * 0.5;
    return base;
}
vec3 modeCosmos(vec3 d, float t) {
    float n  = fbm3(d * 2.0 + vec3(t * 0.05));
    float n2 = fbm3(d * 4.0 - vec3(t * 0.03));
    float nebula = smoothstep(0.35, 1.0, n * 1.1) * 0.7
                 + smoothstep(0.55, 1.0, n2) * 0.4;
    vec3 col = mix(vec3(0.02, 0.01, 0.04), iColor2 * 0.6, nebula);
    col += iColor * pow(nebula, 2.0) * 0.9;
    col += stars3(d, 140.0, 1.0) * 1.0;
    col += stars3(d, 60.0,  0.6) * 0.6;
    return col;
}
vec3 modeMagma(vec3 d, float t) {
    float n = fbm3(d * 3.0 + vec3(0.0, t * 0.6, 0.0));
    float cracks = pow(1.0 - abs(n - 0.5) * 2.0, 6.0);
    vec3 col = mix(iColor2 * 0.15, iColor, n);
    col = mix(col, iColor * 1.6, cracks);
    col += pow(n, 3.0) * iColor * 0.8;
    return col;
}
vec3 modeOcean(vec3 d, float t) {
    float ripple = fbm3(d * 2.0 + vec3(t * 0.3, 0.0, t * 0.3));
    float wave = sin(d.y * 10.0 + ripple * 5.0 + t) * 0.5 + 0.5;
    wave = pow(wave, 1.5);
    float caustic = fbm3(d * 6.0 + vec3(t * 0.4));
    vec3 deep = iColor2 * 0.3;
    vec3 surf = iColor;
    vec3 col = mix(deep, surf, wave);
    col += vec3(1.0) * pow(caustic, 5.0) * 0.5;
    return col;
}
vec3 modeSunset(vec3 d, float t) {
    // Use elevation only — guaranteed seamless gradient.
    float elev = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    float grad = pow(1.0 - elev, 1.4);
    vec3 horizon = iColor;
    vec3 zenith  = iColor2 * 0.5;
    vec3 col = mix(zenith, horizon, grad);
    float clouds = fbm3(d * 3.0 + vec3(t * 0.05, 0.0, 0.0));
    col = mix(col, col * 1.3 + iColor * 0.2, smoothstep(0.45, 0.85, clouds) * 0.7);
    // Stationary "sun" anchored at +X horizon.
    vec3 sunDir = normalize(vec3(1.0, 0.05, 0.0));
    float sun = pow(max(dot(d, sunDir), 0.0), 200.0);
    col += iColor * sun * 1.6;
    return col;
}
vec3 modeStorm(vec3 d, float t) {
    float n = fbm3(d * 3.0 + vec3(t * 0.4, 0.0, t * 0.1));
    float clouds = smoothstep(0.35, 0.85, n);
    vec3 col = mix(iColor2 * 0.18, vec3(0.05), clouds);
    float bolt = step(0.998, fract(sin(floor(t * 3.0)) * 43758.5453123));
    float boltMask = step(0.4, abs(d.x - 0.0 + sin(d.y * 18.0 + t * 30.0) * 0.05));
    col += iColor * bolt * (1.0 - boltMask) * 2.0;
    col += clouds * iColor * 0.15;
    return col;
}
vec3 modeNeon(vec3 d, float t) {
    // Radial pattern around the up-vector → seamless on yaw.
    float angle = atan(d.x, d.z);             // -π..π — only used through cos
    float elev  = clamp(d.y, -1.0, 1.0);
    float r = sqrt(1.0 - elev * elev);        // distance from vertical axis
    // 12-armed pattern: cos handles the 2π-wrap automatically.
    float lines = cos(angle * 12.0 + t * 1.5) * 0.5 + 0.5;
    lines *= 1.0 - smoothstep(0.4, 1.2, 1.0 - elev);
    vec3 col = iColor2 * 0.2;
    col += iColor * pow(lines, 4.0) * 1.8;
    col += iColor * exp(-(1.0 - elev) * 2.5) * 0.6;
    return col;
}
vec3 modeTwilight(vec3 d, float t) {
    float elev = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    float grad = pow(elev, 1.2);
    vec3 col = mix(iColor, iColor2, grad);
    col *= 0.6 + 0.4 * (1.0 - grad);
    col += stars3(d, 100.0, 1.0) * grad * 1.2;
    float drift = fbm3(d * 2.0 + vec3(t * 0.1));
    col += iColor * smoothstep(0.6, 0.95, drift) * 0.25;
    return col;
}
vec3 modeDawn(vec3 d, float t) {
    float elev = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    float grad = pow(1.0 - elev, 1.5);
    vec3 horizon = iColor;
    vec3 zenith  = iColor2 * 0.7;
    vec3 col = mix(zenith, horizon, grad);
    // Stationary "sun" at +Z horizon.
    vec3 sunDir = normalize(vec3(0.0, 0.10, 1.0));
    float sun = pow(max(dot(d, sunDir), 0.0), 150.0);
    col += iColor * sun * 1.4;
    // Radial "rays" around the sun direction. Use angle around the up-axis
    // for seamless wrap.
    float ang = atan(d.x - sunDir.x, d.z - sunDir.z);
    float rays = pow(0.5 + 0.5 * cos(ang * 14.0 + t), 8.0);
    col += iColor * rays * 0.18 * grad;
    return col;
}
vec3 modeAlien(vec3 d, float t) {
    float n = fbm3(d * 4.0 + vec3(sin(t * 0.4), cos(t * 0.3), sin(t * 0.2)) * 0.5);
    float swirl = sin(d.x * 10.0 + n * 8.0 + t) * 0.5 + 0.5;
    vec3 col = mix(iColor2 * 0.25, iColor, swirl);
    col += iColor * pow(n, 3.0) * 1.2;
    col += vec3(0.0, 1.0, 0.6) * smoothstep(0.85, 1.0, n) * 0.4;
    col += stars3(d, 70.0, 0.7) * 0.5;
    return col;
}

/**
 * Builds a unit world-space view direction for the current pixel from the
 * camera state uniforms.
 */
vec3 viewDirWorld() {
    vec2 ndc = TexCoord * 2.0 - 1.0;
    float tanHalf = tan(iFov * 0.5);
    // Camera-space ray: camera looks down -Z.
    vec3 dirCam = normalize(vec3(ndc.x * tanHalf * iAspect,
                                 ndc.y * tanHalf,
                                -1.0));
    // Apply pitch (rotate around X), then yaw (rotate around Y).
    float cp = cos(iPitch), sp = sin(iPitch);
    vec3 d1 = vec3(dirCam.x,
                   dirCam.y * cp - dirCam.z * sp,
                   dirCam.y * sp + dirCam.z * cp);
    float cy = cos(iYaw), sy = sin(iYaw);
    return normalize(vec3(d1.x * cy + d1.z * sy,
                          d1.y,
                         -d1.x * sy + d1.z * cy));
}

void main() {
    vec3 d = viewDirWorld();
    float t = iTime * iSpeed;

    vec3 col;
    if      (iMode == 0) col = modeAurora  (d, t);
    else if (iMode == 1) col = modeCosmos  (d, t);
    else if (iMode == 2) col = modeMagma   (d, t);
    else if (iMode == 3) col = modeOcean   (d, t);
    else if (iMode == 4) col = modeSunset  (d, t);
    else if (iMode == 5) col = modeStorm   (d, t);
    else if (iMode == 6) col = modeNeon    (d, t);
    else if (iMode == 7) col = modeTwilight(d, t);
    else if (iMode == 8) col = modeDawn    (d, t);
    else                 col = modeAlien   (d, t);

    // Soft tone curve
    col = col / (1.0 + col * 0.4);
    OutColor = vec4(col, 1.0);
}
