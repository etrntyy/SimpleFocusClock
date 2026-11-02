// Kali lace: repeatedly invert and fold the plane (p -> |p| / |p|^2 - c) and colour by how far each orbit travels.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.06;
    vec2 p = rot(uTime * 0.03) * uv * 0.55;
    vec2 c = vec2(0.82 + 0.06 * sin(t * 1.7), 0.55 + 0.05 * cos(t * 1.3));
    c += uTouch * uTouchAmt * 0.04;

    float acc = 0.0;
    float prev = length(p);
    float trap = 1e9;
    for (int i = 0; i < 8; i++) {
        p = abs(p) / (dot(p, p) + 1e-4) - c;
        float l = length(p);
        acc += abs(l - prev);
        prev = l;
        trap = min(trap, abs(p.x * p.y));
    }
    float f = acc / 8.0;
    vec3 col = pal(0.35 * log(1.0 + f * 4.0) + 0.02 * uTime);
    col *= 0.2 + 0.9 * smoothstep(0.0, 2.2, f);
    col += pal(0.55 + 0.4 * f) * 0.5 * exp(-60.0 * trap);
    return col;
}
