#version 430
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(location = 3) in vec2 texCoord0;
layout(location = 0) out vec4 fragColor;
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = ColorModulator;
}
