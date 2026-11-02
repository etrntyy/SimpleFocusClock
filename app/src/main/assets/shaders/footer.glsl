void main() {
    vec3 c = fractal(gl_FragCoord.xy);
    c = clamp(c * uFade, 0.0, 1.0);
    // The black and white versions flip in the light theme.
    c = mix(c, vec3(1.0) - c, uInvert);
    c += (hash21(gl_FragCoord.xy + fract(uTime)) - 0.5) / 255.0;
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
