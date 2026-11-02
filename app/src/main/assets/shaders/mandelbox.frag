// A 2D slice of the Mandelbox: fold the plane like paper, inflate, repeat. The scale breathes between -2.3 and -1.7.
vec3 fractal(vec2 fc) {
    vec2 uv = uvOf(fc);
    float scale = -2.0 + 0.3 * sin(uTime * 0.08);
    vec2 c = rot(uTime * 0.02) * uv * 1.7;
    vec2 z = c;
    float esc = 0.0;
    float it = 0.0;
    for (int n = 0; n < 40; n++) {
        z = clamp(z, -1.0, 1.0) * 2.0 - z;
        float r2 = dot(z, z);
        if (r2 < 0.25) z *= 4.0;
        else if (r2 < 1.0) z /= r2;
        z = scale * z + c;
        if (dot(z, z) > 400.0) {
            esc = 1.0;
            it = float(n);
            break;
        }
    }
    float sl = it - log2(log2(max(dot(z, z), 4.0))) + 4.0;
    vec3 col = pal(0.04 * sl + 0.5 * esc + 0.02 * uTime) * (0.25 + 0.75 * smoothstep(0.0, 10.0, sl));
    return mix(pal(0.2 + 0.05 * length(z)) * 0.1, col, esc);
}
