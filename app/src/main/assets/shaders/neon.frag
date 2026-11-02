// The Mandelbrot set as glowing contour lines, found with a distance estimate, drifting into the triple spiral valley.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float ph = 0.5 - 0.5 * cos(uTime * 0.07);
    float zoom = exp(-ph * 6.2);
    vec2 center = mix(vec2(-0.5, 0.0), vec2(-0.0452407411, 0.9868162204352258), smoothstep(0.0, 0.9, ph));
    vec2 c = center + rot(uTime * 0.03) * uv * 1.2 * zoom;
    float maxIt = 100.0 + 220.0 * ph;

    vec2 z = vec2(0.0);
    vec2 dz = vec2(0.0);
    float esc = 0.0;
    for (int n = 0; n < 320; n++) {
        if (float(n) >= maxIt) break;
        dz = 2.0 * cmul(z, dz) + vec2(1.0, 0.0);
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        if (dot(z, z) > 1.0e6) {
            esc = 1.0;
            break;
        }
    }
    if (esc < 0.5) return vec3(0.0);
    float r = length(z);
    float d = 0.5 * r * log(r) / length(dz);
    float px = zoom * 1.2 * 2.0 / min(uRes.x, uRes.y);
    float g = d / px;
    float rings = 0.5 + 0.5 * cos(log2(d / zoom) * 5.0 - uTime * 0.6);
    vec3 col = pal(0.12 * log2(d / zoom) + 0.03 * uTime);
    col *= (0.15 + 0.85 * pow(rings, 3.0)) * exp(-0.04 * g) * 1.6;
    col += pal(0.5) * 0.8 * exp(-1.2 * g);
    return col;
}
