#version 150 core

uniform vec2  u_center;
uniform float u_outerRadius;
uniform float u_innerRadius;
uniform float u_startAngle;
uniform float u_endAngle;
uniform vec4  u_color;
uniform float u_feather;

in vec2 v_pos;
out vec4 fragColor;

const float TAU = 6.28318530718;

void main() {
    vec2 d = v_pos - u_center;
    float r = length(d);

    float outerMask = 1.0 - smoothstep(u_outerRadius - u_feather, u_outerRadius, r);
    float innerMask = u_innerRadius <= 0.0
        ? 1.0
        : smoothstep(u_innerRadius - u_feather, u_innerRadius, r);
    float radialMask = outerMask * innerMask;

    float span = u_endAngle - u_startAngle;
    float angularMask;
    if (span >= TAU - 1e-4) {
        angularMask = 1.0;
    } else {
        float a = atan(d.y, d.x) - u_startAngle;
        a = a - floor(a / TAU) * TAU;
        float angFeather = u_feather / max(r, 1.0);
        float lo = smoothstep(0.0, angFeather, a);
        float hi = 1.0 - smoothstep(span - angFeather, span, a);
        angularMask = lo * hi;
    }

    float mask = radialMask * angularMask;
    if (mask <= 0.0) discard;

    fragColor = vec4(u_color.rgb, u_color.a * mask);
}
