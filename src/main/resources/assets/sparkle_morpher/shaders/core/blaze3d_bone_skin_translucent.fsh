#version 430

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) flat in int packedLight;
layout(location = 5) flat in uint cullable;
layout(location = 6) flat in float facingSign;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    bool doCull = facingSign >= 0.0;
    if (doCull && cullable != 0u && !gl_FrontFacing) {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }

    color *= vertexColor;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    int packedOverlay = int(round(ModelOffset.x));
    int overlayU = packedOverlay & 0xFFFF;
    int overlayV = (packedOverlay >> 16) & 0xFFFF;
    vec4 overlayColor = texelFetch(Sampler1, ivec2(overlayU, overlayV), 0);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    ivec2 lightUv = ivec2(packedLight & 0xFFFF, (packedLight >> 16) & 0xFFFF);
    color *= sample_lightmap(Sampler2, lightUv);

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
