// A Julia set whose seed wanders through the Seahorse Valley of the Mandelbrot set. Touch drags the seed.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.11;
    vec2 c = vec2(-0.76, 0.1) + vec2(0.05 * cos(a * 2.0), 0.045 * sin(a * 2.7)) + 0.1 * vec2(cos(a * 0.5), sin(a * 0.5)) * (0.5 - 0.5 * cos(a * 0.3));
    c = mix(c, uTouch * 0.55, uTouchAmt);
    vec2 z = rot(uTime * 0.02) * uv * 1.15;

    float it = 0.0;
    float esc = 0.0;
    for (int n = 0; n < 110; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return pal(0.6 + 0.1 * length(z)) * 0.08;
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.03 * sl + 0.02 * uTime) * (0.3 + 0.7 * smoothstep(0.0, 8.0, sl));
}
