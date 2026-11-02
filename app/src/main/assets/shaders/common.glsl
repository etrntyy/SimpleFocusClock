precision highp float;

uniform vec2 uRes;
uniform float uTime;
uniform float uScroll;
uniform vec2 uTouch;
uniform float uTouchAmt;
uniform float uFade;
uniform float uInvert;
uniform vec3 uPalA;
uniform vec3 uPalB;
uniform vec3 uPalC;
uniform vec3 uPalD;

const float PI = 3.14159265;
const float TAU = 6.28318531;

vec3 pal(float t) {
    return uPalA + uPalB * cos(TAU * (uPalC * t + uPalD));
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

vec2 cmul(vec2 a, vec2 b) {
    return vec2(a.x * b.x - a.y * b.y, a.x * b.y + a.y * b.x);
}

vec2 cdiv(vec2 a, vec2 b) {
    float d = dot(b, b) + 1e-12;
    return vec2(a.x * b.x + a.y * b.y, a.y * b.x - a.x * b.y) / d;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Screen position in a frame where x spans -1..1 on the short side; the launcher's page scroll nudges it.
vec2 uvOf(vec2 fc) {
    vec2 uv = (2.0 * fc - uRes) / min(uRes.x, uRes.y);
    uv.x += uScroll * 0.2;
    return uv;
}
