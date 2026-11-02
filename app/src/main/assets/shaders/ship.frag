// The Burning Ship fractal, slowly flying in toward the ship's mast and back.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.08);
    float zoom = exp(-ph * 4.3);
    vec2 center = mix(vec2(-0.45, -0.5), vec2(-1.7443359375, -0.017451171875), smoothstep(0.0, 1.0, ph));
    vec2 c = center + vec2(uv.x, -uv.y) * 1.1 * zoom;
    float maxIt = 80.0 + 120.0 * ph;

    vec2 z = vec2(0.0);
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 200; n++) {
        if (float(n) >= maxIt) break;
        z = abs(z);
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.025 * sl + 0.02 * uTime) * (0.3 + 0.7 * smoothstep(0.0, 6.0, sl));
}
