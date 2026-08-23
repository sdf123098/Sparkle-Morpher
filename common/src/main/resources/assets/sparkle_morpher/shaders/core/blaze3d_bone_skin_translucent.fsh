#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in int packedLight;
flat in uint cullable;
flat in float facingSign;

out vec4 fragColor;

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

    int packedOverlay = int(round(ModelOffset.x));
    int overlayU = packedOverlay & 0xFFFF;
    int overlayV = (packedOverlay >> 16) & 0xFFFF;
    vec4 overlayColor = texelFetch(Sampler1, ivec2(overlayU, overlayV), 0);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    ivec2 lightUv = ivec2(packedLight & 0xFFFF, (packedLight >> 16) & 0xFFFF);
    color *= sample_lightmap(Sampler2, lightUv);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
