#version 430 core

layout(local_size_x = 64) in;

layout(std430, binding = 0) readonly buffer InputBuf {
    uint raw_in[];
};

layout(std430, binding = 1) writeonly buffer OutputBuf {
    uint raw_out[];
};

struct BoneData {
    mat4 transform;
    mat4 normal;
    int  packedLight;
    int  isHidden;
    int  pad0;
    int  pad1;
};
layout(std430, binding = 2) readonly buffer BoneBlock {
    BoneData bones[];
};

uniform vec4 u_color;
uniform int u_packedOverlay;
uniform mat4 u_modelView;

vec3 unpackNormal_2_10_10_10(uint p) {
    int xi = int(p & 0x3FFu);
    int yi = int((p >> 10) & 0x3FFu);
    int zi = int((p >> 20) & 0x3FFu);
    if (xi >= 512) xi -= 1024;
    if (yi >= 512) yi -= 1024;
    if (zi >= 512) zi -= 1024;
    return vec3(float(xi), float(yi), float(zi)) / 511.0;
}

uint packColorRGBA(vec4 c) {
    uvec4 q = uvec4(clamp(c, 0.0, 1.0) * 255.0 + 0.5);
    return q.r | (q.g << 8) | (q.b << 16) | (q.a << 24);
}

vec3 getLocalPos(uint vIndex) {
    uint inBase = vIndex * 8u;
    return vec3(
        uintBitsToFloat(raw_in[inBase + 0u]),
        uintBitsToFloat(raw_in[inBase + 1u]),
        uintBitsToFloat(raw_in[inBase + 2u])
    );
}

uint packShortPair(int u, int v) {
    return (uint(u) & 0xFFFFu) | ((uint(v) & 0xFFFFu) << 16);
}

uint packNormalBytes(vec3 n) {
    ivec3 q = ivec3(clamp(n, -1.0, 1.0) * 127.0 + sign(n) * 0.5);
    return (uint(q.x) & 0xFFu) | ((uint(q.y) & 0xFFu) << 8) | ((uint(q.z) & 0xFFu) << 16);
}

void main() {
    uint vid = gl_GlobalInvocationID.x;
    uint vertexCount = raw_in.length() / 8u;
    if (vid >= vertexCount) return;
    uint inBase = vid * 8u;
    uint outBase = vid * 9u;
    vec3 localPos = vec3(uintBitsToFloat(raw_in[inBase + 0u]), uintBitsToFloat(raw_in[inBase + 1u]), uintBitsToFloat(raw_in[inBase + 2u]));
    vec2 uv = vec2(uintBitsToFloat(raw_in[inBase + 3u]), uintBitsToFloat(raw_in[inBase + 4u]));
    uint packedNormal = raw_in[inBase + 5u];
    uint boneAndFlags = raw_in[inBase + 6u];
    uint boneId = boneAndFlags & 0xFFFFu;
    BoneData b = bones[boneId];
    vec3 outPos;
    vec3 outNormal;
    bool hidden = (b.isHidden != 0);
    if (hidden) {
        outPos = vec3(2.0, 2.0, 2.0);
        outNormal = vec3(0.0, 0.0, 1.0);
    } else {
        vec4 ep = b.transform * vec4(localPos, 1.0);
        outPos = ep.xyz;
        vec3 nLocal = unpackNormal_2_10_10_10(packedNormal);
        vec3 nEye = normalize((b.normal * vec4(nLocal, 0.0)).xyz);
        outNormal = nEye;

        uint cullableFlag = (boneAndFlags >> 24) & 0xFFu;
        if (cullableFlag > 0u) {
            uint quadBase = vid & ~3u;

            mat4 combinedMatrix = u_modelView * b.transform;

            vec3 view_v0 = (combinedMatrix * vec4(getLocalPos(quadBase + 0u), 1.0)).xyz;
            vec3 view_v1 = (combinedMatrix * vec4(getLocalPos(quadBase + 1u), 1.0)).xyz;
            vec3 view_v2 = (combinedMatrix * vec4(getLocalPos(quadBase + 2u), 1.0)).xyz;

            vec3 geometricNormal = cross(view_v1 - view_v0, view_v2 - view_v0);

            if (dot(view_v0, geometricNormal) >= 0.0) {
                outPos = vec3(2.0, 2.0, 2.0);
                outNormal = vec3(0.0, 0.0, 1.0);
            }
        }
    }
    int blockL = (b.packedLight >> 4) & 0xF;
    int skyL = (b.packedLight >> 20) & 0xF;
    int lightU = blockL * 16;
    int lightV = skyL * 16;
    int ovlU = u_packedOverlay & 0xFFFF;
    int ovlV = (u_packedOverlay >> 16) & 0xFFFF;
    raw_out[outBase + 0u] = floatBitsToUint(outPos.x);
    raw_out[outBase + 1u] = floatBitsToUint(outPos.y);
    raw_out[outBase + 2u] = floatBitsToUint(outPos.z);
    raw_out[outBase + 3u] = packColorRGBA(u_color);
    raw_out[outBase + 4u] = floatBitsToUint(uv.x);
    raw_out[outBase + 5u] = floatBitsToUint(uv.y);
    raw_out[outBase + 6u] = packShortPair(ovlU, ovlV);
    raw_out[outBase + 7u] = packShortPair(lightU, lightV);
    raw_out[outBase + 8u] = packNormalBytes(outNormal);
}
