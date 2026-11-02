// A magnet Julia set: two escape routes, one to infinity and one to the point 1, and a seed that drifts.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.1;
    vec2 c = vec2(1.9 + 0.5 * cos(t), 0.45 * sin(t * 1.3));
    c = mix(c, vec2(1.9, 0.0) + uTouch * 0.5, uTouchAmt);
    vec2 z = rot(uTime * 0.02) * uv * 2.2;

    float it = 0.0;
    float home = 0.0;
    for (int n = 0; n < 70; n++) {
        vec2 num = cmul(z, z) + c - vec2(1.0, 0.0);
        vec2 den = 2.0 * z + c - vec2(2.0, 0.0);
        vec2 q = cdiv(num, den);
        z = cmul(q, q);
        it = float(n);
        if (dot(z - vec2(1.0, 0.0), z - vec2(1.0, 0.0)) < 1e-5) { home = 1.0; break; }
        if (dot(z, z) > 1e4) break;
    }
    float shade = 1.0 - it / 70.0;
    vec3 col = pal(0.04 * it + 0.3 * home + 0.02 * uTime);
    return col * (0.15 + 0.85 * (home > 0.5 ? 0.4 + 0.6 * shade : shade));
}
