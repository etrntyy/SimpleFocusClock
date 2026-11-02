// Fold the plane into a sixth, shrink, shift, repeat: a six-fold snowflake whose arms keep growing new arms.
vec3 fractal(vec2 fc) {
    vec2 p = rot(uTime * 0.05) * uvOf(fc) * 1.5;
    float seg = TAU / 6.0;
    float orb = 1e9;
    float s = 1.0;
    vec2 shift = vec2(0.9 + 0.12 * sin(uTime * 0.2), 0.12 * cos(uTime * 0.17));
    for (int i = 0; i < 7; i++) {
        float r = length(p);
        float a = atan(p.y, p.x);
        a = mod(a, seg);
        a = abs(a - seg * 0.5);
        p = r * vec2(cos(a), sin(a));
        p = p * 1.7 - shift;
        s *= 1.7;
        orb = min(orb, length(p) / s);
    }
    float d = length(p) / s;
    vec3 col = pal(0.15 * log(s) + 6.0 * sqrt(orb) + 0.03 * uTime);
    return col * (0.1 + 0.9 * exp(-30.0 * d)) + 0.25 * pal(0.4 + 30.0 * orb) * exp(-80.0 * orb);
}
