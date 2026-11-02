// A raymarched Menger sponge turning slowly, lit from the camera's side.
float sdBox(vec3 p, vec3 b) {
    vec3 d = abs(p) - b;
    return min(max(d.x, max(d.y, d.z)), 0.0) + length(max(d, 0.0));
}

float map(vec3 p) {
    float d = sdBox(p, vec3(1.0));
    float s = 1.0;
    for (int m = 0; m < 4; m++) {
        vec3 a = mod(p * s, 2.0) - 1.0;
        s *= 3.0;
        vec3 r = abs(1.0 - 3.0 * abs(a));
        float da = max(r.x, r.y);
        float db = max(r.y, r.z);
        float dc = max(r.z, r.x);
        float c = (min(da, min(db, dc)) - 1.0) / s;
        d = max(d, c);
    }
    return d;
}

vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float a = uTime * 0.1 + uTouch.x * uTouchAmt;
    float h = 0.9 + 0.5 * sin(uTime * 0.06);
    float dist = 3.6 + 0.8 * sin(uTime * 0.045);
    vec3 ro = dist * vec3(cos(a), h * 0.6, sin(a));
    vec3 fw = normalize(-ro);
    vec3 rt = normalize(cross(vec3(0.0, 1.0, 0.0), fw));
    vec3 up = cross(fw, rt);
    vec3 rd = normalize(fw * 3.6 + rt * uv.x + up * uv.y);

    float t = 0.0;
    float steps = 0.0;
    float hit = 0.0;
    for (int i = 0; i < 70; i++) {
        float d = map(ro + rd * t);
        if (d < 0.0006 * t) {
            hit = 1.0;
            break;
        }
        t += d;
        steps += 1.0;
        if (t > 9.0) break;
    }
    vec3 bg = pal(0.75 + 0.12 * uv.y) * 0.1;
    if (hit < 0.5) return bg;

    vec3 p = ro + rd * t;
    vec2 e = vec2(0.0015, 0.0);
    vec3 n = normalize(vec3(
        map(p + e.xyy) - map(p - e.xyy),
        map(p + e.yxy) - map(p - e.yxy),
        map(p + e.yyx) - map(p - e.yyx)));
    vec3 l = normalize(-rd * 0.6 + up * 0.7 + rt * 0.5);
    float diff = clamp(dot(n, l), 0.0, 1.0);
    float ao = 1.0 - steps / 70.0;
    vec3 base = pal(0.12 * length(p) + 0.45 * dot(n, vec3(0.3, 0.6, 0.2)) + 0.02 * uTime);
    vec3 col = base * (0.2 + 0.95 * diff) * (0.35 + 0.65 * ao);
    return mix(col, bg, clamp(t / 9.0, 0.0, 1.0) * 0.4);
}
