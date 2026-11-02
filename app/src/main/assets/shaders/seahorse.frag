// Endless dive into the Seahorse Valley of the Mandelbrot set.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.09);
    float zoom = exp(-ph * 7.4);
    vec2 target = vec2(-0.743643887037151, 0.131825904205330);
    vec2 center = mix(vec2(-0.5, 0.0), target, smoothstep(0.0, 0.7, ph));
    vec2 c = center + rot(uTime * 0.035) * uv * 1.15 * zoom;
    float maxIt = 90.0 + 230.0 * ph;

    vec2 z = vec2(0.0);
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 320; n++) {
        if (float(n) >= maxIt) break;
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    vec3 col = pal(0.022 * sl + 0.03 * uTime);
    return col * (0.35 + 0.65 * smoothstep(0.0, 6.0, sl));
}
