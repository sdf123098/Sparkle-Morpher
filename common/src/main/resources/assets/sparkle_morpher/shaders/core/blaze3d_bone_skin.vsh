#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// 26.1.2 骨骼矩阵存储：TextureFormat 无 RGBA32F（26.2 用 samplerBuffer），
// 这里用 std140 UBO + 固定大小数组。每骨骼 80B（mat4 transform + vec4 meta，交错），
// 与 CPU 侧 Blaze3DBoneMatrices.write 的 80B/bone 交错布局严格一致
// （std140 struct 数组步长 = align(64+16,16) = 80）。
// 法线矩阵不放 UBO，shader 内 inverse(transpose(mat3)) 求。
// BONE_CAP 必须与 Blaze3DBoneSkinPipeline.BONE_CAP 一致。
#define BONE_CAP 768

struct BoneData {
    mat4 transform;
    vec4 meta;
};

layout(std140) uniform BoneMatrices {
    BoneData bones[BONE_CAP];
};

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uint BoneId;
in uint Cullable;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int packedLight;
flat out uint cullable;
flat out float facingSign;

void main() {
    int bone = int(BoneId);
    vec4 meta = bones[bone].meta;
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

    mat4 boneTransform = bones[bone].transform;
    vec4 eyePos = boneTransform * vec4(Position, 1.0);
    vec3 normal = normalize(transpose(inverse(mat3(boneTransform))) * Normal);

    gl_Position = ProjMat * eyePos;
    sphericalVertexDistance = fog_spherical_distance(eyePos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(eyePos.xyz);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, ColorModulator);
    texCoord0 = UV0;
    packedLight = int(round(meta.x));
    cullable = Cullable;
    facingSign = determinant(mat3(boneTransform)) < 0.0 ? -1.0 : 1.0;
}
