// z to the fourth plus c: Julia sets with four-fold lace, as the seed circles round.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.13;
    vec2 c = 0.52 * vec2(cos(a), sin(a)) + 0.06 * vec2(cos(a * 3.1), sin(a * 2.3));
    c = mix(c, uTouch * 0.6, uTouchAmt);
    vec2 z = rot(uTime * 0.03) * uv * 1.25;

    float it = 0.0;
    float esc = 0.0;
    for (int n = 0; n < 90; n++) {
        vec2 z2 = cmul(z, z);
        z = cmul(z2, z2) + c;
        if (dot(z, z) > 1e4) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return pal(0.3 + 0.15 * length(z)) * 0.07;
    float sl = it - log2(log2(dot(z, z))) / 2.0 + 3.0;
    return pal(0.04 * sl + 0.4 * a / TAU) * (0.3 + 0.7 * smoothstep(0.0, 6.0, sl));
}
