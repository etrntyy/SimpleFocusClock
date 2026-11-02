// Nova fractal: Newton's method on z^3 - 1 with a relaxation factor that breathes over time.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.12;
    vec2 a = vec2(1.0, 0.0) + 0.55 * vec2(cos(t), sin(t * 1.3));
    a = mix(a, vec2(1.0, 0.0) + uTouch * 0.3, uTouchAmt);
    float zoom = 3.4 - 1.4 * (0.5 - 0.5 * cos(uTime * 0.05));
    vec2 z = rot(uTime * 0.03) * uv * zoom;

    float it = 0.0;
    float done = 0.0;
    for (int n = 0; n < 48; n++) {
        vec2 z2 = cmul(z, z);
        vec2 f = cmul(z2, z) - vec2(1.0, 0.0);
        vec2 fp = 3.0 * z2;
        vec2 dz = cmul(a, cdiv(f, fp));
        z -= dz;
        if (dot(dz, dz) < 1e-8) {
            done = 1.0;
            it = float(n);
            break;
        }
        it = float(n);
    }
    float ang = atan(z.y, z.x) / TAU;
    float shade = 1.0 - it / 48.0;
    vec3 col = pal(ang + 0.15 * shade + 0.02 * uTime);
    col *= 0.45 + 0.8 * shade;
    return mix(col * 0.5, col, done);
}
