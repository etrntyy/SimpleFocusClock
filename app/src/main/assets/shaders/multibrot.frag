// Mandelbrot morphing into its cubic cousin: z -> (1-m)z^2 + m z^3 + c.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float m = 0.5 - 0.5 * cos(uTime * 0.1);
    vec2 c = rot(uTime * 0.025) * uv * mix(0.95, 0.85, m);
    c.x -= 0.3 * (1.0 - m);

    vec2 z = vec2(0.0);
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 120; n++) {
        vec2 z2 = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y);
        z = mix(z2, cmul(z2, z), m) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float sl = it - log2(log2(dot(z, z))) / log2(2.0 + m) + 4.0;
    return pal(0.03 * sl + 0.03 * uTime) * (0.3 + 0.7 * smoothstep(0.0, 6.0, sl));
}
