// The Collatz conjecture continued to the complex plane: z -> (2 + 7z - (2 + 5z) cos(pi z)) / 4.
vec2 ccos(vec2 z) {
    vec2 ex = vec2(exp(z.y), exp(-z.y));
    return vec2(cos(z.x) * 0.5 * (ex.x + ex.y), -sin(z.x) * 0.5 * (ex.x - ex.y));
}

vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.06);
    float zoom = mix(1.4, 0.35, ph);
    vec2 center = mix(vec2(0.5, 0.0), vec2(1.0, 0.0), ph);
    vec2 z = center + rot(uTime * 0.02) * uv * zoom;

    float it = 0.0;
    float esc = 0.0;
    for (int n = 0; n < 40; n++) {
        vec2 a = vec2(2.0, 0.0) + 5.0 * z;
        vec2 cz = ccos(PI * z);
        z = 0.25 * (vec2(2.0, 0.0) + 7.0 * z - cmul(a, cz));
        if (dot(z, z) > 1.0e4) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    float sl = it - log2(log2(max(dot(z, z), 4.0)));
    vec3 col = pal(0.07 * sl + 0.02 * uTime) * (0.6 + 0.9 * smoothstep(0.0, 6.0, sl));
    vec3 inner = pal(0.6 + 0.1 * atan(z.y, z.x)) * 0.35;
    return mix(inner, col, esc);
}
