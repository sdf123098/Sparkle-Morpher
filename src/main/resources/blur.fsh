#version 150 core

uniform sampler2D u_screen;
uniform vec2  u_screenSize;
uniform vec2  u_rectSize;
uniform float u_radius;
uniform vec4  u_corner;
uniform float u_blurRadius;
uniform float u_gamma;
uniform vec4  u_tint;

uniform int   u_mode;
uniform vec2  u_pieCenter;
uniform float u_pieInner;
uniform float u_pieOuter;
uniform float u_pieStart;
uniform float u_pieEnd;
uniform float u_pieFeather;

in vec2 v_uv;
in vec2 v_pos;
out vec4 fragColor;

const float TAU = 6.28318530718;

float weight(float t, float log2radius, float gamma) {
    return exp(-gamma * pow(log2radius - t, 2.0));
}

vec4 sampleBlurred(vec2 uv, float radius, float gamma) {
    vec4 pix = vec4(0.0);
    float norm = 0.0;
    float lr = log2(max(radius, 1.0));
    for (float i = 0.0; i < 10.0; i += 0.5) {
        float k = weight(i, lr, gamma);
        pix += k * textureLod(u_screen, clamp(uv, 0.0, 1.0), i);
        norm += k;
    }
    return pix * pow(norm, -0.95);
}

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

float rectMask() {
    vec2 halfSize = u_rectSize * 0.5;
    vec2 uv = v_uv * u_rectSize;
    vec2 local = uv - halfSize;

    float enabled = 1.0;
    if (uv.x <  u_radius                && uv.y <  u_radius                && u_corner.z == 0.0) enabled = 0.0;
    if (uv.x >= u_rectSize.x - u_radius && uv.y <  u_radius                && u_corner.y == 0.0) enabled = 0.0;
    if (uv.x <  u_radius                && uv.y >= u_rectSize.y - u_radius && u_corner.x == 0.0) enabled = 0.0;
    if (uv.x >= u_rectSize.x - u_radius && uv.y >= u_rectSize.y - u_radius && u_corner.w == 0.0) enabled = 0.0;

    float r = mix(0.1, u_radius, enabled);
    vec2 bound = mix(halfSize - 1.1, halfSize - u_radius - 1.0, enabled);

    float dist = roundSDF(local, bound, r);
    return 1.0 - smoothstep(0.0, 1.0, dist);
}

float pieMask() {
    vec2 d = v_pos - u_pieCenter;
    float r = length(d);
    float feather = max(u_pieFeather, 1.0);
    float outerM = 1.0 - smoothstep(u_pieOuter - feather, u_pieOuter, r);
    float innerM = u_pieInner <= 0.0
        ? 1.0
        : smoothstep(u_pieInner - feather, u_pieInner, r);
    float radial = outerM * innerM;

    float span = u_pieEnd - u_pieStart;
    float angular;
    if (span >= TAU - 1e-4) {
        angular = 1.0;
    } else {
        float a = atan(d.y, d.x) - u_pieStart;
        a = a - floor(a / TAU) * TAU;
        float angFeather = feather / max(r, 1.0);
        float lo = smoothstep(0.0, angFeather, a);
        float hi = 1.0 - smoothstep(span - angFeather, span, a);
        angular = lo * hi;
    }
    return radial * angular;
}

void main() {
    float maskAlpha = (u_mode == 1) ? pieMask() : rectMask();
    if (maskAlpha < 0.001) discard;

    vec2 screenUV = gl_FragCoord.xy / u_screenSize;
    vec4 blurred = sampleBlurred(screenUV, u_blurRadius, u_gamma);

    fragColor = vec4(blurred.rgb * u_tint.rgb, maskAlpha * u_tint.a);
}
