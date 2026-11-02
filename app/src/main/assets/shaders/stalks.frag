// A Julia set painted by how close each orbit comes to the two axes: glowing stalks and tendrils.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.09;
    vec2 c = vec2(-0.4, 0.6) + 0.12 * vec2(cos(a * 1.3), sin(a));
    c = mix(c, uTouch * 0.6, uTouchAmt);
    vec2 z = rot(uTime * 0.025) * uv * 1.4;

    float trap = 1e9;
    float it = 0.0;
    for (int n = 0; n < 60; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        trap = min(trap, min(abs(z.x), abs(z.y)));
        it = float(n);
        if (dot(z, z) > 64.0) break;
    }
    float glow = exp(-6.0 * trap);
    vec3 col = pal(0.5 * trap + 0.01 * it + 0.03 * uTime);
    return col * (0.12 + 0.9 * glow);
}
