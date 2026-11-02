// The Julia set for c = i has no inside at all: a fine web of dendrites, like lightning.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.07);
    vec2 c = vec2(0.0, 1.0) + 0.02 * vec2(sin(uTime * 0.3), cos(uTime * 0.23));
    vec2 z = rot(uTime * 0.04) * uv * (1.9 - 1.1 * ph);

    float it = 0.0;
    float esc = 0.0;
    for (int n = 0; n < 120; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return pal(0.7) * 0.9;
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.12 / (0.05 + 0.1 * sl) + 0.02 * uTime) * (0.1 + 0.9 * exp(-0.35 * sl));
}
