// The Mandelbrot set seen through stained glass: each pixel takes the colour of the circle its orbit came closest to.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.06);
    float zoom = exp(-ph * 2.2);
    vec2 center = mix(vec2(-0.5, 0.0), vec2(-0.16, 1.03), smoothstep(0.0, 1.0, ph));
    vec2 c = center + rot(uTime * 0.03) * uv * 1.4 * zoom;

    vec2 z = vec2(0.0);
    float trap = 1e9;
    float esc = 0.0;
    for (int n = 0; n < 90; n++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        trap = min(trap, abs(length(z) - 0.6));
        if (dot(z, z) > 256.0) { esc = 1.0; break; }
    }
    vec3 col = pal(1.8 * trap + 0.5 * esc + 0.02 * uTime);
    float edge = smoothstep(0.0, 0.05, trap);
    return col * (0.25 + 0.75 * edge) * (0.5 + 0.5 * esc);
}
