// Fractal noise, folded into itself twice (domain warping), drifting like a nebula.
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float f = 0.0;
    float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 6; i++) {
        f += a * vnoise(p);
        p = m * p;
        a *= 0.5;
    }
    return f;
}

vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc) * 1.3;
    float t = uTime * 0.07;
    vec2 q = vec2(fbm(uv + vec2(0.0, t)), fbm(uv + vec2(5.2, 1.3) - t));
    vec2 r = vec2(fbm(uv + 3.5 * q + vec2(1.7, 9.2) + 0.6 * t),
                  fbm(uv + 3.5 * q + vec2(8.3, 2.8) - 0.5 * t));
    r += uTouch * uTouchAmt * 0.3;
    float f = fbm(uv + 3.5 * r);
    vec3 col = pal(f * 1.1 + 0.4 * length(q) + 0.02 * uTime);
    col *= 0.15 + 1.1 * f * f + 0.35 * f;
    col = mix(col, pal(r.x + 0.5) * 1.2, smoothstep(0.55, 1.0, length(r)) * 0.4);
    return col;
}
