// The Phoenix fractal: each step also remembers the one before it, which gives flame-like feathers.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.08;
    float cx = 0.5667 + 0.07 * sin(t);
    float p = -0.5 + 0.18 * cos(t * 1.3);
    cx += uTouch.x * uTouchAmt * 0.1;
    vec2 z = rot(1.5708 + uTime * 0.015) * uv * 1.25;
    vec2 zp = vec2(0.0);

    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 110; n++) {
        vec2 zn = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + vec2(cx, 0.0) + p * zp;
        zp = z;
        z = zn;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return pal(0.5 + 0.2 * length(z)) * 0.07;
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.035 * sl + 0.02 * uTime) * (0.3 + 0.7 * smoothstep(0.0, 8.0, sl));
}
