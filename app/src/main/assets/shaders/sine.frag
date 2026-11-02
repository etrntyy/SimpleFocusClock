// z becomes c times sin(z): a repeating pattern of tongues and spirals along the real axis.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.08;
    vec2 c = vec2(1.0 + 0.35 * cos(t), 0.3 * sin(t * 1.4));
    c = mix(c, vec2(1.0, 0.0) + uTouch * 0.4, uTouchAmt);
    vec2 z = rot(uTime * 0.015) * uv * 3.4;

    float it = 0.0;
    for (int n = 0; n < 60; n++) {
        float ey = exp(z.y);
        float ny = exp(-z.y);
        vec2 s = vec2(sin(z.x) * 0.5 * (ey + ny), cos(z.x) * 0.5 * (ey - ny));
        z = cmul(c, s);
        it = float(n);
        if (abs(z.y) > 30.0) break;
    }
    float v = it / 60.0;
    vec3 col = pal(v * 1.6 + 0.02 * uTime);
    return col * (0.1 + 0.9 * smoothstep(0.0, 0.5, 1.0 - v));
}
