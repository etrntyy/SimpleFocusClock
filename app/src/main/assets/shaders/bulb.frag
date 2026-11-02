// A raymarched Mandelbulb whose power slides between 5 and 9 while the camera circles it.
float trapV;

float de(vec3 pos, float power) {
    vec3 z = pos;
    float dr = 1.0;
    float r = 0.0;
    trapV = 1e9;
    for (int i = 0; i < 6; i++) {
        r = length(z);
        if (r > 2.0) break;
        float theta = acos(clamp(z.z / max(r, 1e-6), -1.0, 1.0));
        float phi = atan(z.y, z.x);
        dr = pow(r, power - 1.0) * power * dr + 1.0;
        float zr = pow(r, power);
        theta *= power;
        phi *= power;
        z = zr * vec3(sin(theta) * cos(phi), sin(theta) * sin(phi), cos(theta)) + pos;
        trapV = min(trapV, dot(z, z));
    }
    return 0.5 * log(max(r, 1e-6)) * r / dr;
}

vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float power = 7.0 + 2.0 * sin(uTime * 0.09);
    float a = uTime * 0.12 + uTouch.x * uTouchAmt;
    float h = 0.5 + 0.35 * sin(uTime * 0.07);
    vec3 ro = 2.3 * vec3(cos(a), h, sin(a));
    vec3 fw = normalize(-ro);
    vec3 rt = normalize(cross(vec3(0.0, 1.0, 0.0), fw));
    vec3 up = cross(fw, rt);
    vec3 rd = normalize(fw * 2.3 + rt * uv.x + up * uv.y);

    float t = 0.0;
    float d = 0.0;
    float hit = 0.0;
    for (int i = 0; i < 56; i++) {
        d = de(ro + rd * t, power);
        if (d < 0.004) {
            hit = 1.0;
            break;
        }
        t += d;
        if (t > 6.0) break;
    }
    vec3 bg = pal(0.7 + 0.1 * uv.y) * 0.1 * (1.0 - 0.4 * length(uv));
    if (hit < 0.5) return bg;

    vec3 p = ro + rd * t;
    float tr = trapV;
    vec2 e = vec2(0.006, 0.0);
    vec3 n = normalize(vec3(
        de(p + e.xyy, power) - de(p - e.xyy, power),
        de(p + e.yxy, power) - de(p - e.yxy, power),
        de(p + e.yyx, power) - de(p - e.yyx, power)));
    vec3 l = normalize(-rd * 0.7 + up * 0.6 + rt * 0.5);
    float diff = clamp(dot(n, l), 0.0, 1.0);
    float rim = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
    vec3 base = pal(0.25 * sqrt(tr) + 0.02 * uTime);
    vec3 col = base * (0.3 + 0.9 * diff) + 0.35 * rim * pal(0.5 + 0.1 * tr);
    return mix(col, bg, clamp(t / 6.0, 0.0, 1.0) * 0.5);
}
