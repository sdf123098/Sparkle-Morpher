#version 150 core

uniform mat4 u_proj;
uniform vec4 u_rect;

out vec2 v_pos;

void main() {
    vec2 corners[6] = vec2[6](
        vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0),
        vec2(0.0, 1.0), vec2(1.0, 0.0), vec2(0.0, 0.0)
    );
    vec2 c = corners[gl_VertexID];
    vec2 pos = u_rect.xy + c * u_rect.zw;
    gl_Position = u_proj * vec4(pos, 0.0, 1.0);
    v_pos = pos;
}
