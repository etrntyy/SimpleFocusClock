// Apollonian-style circle inversion: a packing of ever smaller circles, with the packing slowly mutating.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float k = 1.15 + 0.12 * sin(uTime * 0.17);
    vec2 p = rot(uTime * 0.04) * uv * 1.1;
    p += vec2(sin(uTime * 0.11), cos(uTime * 0.13)) * 0.3;
    float scale = 1.0;
    float orb = 1e9;
    for (int i = 0; i < 8; i++) {
        p = -1.0 + 2.0 * fract(0.5 * p + 0.5);
        float r2 = dot(p, p);
        float f = k / r2;
        p *= f;
        scale *= f;
        orb = min(orb, r2);
    }
    float d = 0.25 * abs(p.y) / scale;
    float e = length(p) / scale;
    vec3 col = pal(0.25 * log(scale) + 0.4 * sqrt(orb) + 0.03 * uTime);
    col *= 0.25 + 0.75 * smoothstep(0.0, 0.01, d * 6.0);
    col += 0.35 * pal(0.6 + 0.3 * log(scale)) * exp(-60.0 * e);
    return col;
}
