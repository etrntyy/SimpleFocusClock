// Julia sets for a seed that circles the edge of the Mandelbrot set: flowers that open and close.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.14;
    vec2 c = 0.7885 * vec2(cos(a), sin(a));
    c = mix(c, uTouch * 0.7, uTouchAmt);
    vec2 z = rot(uTime * 0.03) * uv * 1.3;

    float it = 0.0;
    float esc = 0.0;
    for (int n = 0; n < 100; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return pal(0.5 + 0.2 * length(z)) * 0.06;
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.035 * sl + 0.5 * a / TAU) * (0.3 + 0.7 * smoothstep(0.0, 7.0, sl));
}
