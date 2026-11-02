// Sierpinski carpet: a zoom that never ends, because every corner contains the whole carpet again.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float zt = uTime * 0.18;
    float k = fract(zt);
    vec2 p = abs(rot(uTime * 0.04) * uv) * 0.9 * exp2(-k * log2(3.0));
    p += abs(uTouch) * uTouchAmt * 0.02;

    vec3 col = vec3(0.0);
    float edge = 1.0;
    for (int i = 0; i < 9; i++) {
        vec2 cell = floor(p * 3.0);
        vec2 f = fract(p * 3.0);
        if (cell.x == 1.0 && cell.y == 1.0) {
            float lvl = float(i) + k;
            float rad = length(f - 0.5);
            col = pal(lvl * 1.0 + rad * 0.5) * (1.1 - 1.2 * rad);
            col *= 0.4 + 0.6 * smoothstep(0.0, 0.1, 0.5 - max(abs(f.x - 0.5), abs(f.y - 0.5)));
            break;
        }
        p = f;
        if (i == 8) col = vec3(0.01);
    }
    return col;
}
