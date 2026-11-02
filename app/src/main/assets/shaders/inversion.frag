// The Mandelbrot set turned inside out: the point at infinity sits in the middle and the whole set hugs the edge.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.08;
    vec2 q = rot(uTime * 0.05) * uv * (0.8 + 0.25 * sin(t));
    vec2 c = cdiv(vec2(1.0, 0.0), q) * 0.8 + vec2(-0.55, 0.0);

    vec2 z = vec2(0.0);
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 130; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 256.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float sl = it - log2(log2(dot(z, z))) + 4.0;
    return pal(0.03 * sl + 0.02 * uTime) * (0.25 + 0.75 * smoothstep(0.0, 5.0, sl));
}
