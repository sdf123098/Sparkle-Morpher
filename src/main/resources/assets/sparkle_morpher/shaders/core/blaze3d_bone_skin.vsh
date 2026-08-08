#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Normal;
in uint BoneId;
in uint Cullable;

uniform samplerBuffer BoneMatrices;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int packedLight;
flat out uint cullable;
flat out float facingSign;

mat4 readBoneMatrix(int bone, int baseTexel) {
    int offset = bone * 9 + baseTexel;
    return mat4(
        texelFetch(BoneMatrices, offset),
        texelFetch(BoneMatrices, offset + 1),
        texelFetch(BoneMatrices, offset + 2),
        texelFetch(BoneMatrices, offset + 3)
    );
}

void main() {
    int bone = int(BoneId);
    vec4 meta = texelFetch(BoneMatrices, bone * 9 + 8);
    if (meta.y > 0.5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        sphericalVertexDistance = 0.0;
        cylindricalVertexDistance = 0.0;
        vertexColor = vec4(0.0);
        texCoord0 = vec2(0.0);
        packedLight = 0;
        cullable = 0u;
        facingSign = 1.0;
        return;
    }

    mat4 boneTransform = readBoneMatrix(bone, 0);
    mat4 normalTransform = readBoneMatrix(bone, 4);
    vec4 eyePos = boneTransform * vec4(Position, 1.0);
    vec3 normal = normalize((normalTransform * vec4(Normal.xyz, 0.0)).xyz);

    gl_Position = ProjMat * eyePos;
    sphericalVertexDistance = fog_spherical_distance(eyePos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(eyePos.xyz);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, ColorModulator);
    texCoord0 = UV0;
    packedLight = int(round(meta.x));
    cullable = Cullable;
    facingSign = determinant(mat3(boneTransform)) < 0.0 ? -1.0 : 1.0;
}
