#version 430
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = ColorModulator;
}
