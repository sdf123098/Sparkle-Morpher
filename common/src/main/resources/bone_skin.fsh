#version 430 core

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform int   u_packedOverlay;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform vec4  u_fogColor;
uniform int   u_alphaMode;
in float v_cullable;

in vec2  v_uv;
in vec3  v_normal;
in vec4  v_color;
in float v_vertexDistance;
flat in int v_packedLight;

out vec4 fragColor;

vec4 linearFog(vec4 inColor, float vd, float fs, float fe, vec4 fc) {
    if (vd <= fs) return inColor;
    float t = vd < fe ? smoothstep(fs, fe, vd) : 1.0;
    return vec4(mix(inColor.rgb, fc.rgb, t * fc.a), inColor.a);
}

void main() {
    if (u_alphaMode != 2 && v_cullable > 0.5 && !gl_FrontFacing) {
        discard;
    }

    vec4 texColor = texture(Sampler0, v_uv);
    if (texColor.a < 0.1) discard;
    if (u_alphaMode == 1 && texColor.a < 0.99) discard;
    if (u_alphaMode == 2 && texColor.a >= 0.99) discard;

    vec4 color = texColor * v_color;

    int oU = u_packedOverlay & 0xFFFF;
    int oV = (u_packedOverlay >> 16) & 0xFFFF;
    vec4 overlayColor = texelFetch(Sampler1, ivec2(oU, oV), 0);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    int blockUV = (v_packedLight & 0xFFFF) / 16;
    int skyUV   = ((v_packedLight >> 16) & 0xFFFF) / 16;
    vec4 lightColor = texelFetch(Sampler2, ivec2(blockUV, skyUV), 0);
    color.rgb *= lightColor.rgb;

    fragColor = linearFog(color, v_vertexDistance, u_fogStart, u_fogEnd, u_fogColor);
}
