// Julia set seen through orbit traps: instead of counting escapes, measure how close the orbit comes to a ring and a cross.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float t = uTime * 0.09;
    vec2 c = vec2(-0.4 + 0.15 * cos(t * 1.3), 0.6 + 0.1 * sin(t));
    c = mix(c, vec2(-0.4, 0.6) + uTouch * 0.25, uTouchAmt);
    vec2 z = rot(uTime * 0.03) * uv * 1.3;

    float ring = 1e9;
    float xline = 1e9;
    float last = 0.0;
    for (int i = 0; i < 48; i++) {
        z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
        ring = min(ring, abs(length(z) - 0.75 - 0.2 * sin(t * 2.0)));
        xline = min(xline, min(abs(z.x), abs(z.y)));
        if (dot(z, z) > 64.0) {
            last = float(i);
            break;
        }
    }
    vec3 col = pal(0.8 * sqrt(ring) + 0.02 * uTime) * exp(-3.0 * ring);
    col += pal(0.3 + 1.2 * sqrt(xline)) * 0.8 * exp(-8.0 * xline);
    return 0.75 * col * (0.55 + 0.45 * smoothstep(0.0, 10.0, last));
}
