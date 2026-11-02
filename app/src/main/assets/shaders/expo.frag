// Iterating c times e to the z: the points that stay home form a river of little spirals.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.07;
    vec2 c = 0.5 * vec2(cos(t), sin(t * 1.2)) + vec2(0.35, 0.1);
    vec2 z = rot(uTime * 0.02) * uv * 3.2 - vec2(0.8, 0.0);

    float it = 0.0;
    for (int n = 0; n < 50; n++) {
        float e = exp(z.x);
        z = cmul(c, vec2(e * cos(z.y), e * sin(z.y)));
        it = float(n);
        if (z.x > 40.0) break;
    }
    float v = it / 50.0;
    vec3 col = pal(1.3 * v + 0.03 * uTime);
    return col * (0.12 + 0.88 * (1.0 - v));
}
