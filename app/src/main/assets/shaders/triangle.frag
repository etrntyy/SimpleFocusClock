// Sierpinski gasket zooming forever: every corner contains the whole pattern again.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float zt = uTime * 0.22;
    float k = fract(zt);
    vec2 p = abs(rot(uTime * 0.03) * uv) * 0.7 * exp2(-k);
    p += abs(uTouch) * uTouchAmt * 0.01;

    vec3 col = vec3(0.01);
    for (int i = 0; i < 12; i++) {
        vec2 q = p * 2.0;
        vec2 cell = floor(q);
        vec2 f = fract(q);
        if (cell.x == 1.0 && cell.y == 1.0) {
            float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
            float lvl = float(i) + k;
            col = pal(lvl + 0.35 * (f.x + f.y)) * (0.45 + 1.1 * edge);
            col *= 0.55 + 0.45 * smoothstep(0.0, 0.08, edge);
            break;
        }
        p = f;
    }
    return col;
}
