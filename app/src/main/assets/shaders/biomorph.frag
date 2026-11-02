// Pickover biomorphs: stop an orbit as soon as it strays and test it against the axes, and cells and microbes appear.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.07;
    vec2 c = vec2(0.45 + 0.1 * cos(t), 0.1 * sin(t * 1.3));
    c = mix(c, vec2(0.45, 0.0) + uTouch * 0.2, uTouchAmt);
    vec2 z = rot(uTime * 0.03) * uv * 2.2;

    float it = 0.0;
    for (int n = 0; n < 40; n++) {
        z = cmul(z, cmul(z, z)) + c;
        it = float(n);
        if (dot(z, z) > 100.0) break;
    }
    float body = (abs(z.x) < 8.0 || abs(z.y) < 8.0) ? 1.0 : 0.0;
    vec3 col = pal(0.06 * it + 0.3 * body + 0.02 * uTime);
    return col * (0.15 + 0.85 * body * (0.4 + 0.6 * (1.0 - it / 40.0)));
}
