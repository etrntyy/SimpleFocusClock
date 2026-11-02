// A raymarched Sierpinski tetrahedron, spinning slowly through the dark.
float map(vec3 z) {
    float scale = 2.0;
    for (int i = 0; i < 9; i++) {
        if (z.x + z.y < 0.0) z.xy = -z.yx;
        if (z.x + z.z < 0.0) z.xz = -z.zx;
        if (z.y + z.z < 0.0) z.yz = -z.zy;
        z = z * scale - vec3(1.0) * (scale - 1.0);
    }
    return length(z) * pow(scale, -9.0);
}

vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.13 + uTouch.x * uTouchAmt;
    float h = 0.6 + 0.5 * sin(uTime * 0.07);
    vec3 ro = 3.4 * vec3(cos(a), h, sin(a));
    vec3 fw = normalize(-ro);
    vec3 rt = normalize(cross(vec3(0.0, 1.0, 0.0), fw));
    vec3 up = cross(fw, rt);
    vec3 rd = normalize(fw * 4.2 + rt * uv.x + up * uv.y);

    float t = 0.0;
    float steps = 0.0;
    float hit = 0.0;
    for (int i = 0; i < 64; i++) {
        float d = map(ro + rd * t);
        if (d < 0.0008 * (1.0 + t)) {
            hit = 1.0;
            break;
        }
        t += d;
        steps += 1.0;
        if (t > 9.0) break;
    }
    vec3 bg = pal(0.6 + 0.1 * uv.y) * 0.08;
    if (hit < 0.5) return bg + pal(0.2) * 0.12 * pow(steps / 64.0, 2.0);

    vec3 p = ro + rd * t;
    vec2 e = vec2(0.003, 0.0);
    vec3 n = normalize(vec3(
        map(p + e.xyy) - map(p - e.xyy),
        map(p + e.yxy) - map(p - e.yxy),
        map(p + e.yyx) - map(p - e.yyx)));
    vec3 l = normalize(-rd * 0.6 + up * 0.7 + rt * 0.4);
    float diff = clamp(dot(n, l), 0.0, 1.0);
    float ao = 1.0 - steps / 64.0;
    vec3 base = pal(0.3 * dot(p, vec3(0.4, 0.7, 0.3)) + 0.02 * uTime);
    return base * (0.2 + 0.95 * diff) * (0.3 + 0.7 * ao);
}
