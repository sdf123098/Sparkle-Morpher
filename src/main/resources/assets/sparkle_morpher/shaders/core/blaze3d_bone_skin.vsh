#version 430

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Normal;
layout(location = 3) in uint BoneId;
layout(location = 4) in uint Cullable;

layout(std430) readonly buffer SpmBoneStorage {
    vec4 spmBoneData[];
};

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) flat out int packedLight;
layout(location = 5) flat out uint cullable;
layout(location = 6) flat out float facingSign;

mat4 readBoneMatrix(int bone, int baseTexel) {
    int offset = bone * 9 + baseTexel;
    return mat4(
        spmBoneData[offset],
        spmBoneData[offset + 1],
        spmBoneData[offset + 2],
        spmBoneData[offset + 3]
    );
}

void main() {
    int bone = int(BoneId);
    vec4 meta = spmBoneData[bone * 9 + 8];
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
    vec4 eyePos = ModelViewMat * boneTransform * vec4(Position, 1.0);
    vec3 normal = normalize(mat3(ModelViewMat) * (normalTransform * vec4(Normal.xyz, 0.0)).xyz);

    gl_Position = ProjMat * eyePos;
    sphericalVertexDistance = fog_spherical_distance(eyePos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(eyePos.xyz);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, ColorModulator);
    texCoord0 = UV0;
    packedLight = int(round(meta.x));
    cullable = Cullable;
    facingSign = determinant(mat3(boneTransform)) < 0.0 ? -1.0 : 1.0;
}
