#version 330

uniform mat4 PieProj;

in vec2 Position;

void main() {
    gl_Position = PieProj * vec4(Position, 0.0, 1.0);
}