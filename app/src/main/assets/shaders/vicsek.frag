// The Vicsek cross, zooming in for ever: every plus sign is made of five smaller plus signs.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float f = fract(uTime * 0.07);
    float zoom = exp(f * log(3.0));
    vec2 p = rot(uTime * 0.02 * 0.0) * uv * 0.55 / zoom + vec2(0.5);
    float depth = 0.0;
    float alive = 1.0;
    for (int i = 0; i < 9; i++) {
        p *= 3.0;
        vec2 q = floor(p);
        p = fract(p);
        float keep = (q.x == 1.0 || q.y == 1.0) ? 1.0 : 0.0;
        if (keep < 0.5 && alive > 0.5) { alive = 0.0; depth = float(i); }
    }
    float edge = min(min(p.x, 1.0 - p.x), min(p.y, 1.0 - p.y));
    vec3 col = pal(0.11 * depth + 0.1 * f + 0.02 * uTime);
    if (alive > 0.5) return pal(0.9 + 0.05 * uTime) * (0.55 + 0.45 * smoothstep(0.0, 0.2, edge));
    return col * (0.16 + 0.25 * smoothstep(0.0, 0.3, edge));
}
