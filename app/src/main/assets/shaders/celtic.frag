// The Celtic Mandelbrot: fold the real part after squaring and the edge ties itself in knots.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.06);
    float zoom = exp(-ph * 2.6);
    vec2 center = mix(vec2(-0.35, 0.0), vec2(-1.1, 0.0), smoothstep(0.2, 1.0, ph));
    vec2 c = center + rot(uTime * 0.04) * uv * 1.5 * zoom;
    float maxIt = 90.0 + 60.0 * ph;

    vec2 z = vec2(0.0);
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 150; n++) {
        if (float(n) >= maxIt) break;
        z = vec2(abs(z.x * z.x - z.y * z.y), 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.03 * sl + 0.02 * uTime) * (0.3 + 0.7 * smoothstep(0.0, 6.0, sl));
}
