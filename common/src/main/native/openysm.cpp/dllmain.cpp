#if defined(__GNUC__) || defined(__clang__)
#pragma GCC optimize("O3,unroll-loops")
#endif

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
#if defined(__GNUC__) || defined(__clang__)
#pragma GCC target("avx2,fma,bmi2")
#endif
#include <immintrin.h>
#elif defined(__aarch64__) || defined(_M_ARM64) || defined(__arm__) || defined(_M_ARM)
#define SSE2NEON_SUPPRESS_WARNINGS
#include "sse2neon.h"
#else
#include <immintrin.h>
#endif

#include <vector>
#include <cmath>
#include <cstring>
#include <cstdio>
#include <functional>
#include <algorithm>
#include <memory>
#include "jni.h"
#include <cstdint>

#if defined(__GLIBC__) || defined(__BIONIC__)
#define FAST_SINCOS(x, s, c) sincosf((x), (s), (c))
#else
inline void ms_sincosf(float x, float *s, float *c) {
    *s = std::sin(x);
    *c = std::cos(x);
}

#define FAST_SINCOS(x, s, c) ms_sincosf((x), (s), (c))
#endif

#if defined(__FMA__)
#define MADD_PS(a, b, c) _mm_fmadd_ps((a), (b), (c))
#else
#define MADD_PS(a, b, c) _mm_add_ps(_mm_mul_ps((a), (b)), (c))
#endif

static jclass g_NativeModelRendererClass = nullptr;
static jmethodID g_submitVerticesID = nullptr;

struct FastQuad {
    int boneIdx;
    bool cullable;
    __m128 x, y, z;
    __m128 u, v;
    float nx, ny, nz;
};

struct NativeBone {
    int parentIdx;
    int partMask;
    bool glow;
    float pivotX, pivotY, pivotZ;
    int quadStart;
    int quadCount;
    int subtreeCount;
    float aabbMin[3];
    float aabbMax[3];
};

struct alignas(16) PrecomputedBoneMats {
    alignas(16) float gb[16];
    __m128 gn_c0, gn_c1, gn_c2;
    int currentLight;
};

enum Uninitialized { UNINITIALIZED };

struct alignas(16) Mat4 {
    float m[16];

    Mat4() { identity(); }

    Mat4(Uninitialized) {
    }

    Mat4(const float *data) { std::memcpy(m, data, 16 * sizeof(float)); }

    inline void identity() {
        std::memset(m, 0, 16 * sizeof(float));
        m[0] = m[5] = m[10] = m[15] = 1.0f;
    }

    inline void mul(const Mat4 &right) {
        __m128 l0 = _mm_load_ps(&m[0]);
        __m128 l1 = _mm_load_ps(&m[4]);
        __m128 l2 = _mm_load_ps(&m[8]);
        __m128 l3 = _mm_load_ps(&m[12]);

        auto mac = [&](const float *r_col) {
            __m128 v = _mm_load_ps(r_col);
            __m128 res = _mm_mul_ps(l0, _mm_shuffle_ps(v, v, _MM_SHUFFLE(0, 0, 0, 0)));
            res = MADD_PS(l1, _mm_shuffle_ps(v, v, _MM_SHUFFLE(1, 1, 1, 1)), res);
            res = MADD_PS(l2, _mm_shuffle_ps(v, v, _MM_SHUFFLE(2, 2, 2, 2)), res);
            res = MADD_PS(l3, _mm_shuffle_ps(v, v, _MM_SHUFFLE(3, 3, 3, 3)), res);
            return res;
        };

        __m128 r0 = mac(&right.m[0]);
        __m128 r1 = mac(&right.m[4]);
        __m128 r2 = mac(&right.m[8]);
        __m128 r3 = mac(&right.m[12]);

        _mm_store_ps(&m[0], r0);
        _mm_store_ps(&m[4], r1);
        _mm_store_ps(&m[8], r2);
        _mm_store_ps(&m[12], r3);
    }

    inline Mat4 normalMatrix4x4() const {
        Mat4 res(UNINITIALIZED);
        res.m[0] = m[0];
        res.m[1] = m[1];
        res.m[2] = m[2];
        res.m[3] = 0.0f;
        res.m[4] = m[4];
        res.m[5] = m[5];
        res.m[6] = m[6];
        res.m[7] = 0.0f;
        res.m[8] = m[8];
        res.m[9] = m[9];
        res.m[10] = m[10];
        res.m[11] = 0.0f;
        res.m[12] = 0.0f;
        res.m[13] = 0.0f;
        res.m[14] = 0.0f;
        res.m[15] = 1.0f;
        return res;
    }
};

struct NativeModel {
    std::vector<NativeBone> bones;
    std::vector<FastQuad> fastQuads;
    std::vector<int> evalOrder;

    std::vector<Mat4> cacheGlobalTransforms;
    std::vector<Mat4> cacheGlobalNormals;
    std::vector<PrecomputedBoneMats> cachePrecompMats;
    std::vector<int> visibleBones;
};

struct GpuVertex {
    float pos[3];
    float uv[2];
    uint32_t normal;
    uint16_t boneId;
    uint8_t partMask;
    uint8_t flags;
    uint32_t pad;
};

static_assert(sizeof(GpuVertex) == 32, "GpuVertex size error");

struct BoneDataOut {
    float transform[16];
    float normal[16];
    int32_t packedLight;
    int32_t isHidden;
    int32_t pad[2];
};

static_assert(sizeof(BoneDataOut) == 144, "BoneDataOut mismatch");

struct NativeGpuMesh {
    std::vector<NativeBone> bones;
    std::vector<int> evalOrder;
    int boneCount = 0;
    std::unique_ptr<GpuVertex[]> vertexData;
    std::unique_ptr<uint32_t[]> indexData;
    int vertexCount = 0;
    int indexCount = 0;
    int partMask1Start = 0, partMask1Count = 0;
    int partMask2Start = 0, partMask2Count = 0;
    int partMask3Start = 0, partMask3Count = 0;
    std::vector<Mat4> globalTransforms;
    std::vector<Mat4> globalNormals;
    std::vector<uint8_t> hiddenInherited;
};

static inline uint32_t packNormal_2_10_10_10_REV(float x, float y, float z) {
    auto pack = [](float v) -> uint32_t {
        int i = static_cast<int>(std::lround(v * 511.0f));
        if (i < -512) i = -512;
        if (i > 511) i = 511;
        return static_cast<uint32_t>(i & 0x3FF);
    };
    return pack(x) | (pack(y) << 10) | (pack(z) << 20);
}

extern "C" {
JNIEXPORT jlong JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nInitModelCache(
    JNIEnv *env, jclass clazz, jobject buffer) {
    char *data = (char *) env->GetDirectBufferAddress(buffer);
    if (!data) return 0;

    NativeModel *model = new NativeModel();
    int offset = 0;

    auto readInt = [&]() {
        int v;
        std::memcpy(&v, data + offset, 4);
        offset += 4;
        return v;
    };
    auto readFloat = [&]() {
        float v;
        std::memcpy(&v, data + offset, 4);
        offset += 4;
        return v;
    };
    auto readByte = [&]() {
        char v = data[offset];
        offset += 1;
        return v;
    };

    int boneCount = readInt();
    model->bones.resize(boneCount);
    model->cacheGlobalTransforms.resize(boneCount);
    model->cacheGlobalNormals.resize(boneCount);
    model->cachePrecompMats.resize(boneCount);
    model->visibleBones.reserve(boneCount);
    model->fastQuads.reserve(boneCount * 20);

    std::vector<std::vector<int> > children(boneCount);

    for (int i = 0; i < boneCount; ++i) {
        NativeBone &bone = model->bones[i];
        bone.parentIdx = readInt();
        if (bone.parentIdx != -1) {
            children[bone.parentIdx].push_back(i);
        }

        bone.partMask = readInt();
        bone.glow = readByte() != 0;
        bone.pivotX = readFloat();
        bone.pivotY = readFloat();
        bone.pivotZ = readFloat();

        bone.quadStart = model->fastQuads.size();

        int cubeCount = readInt();
        for (int j = 0; j < cubeCount; ++j) {
            bool cullable = readByte() != 0;
            int quadCount = readInt();
            for (int k = 0; k < quadCount; ++k) {
                FastQuad fq;
                fq.boneIdx = i;
                fq.cullable = cullable;

                alignas(16) float tmpX[4], tmpY[4], tmpZ[4], tmpU[4], tmpV[4];
                for (int v = 0; v < 4; ++v) {
                    tmpX[v] = readFloat();
                    tmpY[v] = readFloat();
                    tmpZ[v] = readFloat();
                }
                for (int v = 0; v < 4; ++v) {
                    tmpU[v] = readFloat();
                    tmpV[v] = readFloat();
                }
                fq.nx = readFloat();
                fq.ny = readFloat();
                fq.nz = readFloat();

                fq.x = _mm_load_ps(tmpX);
                fq.y = _mm_load_ps(tmpY);
                fq.z = _mm_load_ps(tmpZ);
                fq.u = _mm_load_ps(tmpU);
                fq.v = _mm_load_ps(tmpV);
                model->fastQuads.push_back(fq);
            }
        }
        bone.quadCount = model->fastQuads.size() - bone.quadStart;

        if (bone.quadCount > 0) {
            __m128 vminX = _mm_set1_ps(INFINITY), vmaxX = _mm_set1_ps(-INFINITY);
            __m128 vminY = vminX, vmaxY = vmaxX;
            __m128 vminZ = vminX, vmaxZ = vmaxX;
            for (int q = bone.quadStart; q < bone.quadStart + bone.quadCount; ++q) {
                const FastQuad &fq = model->fastQuads[q];
                vminX = _mm_min_ps(vminX, fq.x);
                vmaxX = _mm_max_ps(vmaxX, fq.x);
                vminY = _mm_min_ps(vminY, fq.y);
                vmaxY = _mm_max_ps(vmaxY, fq.y);
                vminZ = _mm_min_ps(vminZ, fq.z);
                vmaxZ = _mm_max_ps(vmaxZ, fq.z);
            }
            auto hmin = [](__m128 v) {
                v = _mm_min_ps(v, _mm_shuffle_ps(v, v, _MM_SHUFFLE(2, 3, 0, 1)));
                v = _mm_min_ps(v, _mm_shuffle_ps(v, v, _MM_SHUFFLE(1, 0, 3, 2)));
                return _mm_cvtss_f32(v);
            };
            auto hmax = [](__m128 v) {
                v = _mm_max_ps(v, _mm_shuffle_ps(v, v, _MM_SHUFFLE(2, 3, 0, 1)));
                v = _mm_max_ps(v, _mm_shuffle_ps(v, v, _MM_SHUFFLE(1, 0, 3, 2)));
                return _mm_cvtss_f32(v);
            };
            bone.aabbMin[0] = hmin(vminX);
            bone.aabbMax[0] = hmax(vmaxX);
            bone.aabbMin[1] = hmin(vminY);
            bone.aabbMax[1] = hmax(vmaxY);
            bone.aabbMin[2] = hmin(vminZ);
            bone.aabbMax[2] = hmax(vmaxZ);
        } else {
            bone.aabbMin[0] = bone.aabbMin[1] = bone.aabbMin[2] = 0.0f;
            bone.aabbMax[0] = bone.aabbMax[1] = bone.aabbMax[2] = 0.0f;
        }
    }
    model->fastQuads.shrink_to_fit();

    std::function<int(int)> dfs = [&](int idx) -> int {
        model->evalOrder.push_back(idx);
        int count = 0;
        for (int child: children[idx]) {
            count += dfs(child);
        }
        model->bones[idx].subtreeCount = count;
        return count + 1;
    };

    for (int i = 0; i < boneCount; ++i) {
        if (model->bones[i].parentIdx == -1) dfs(i);
    }

    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nDestroyModelCache(
    JNIEnv *env, jclass clazz, jlong handle) {
    delete reinterpret_cast<NativeModel *>(handle);
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeModelVertices(
    JNIEnv *env, jclass clazz, jlong handle, jobject vertexConsumer,
    jfloatArray matrixArray, jfloatArray animArray,
    jint renderPartMask, jint packedLight, jint packedOverlay,
    jfloat r, jfloat g, jfloat b, jfloat a) {
    NativeModel *model = reinterpret_cast<NativeModel *>(handle);
    if (!model || model->fastQuads.empty()) return;

    const __m128 rgba = _mm_setr_ps(r, g, b, a);

    jfloat *matricesData = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(matrixArray, nullptr));
    jfloat *animData = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(animArray, nullptr));

    Mat4 rootPoseMat(matricesData);
    float *rootNormalArr = matricesData + 16;
    Mat4 projMat(matricesData + 32);

    const bool cullingEnabled = std::fabs(projMat.m[11]) > 1e-3f;

    alignas(16) float frustum[6][4];
    if (cullingEnabled) {
        const float *M = projMat.m;
        for (int j = 0; j < 4; ++j) {
            float r0 = M[j * 4 + 0];
            float r1 = M[j * 4 + 1];
            float r2 = M[j * 4 + 2];
            float r3 = M[j * 4 + 3];
            frustum[0][j] = r3 + r0;
            frustum[1][j] = r3 - r0;
            frustum[2][j] = r3 + r1;
            frustum[3][j] = r3 - r1;
            frustum[4][j] = r3 + r2;
            frustum[5][j] = r3 - r2;
        }
    }

    Mat4 rootNormalMat(UNINITIALIZED);
    rootNormalMat.m[0] = rootNormalArr[0];
    rootNormalMat.m[1] = rootNormalArr[1];
    rootNormalMat.m[2] = rootNormalArr[2];
    rootNormalMat.m[3] = 0.0f;
    rootNormalMat.m[4] = rootNormalArr[3];
    rootNormalMat.m[5] = rootNormalArr[4];
    rootNormalMat.m[6] = rootNormalArr[5];
    rootNormalMat.m[7] = 0.0f;
    rootNormalMat.m[8] = rootNormalArr[6];
    rootNormalMat.m[9] = rootNormalArr[7];
    rootNormalMat.m[10] = rootNormalArr[8];
    rootNormalMat.m[11] = 0.0f;
    rootNormalMat.m[12] = 0.0f;
    rootNormalMat.m[13] = 0.0f;
    rootNormalMat.m[14] = 0.0f;
    rootNormalMat.m[15] = 1.0f;

    int glowLight = (15 << 4) | (15 << 20);
    size_t boneCount = model->bones.size();

    model->visibleBones.clear();

    int k = 0;
    while (k < boneCount) {
        int bIdx = model->evalOrder[k];
        NativeBone &bone = model->bones[bIdx];

        int pOffset = bIdx * 12;
        float animRx = animData[pOffset + 0], animRy = animData[pOffset + 1], animRz = animData[pOffset + 2];
        float animTx = animData[pOffset + 3], animTy = animData[pOffset + 4], animTz = animData[pOffset + 5];
        float animSx = animData[pOffset + 6], animSy = animData[pOffset + 7], animSz = animData[pOffset + 8];
        float skipChildrenFlag = animData[pOffset + 10];

        float px = bone.pivotX * 0.0625f, py = bone.pivotY * 0.0625f, pz = bone.pivotZ * 0.0625f;
        float dx = px - animTx * 0.0625f;
        float dy = py + animTy * 0.0625f;
        float dz = pz + animTz * 0.0625f;

        float cx, sx, cy, sy, cz, sz;
        FAST_SINCOS(animRx, &sx, &cx);
        FAST_SINCOS(animRy, &sy, &cy);
        FAST_SINCOS(animRz, &sz, &cz);

        Mat4 localMat(UNINITIALIZED);
        localMat.m[0] = (cz * cy) * animSx;
        localMat.m[1] = (sz * cy) * animSx;
        localMat.m[2] = (-sy) * animSx;
        localMat.m[3] = 0.0f;

        localMat.m[4] = (cz * sy * sx - sz * cx) * animSy;
        localMat.m[5] = (sz * sy * sx + cz * cx) * animSy;
        localMat.m[6] = (cy * sx) * animSy;
        localMat.m[7] = 0.0f;

        localMat.m[8] = (cz * sy * cx + sz * sx) * animSz;
        localMat.m[9] = (sz * sy * cx - cz * sx) * animSz;
        localMat.m[10] = (cy * cx) * animSz;
        localMat.m[11] = 0.0f;

        localMat.m[12] = dx - (localMat.m[0] * px + localMat.m[4] * py + localMat.m[8] * pz);
        localMat.m[13] = dy - (localMat.m[1] * px + localMat.m[5] * py + localMat.m[9] * pz);
        localMat.m[14] = dz - (localMat.m[2] * px + localMat.m[6] * py + localMat.m[10] * pz);
        localMat.m[15] = 1.0f;

        const Mat4 &parentGlobal = (bone.parentIdx != -1) ? model->cacheGlobalTransforms[bone.parentIdx] : rootPoseMat;
        Mat4 &globalMat = model->cacheGlobalTransforms[bIdx];
        globalMat = parentGlobal;
        globalMat.mul(localMat);

        const Mat4 &parentNormal = (bone.parentIdx != -1) ? model->cacheGlobalNormals[bone.parentIdx] : rootNormalMat;
        Mat4 localNormalMat = localMat.normalMatrix4x4();
        Mat4 &globalNormalMat = model->cacheGlobalNormals[bIdx];
        globalNormalMat = parentNormal;
        globalNormalMat.mul(localNormalMat);

        auto &precomp = model->cachePrecompMats[bIdx];
        std::memcpy(precomp.gb, globalMat.m, 16 * sizeof(float));

        precomp.gn_c0 = _mm_load_ps(&globalNormalMat.m[0]);
        precomp.gn_c1 = _mm_load_ps(&globalNormalMat.m[4]);
        precomp.gn_c2 = _mm_load_ps(&globalNormalMat.m[8]);
        precomp.currentLight = bone.glow ? glowLight : packedLight;

        if (animSx == 0.0f || animSy == 0.0f || animSz == 0.0f) {
            k += bone.subtreeCount + 1;
            continue;
        }

        if (cullingEnabled && bone.quadCount > 0) {
            const float *M = globalMat.m;
            float cx = (bone.aabbMax[0] + bone.aabbMin[0]) * 0.5f;
            float cy = (bone.aabbMax[1] + bone.aabbMin[1]) * 0.5f;
            float cz = (bone.aabbMax[2] + bone.aabbMin[2]) * 0.5f;
            float ex = (bone.aabbMax[0] - bone.aabbMin[0]) * 0.5f;
            float ey = (bone.aabbMax[1] - bone.aabbMin[1]) * 0.5f;
            float ez = (bone.aabbMax[2] - bone.aabbMin[2]) * 0.5f;
            float wMin[3], wMax[3];
            for (int row = 0; row < 3; ++row) {
                float m0 = M[0 * 4 + row], m1 = M[1 * 4 + row], m2 = M[2 * 4 + row], m3 = M[3 * 4 + row];
                float wc = m0 * cx + m1 * cy + m2 * cz + m3;
                float we = std::fabs(m0) * ex + std::fabs(m1) * ey + std::fabs(m2) * ez;
                wMin[row] = wc - we;
                wMax[row] = wc + we;
            }
            bool outside = false;
            for (int p = 0; p < 6; ++p) {
                float a = frustum[p][0], b = frustum[p][1], c = frustum[p][2], d = frustum[p][3];
                float px = (a >= 0.0f) ? wMax[0] : wMin[0];
                float py = (b >= 0.0f) ? wMax[1] : wMin[1];
                float pz = (c >= 0.0f) ? wMax[2] : wMin[2];
                if (a * px + b * py + c * pz + d < 0.0f) {
                    outside = true;
                    break;
                }
            }
            if (outside) {
                if (skipChildrenFlag != 0.0f) {
                    k += bone.subtreeCount + 1;
                } else {
                    k++;
                }
                continue;
            }
        }

        model->visibleBones.push_back(bIdx);

        if (skipChildrenFlag != 0.0f) {
            k += bone.subtreeCount + 1;
            continue;
        }
        k++;
    }

    int maxVertices = 0;
    for (int bIdx: model->visibleBones) {
        const NativeBone &bone = model->bones[bIdx];
        if (bone.quadCount == 0) continue;
        if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) continue;
        maxVertices += bone.quadCount * 4;
    }

    if (maxVertices == 0) {
        env->ReleasePrimitiveArrayCritical(matrixArray, matricesData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(animArray, animData, JNI_ABORT);
        return;
    }

    int maxFloats = maxVertices * 12;
    int maxInts = maxVertices * 2;

    static thread_local std::vector<float> fData;
    static thread_local std::vector<int> iData;

    fData.reserve(maxFloats + 4);
    iData.reserve(maxInts);

    float *fPtr = fData.data();
    int *iPtr = iData.data();

    int actualVertices = 0;

    __m128 p00 = _mm_set1_ps(projMat.m[0]), p01 = _mm_set1_ps(projMat.m[4]), p02 = _mm_set1_ps(projMat.m[8]), p03 =
            _mm_set1_ps(projMat.m[12]);
    __m128 p10 = _mm_set1_ps(projMat.m[1]), p11 = _mm_set1_ps(projMat.m[5]), p12 = _mm_set1_ps(projMat.m[9]), p13 =
            _mm_set1_ps(projMat.m[13]);
    __m128 p30 = _mm_set1_ps(projMat.m[3]), p31 = _mm_set1_ps(projMat.m[7]), p32 = _mm_set1_ps(projMat.m[11]), p33 =
            _mm_set1_ps(projMat.m[15]);

    for (int bIdx: model->visibleBones) {
        const NativeBone &bone = model->bones[bIdx];
        if (bone.quadCount == 0) continue;
        if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) continue;

        const auto &pMat = model->cachePrecompMats[bIdx];

        const uint64_t ovl_light64 = (static_cast<uint64_t>(static_cast<uint32_t>(pMat.currentLight)) << 32) |
                                     static_cast<uint32_t>(packedOverlay);

        __m128 gb0 = _mm_set1_ps(pMat.gb[0]), gb1 = _mm_set1_ps(pMat.gb[1]), gb2 = _mm_set1_ps(pMat.gb[2]);
        __m128 gb4 = _mm_set1_ps(pMat.gb[4]), gb5 = _mm_set1_ps(pMat.gb[5]), gb6 = _mm_set1_ps(pMat.gb[6]);
        __m128 gb8 = _mm_set1_ps(pMat.gb[8]), gb9 = _mm_set1_ps(pMat.gb[9]), gb10 = _mm_set1_ps(pMat.gb[10]);
        __m128 gb12 = _mm_set1_ps(pMat.gb[12]), gb13 = _mm_set1_ps(pMat.gb[13]), gb14 = _mm_set1_ps(pMat.gb[14]);

        for (int q = 0; q < bone.quadCount; ++q) {
            const FastQuad &fq = model->fastQuads[bone.quadStart + q];

            __m128 gX = MADD_PS(gb0, fq.x, MADD_PS(gb4, fq.y, MADD_PS(gb8, fq.z, gb12)));
            __m128 gY = MADD_PS(gb1, fq.x, MADD_PS(gb5, fq.y, MADD_PS(gb9, fq.z, gb13)));
            __m128 gZ = MADD_PS(gb2, fq.x, MADD_PS(gb6, fq.y, MADD_PS(gb10, fq.z, gb14)));

            if (fq.cullable) {
                __m128 pX = MADD_PS(p00, gX, MADD_PS(p01, gY, MADD_PS(p02, gZ, p03)));
                __m128 pY = MADD_PS(p10, gX, MADD_PS(p11, gY, MADD_PS(p12, gZ, p13)));
                __m128 pW = MADD_PS(p30, gX, MADD_PS(p31, gY, MADD_PS(p32, gZ, p33)));

                __m128 pY_120 = _mm_shuffle_ps(pY, pY, _MM_SHUFFLE(3, 0, 2, 1));
                __m128 pW_201 = _mm_shuffle_ps(pW, pW, _MM_SHUFFLE(3, 1, 0, 2));
                __m128 pY_201 = _mm_shuffle_ps(pY, pY, _MM_SHUFFLE(3, 1, 0, 2));
                __m128 pW_120 = _mm_shuffle_ps(pW, pW, _MM_SHUFFLE(3, 0, 2, 1));

                __m128 sub = _mm_sub_ps(_mm_mul_ps(pY_120, pW_201), _mm_mul_ps(pY_201, pW_120));
                __m128 mx = _mm_mul_ps(pX, sub);
                __m128 mx1 = _mm_shuffle_ps(mx, mx, _MM_SHUFFLE(1, 1, 1, 1));
                __m128 mx2 = _mm_shuffle_ps(mx, mx, _MM_SHUFFLE(2, 2, 2, 2));
                __m128 sum = _mm_add_ps(mx, _mm_add_ps(mx1, mx2));

                float det = _mm_cvtss_f32(sum);
                if (det <= 0.0f) continue;
            }

            __m128 n_res = MADD_PS(pMat.gn_c0, _mm_set1_ps(fq.nx),
                                   MADD_PS(pMat.gn_c1, _mm_set1_ps(fq.ny), _mm_mul_ps(pMat.gn_c2, _mm_set1_ps(fq.nz))));
            __m128 dp = _mm_mul_ps(n_res, n_res);
            __m128 sum = _mm_add_ps(dp, _mm_shuffle_ps(dp, dp, _MM_SHUFFLE(2, 3, 0, 1)));
            sum = _mm_add_ps(sum, _mm_shuffle_ps(sum, sum, _MM_SHUFFLE(1, 0, 3, 2)));
            n_res = _mm_mul_ps(n_res, _mm_rsqrt_ps(_mm_max_ps(sum, _mm_set1_ps(1e-8f))));

            alignas(16) float fx[4], fy[4], fz[4], fu[4], fv[4];
            _mm_store_ps(fx, gX);
            _mm_store_ps(fy, gY);
            _mm_store_ps(fz, gZ);
            _mm_store_ps(fu, fq.u);
            _mm_store_ps(fv, fq.v);

            for (int v = 0; v < 4; ++v) {
                fPtr[0] = fx[v];
                fPtr[1] = fy[v];
                fPtr[2] = fz[v];
                _mm_storeu_ps(fPtr + 3, rgba);
                fPtr[7] = fu[v];
                fPtr[8] = fv[v];
                _mm_storeu_ps(fPtr + 9, n_res);
                fPtr += 12;

                std::memcpy(iPtr, &ovl_light64, sizeof(uint64_t));
                iPtr += 2;
            }

            actualVertices += 4;
        }
    }

    env->ReleasePrimitiveArrayCritical(matrixArray, matricesData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(animArray, animData, JNI_ABORT);

    if (actualVertices > 0 && g_NativeModelRendererClass && g_submitVerticesID) {
        jobject fBuf = env->NewDirectByteBuffer(fData.data(), static_cast<jlong>(actualVertices) * 12 * sizeof(float));
        jobject iBuf = env->NewDirectByteBuffer(iData.data(), static_cast<jlong>(actualVertices) * 2 * sizeof(int));
        env->CallStaticVoidMethod(g_NativeModelRendererClass, g_submitVerticesID, vertexConsumer, actualVertices, fBuf,
                                  iBuf);
        env->DeleteLocalRef(fBuf);
        env->DeleteLocalRef(iBuf);
    }
}

JNIEXPORT jlong JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nBuildGpuMesh(
    JNIEnv *env, jclass clazz, jobject buffer, jintArray outMeta) {
    const uint8_t *data = reinterpret_cast<const uint8_t *>(env->GetDirectBufferAddress(buffer));
    if (!data) return 0;

    int offset = 0;
    auto readInt = [&]() {
        int v;
        std::memcpy(&v, data + offset, 4);
        offset += 4;
        return v;
    };
    auto readFloat = [&]() {
        float v;
        std::memcpy(&v, data + offset, 4);
        offset += 4;
        return v;
    };
    auto readByte = [&]() {
        uint8_t v = data[offset];
        offset += 1;
        return v;
    };

    auto *mesh = new NativeGpuMesh();
    int boneCount = readInt();
    mesh->boneCount = boneCount;
    mesh->bones.resize(boneCount);
    mesh->globalTransforms.resize(boneCount);
    mesh->globalNormals.resize(boneCount);
    mesh->hiddenInherited.assign(boneCount, 0);

    std::vector<std::vector<int> > children(boneCount);

    struct QuadRecord {
        uint32_t vertexOffset;
        uint8_t partMask;
    };
    std::vector<QuadRecord> quadRecords;
    quadRecords.reserve(1024);

    std::vector<GpuVertex> tmpVerts;
    tmpVerts.reserve(4096);

    for (int i = 0; i < boneCount; ++i) {
        NativeBone &bone = mesh->bones[i];
        bone.parentIdx = readInt();
        if (bone.parentIdx != -1) children[bone.parentIdx].push_back(i);
        bone.partMask = readInt();
        bone.glow = readByte() != 0;
        bone.pivotX = readFloat();
        bone.pivotY = readFloat();
        bone.pivotZ = readFloat();
        bone.quadStart = 0;
        bone.quadCount = 0;
        bone.aabbMin[0] = bone.aabbMin[1] = bone.aabbMin[2] = 0.0f;
        bone.aabbMax[0] = bone.aabbMax[1] = bone.aabbMax[2] = 0.0f;

        int cubeCount = readInt();
        for (int c = 0; c < cubeCount; ++c) {
            uint8_t cullable = readByte() != 0 ? 1 : 0;
            int qc = readInt();
            for (int q = 0; q < qc; ++q) {
                uint32_t vOff = static_cast<uint32_t>(tmpVerts.size());

                float vx[4], vy[4], vz[4], uu[4], vv[4];
                for (int v = 0; v < 4; ++v) {
                    vx[v] = readFloat();
                    vy[v] = readFloat();
                    vz[v] = readFloat();
                }
                for (int v = 0; v < 4; ++v) {
                    uu[v] = readFloat();
                    vv[v] = readFloat();
                }
                float nx = readFloat(), ny = readFloat(), nz = readFloat();
                uint32_t packedNorm = packNormal_2_10_10_10_REV(nx, ny, nz);

                for (int v = 0; v < 4; ++v) {
                    GpuVertex gv;
                    gv.pos[0] = vx[v];
                    gv.pos[1] = vy[v];
                    gv.pos[2] = vz[v];
                    gv.uv[0] = uu[v];
                    gv.uv[1] = vv[v];
                    gv.normal = packedNorm;
                    gv.boneId = static_cast<uint16_t>(i);
                    gv.partMask = static_cast<uint8_t>(bone.partMask);
                    gv.flags = cullable;
                    gv.pad = 0;
                    tmpVerts.push_back(gv);
                }
                quadRecords.push_back({vOff, static_cast<uint8_t>(bone.partMask)});
            }
        }
    }

    std::function<int(int)> dfs = [&](int idx) -> int {
        mesh->evalOrder.push_back(idx);
        int count = 0;
        for (int child: children[idx]) count += dfs(child);
        mesh->bones[idx].subtreeCount = count;
        return count + 1;
    };
    for (int i = 0; i < boneCount; ++i) {
        if (mesh->bones[i].parentIdx == -1) dfs(i);
    }

    std::stable_sort(quadRecords.begin(), quadRecords.end(),
                     [](const QuadRecord &a, const QuadRecord &b) { return a.partMask < b.partMask; });

    mesh->vertexCount = static_cast<int>(tmpVerts.size());
    mesh->vertexData.reset(new GpuVertex[mesh->vertexCount]);
    std::memcpy(mesh->vertexData.get(), tmpVerts.data(), static_cast<size_t>(mesh->vertexCount) * sizeof(GpuVertex));

    mesh->indexCount = static_cast<int>(quadRecords.size()) * 6;
    mesh->indexData.reset(new uint32_t[mesh->indexCount]);

    int currentPartMask = -1;
    int rangeStart = 0;
    auto closeRange = [&](int endIdx) {
        if (currentPartMask < 0) return;
        int count = endIdx - rangeStart;
        switch (currentPartMask) {
            case 1: mesh->partMask1Start = rangeStart;
                mesh->partMask1Count = count;
                break;
            case 2: mesh->partMask2Start = rangeStart;
                mesh->partMask2Count = count;
                break;
            case 3: mesh->partMask3Start = rangeStart;
                mesh->partMask3Count = count;
                break;
            default: break;
        }
    };

    int idxOffset = 0;
    for (const QuadRecord &rec: quadRecords) {
        if (static_cast<int>(rec.partMask) != currentPartMask) {
            closeRange(idxOffset);
            currentPartMask = rec.partMask;
            rangeStart = idxOffset;
        }
        uint32_t v = rec.vertexOffset;
        mesh->indexData[idxOffset + 0] = v + 0;
        mesh->indexData[idxOffset + 1] = v + 1;
        mesh->indexData[idxOffset + 2] = v + 2;
        mesh->indexData[idxOffset + 3] = v + 0;
        mesh->indexData[idxOffset + 4] = v + 2;
        mesh->indexData[idxOffset + 5] = v + 3;
        idxOffset += 6;
    }
    closeRange(idxOffset);

    if (outMeta != nullptr && env->GetArrayLength(outMeta) >= 9) {
        jint vals[9] = {
            mesh->vertexCount,
            mesh->indexCount,
            mesh->boneCount,
            mesh->partMask1Start, mesh->partMask1Count,
            mesh->partMask2Start, mesh->partMask2Count,
            mesh->partMask3Start, mesh->partMask3Count,
        };
        env->SetIntArrayRegion(outMeta, 0, 9, vals);
    }

    return reinterpret_cast<jlong>(mesh);
}

JNIEXPORT jobject JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nGetGpuMeshVertexBuffer(
    JNIEnv *env, jclass clazz, jlong handle) {
    auto *mesh = reinterpret_cast<NativeGpuMesh *>(handle);
    if (!mesh || !mesh->vertexData) return nullptr;
    return env->NewDirectByteBuffer(mesh->vertexData.get(), static_cast<jlong>(mesh->vertexCount) * sizeof(GpuVertex));
}

JNIEXPORT jobject JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nGetGpuMeshIndexBuffer(
    JNIEnv *env, jclass clazz, jlong handle) {
    auto *mesh = reinterpret_cast<NativeGpuMesh *>(handle);
    if (!mesh || !mesh->indexData) return nullptr;
    return env->NewDirectByteBuffer(mesh->indexData.get(), static_cast<jlong>(mesh->indexCount) * sizeof(uint32_t));
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nReleaseGpuMeshScratch(
    JNIEnv *env, jclass clazz, jlong handle) {
    auto *mesh = reinterpret_cast<NativeGpuMesh *>(handle);
    if (!mesh) return;
    mesh->vertexData.reset();
    mesh->indexData.reset();
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nFreeGpuMesh(
    JNIEnv *env, jclass clazz, jlong handle) {
    delete reinterpret_cast<NativeGpuMesh *>(handle);
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeBoneMatrices(
    JNIEnv *env, jclass clazz, jlong handle,
    jfloatArray rootPoseArr, jfloatArray rootNormalArr, jfloatArray animArray,
    jint packedLight, jobject outBoneBuffer) {
    auto *mesh = reinterpret_cast<NativeGpuMesh *>(handle);
    if (!mesh) return;

    auto *outRaw = static_cast<BoneDataOut *>(env->GetDirectBufferAddress(outBoneBuffer));
    if (!outRaw) return;

    jfloat *rootPose = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(rootPoseArr, nullptr));
    jfloat *rootNormal = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(rootNormalArr, nullptr));
    jfloat *anim = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(animArray, nullptr));

    Mat4 rootPoseMat(rootPose);
    Mat4 rootNormalMat(UNINITIALIZED);
    rootNormalMat.m[0] = rootNormal[0];
    rootNormalMat.m[1] = rootNormal[1];
    rootNormalMat.m[2] = rootNormal[2];
    rootNormalMat.m[3] = 0.0f;
    rootNormalMat.m[4] = rootNormal[3];
    rootNormalMat.m[5] = rootNormal[4];
    rootNormalMat.m[6] = rootNormal[5];
    rootNormalMat.m[7] = 0.0f;
    rootNormalMat.m[8] = rootNormal[6];
    rootNormalMat.m[9] = rootNormal[7];
    rootNormalMat.m[10] = rootNormal[8];
    rootNormalMat.m[11] = 0.0f;
    rootNormalMat.m[12] = 0.0f;
    rootNormalMat.m[13] = 0.0f;
    rootNormalMat.m[14] = 0.0f;
    rootNormalMat.m[15] = 1.0f;

    const int glowLight = (15 << 4) | (15 << 20);
    std::fill(mesh->hiddenInherited.begin(), mesh->hiddenInherited.end(), 0);

    for (int bIdx: mesh->evalOrder) {
        const NativeBone &bone = mesh->bones[bIdx];

        int pOffset = bIdx * 12;
        float animRx = anim[pOffset + 0], animRy = anim[pOffset + 1], animRz = anim[pOffset + 2];
        float animTx = anim[pOffset + 3], animTy = anim[pOffset + 4], animTz = anim[pOffset + 5];
        float animSx = anim[pOffset + 6], animSy = anim[pOffset + 7], animSz = anim[pOffset + 8];
        float skipChildrenFlag = anim[pOffset + 10];

        float px = bone.pivotX * 0.0625f, py = bone.pivotY * 0.0625f, pz = bone.pivotZ * 0.0625f;
        float dx = px - animTx * 0.0625f;
        float dy = py + animTy * 0.0625f;
        float dz = pz + animTz * 0.0625f;

        float cx, sx, cy, sy, cz, sz;
        FAST_SINCOS(animRx, &sx, &cx);
        FAST_SINCOS(animRy, &sy, &cy);
        FAST_SINCOS(animRz, &sz, &cz);

        Mat4 localMat(UNINITIALIZED);
        localMat.m[0] = (cz * cy) * animSx;
        localMat.m[1] = (sz * cy) * animSx;
        localMat.m[2] = (-sy) * animSx;
        localMat.m[3] = 0.0f;
        localMat.m[4] = (cz * sy * sx - sz * cx) * animSy;
        localMat.m[5] = (sz * sy * sx + cz * cx) * animSy;
        localMat.m[6] = (cy * sx) * animSy;
        localMat.m[7] = 0.0f;
        localMat.m[8] = (cz * sy * cx + sz * sx) * animSz;
        localMat.m[9] = (sz * sy * cx - cz * sx) * animSz;
        localMat.m[10] = (cy * cx) * animSz;
        localMat.m[11] = 0.0f;
        localMat.m[12] = dx - (localMat.m[0] * px + localMat.m[4] * py + localMat.m[8] * pz);
        localMat.m[13] = dy - (localMat.m[1] * px + localMat.m[5] * py + localMat.m[9] * pz);
        localMat.m[14] = dz - (localMat.m[2] * px + localMat.m[6] * py + localMat.m[10] * pz);
        localMat.m[15] = 1.0f;

        bool inheritedHidden = (bone.parentIdx != -1) && mesh->hiddenInherited[bone.parentIdx] != 0;
        bool selfHidden = inheritedHidden || (animSx == 0.0f || animSy == 0.0f || animSz == 0.0f);

        const Mat4 &parentGlobal = (bone.parentIdx != -1) ? mesh->globalTransforms[bone.parentIdx] : rootPoseMat;
        Mat4 &globalMat = mesh->globalTransforms[bIdx];
        globalMat = parentGlobal;
        globalMat.mul(localMat);

        const Mat4 &parentNormal = (bone.parentIdx != -1) ? mesh->globalNormals[bone.parentIdx] : rootNormalMat;
        Mat4 localNormalMat = localMat.normalMatrix4x4();
        Mat4 &globalNormalMat = mesh->globalNormals[bIdx];
        globalNormalMat = parentNormal;
        globalNormalMat.mul(localNormalMat);

        BoneDataOut &out = outRaw[bIdx];
        std::memcpy(out.transform, globalMat.m, 64);
        std::memcpy(out.normal, globalNormalMat.m, 64);
        out.packedLight = bone.glow ? glowLight : packedLight;
        out.isHidden = selfHidden ? 1 : 0;
        out.pad[0] = out.pad[1] = 0;

        mesh->hiddenInherited[bIdx] = (selfHidden || skipChildrenFlag != 0.0f) ? 1 : 0;
    }

    env->ReleasePrimitiveArrayCritical(rootPoseArr, rootPose, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(rootNormalArr, rootNormal, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(animArray, anim, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeBoneMatricesLocal(
    JNIEnv *env, jclass clazz, jlong handle, jfloatArray animArray, jint packedLight, jobject outBoneBuffer) {
    auto *mesh = reinterpret_cast<NativeGpuMesh *>(handle);
    if (!mesh) return;

    auto *outRaw = static_cast<BoneDataOut *>(env->GetDirectBufferAddress(outBoneBuffer));
    if (!outRaw) return;

    jfloat *anim = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(animArray, nullptr));

    const int glowLight = (15 << 4) | (15 << 20);
    std::fill(mesh->hiddenInherited.begin(), mesh->hiddenInherited.end(), 0);

    for (int bIdx: mesh->evalOrder) {
        const NativeBone &bone = mesh->bones[bIdx];

        int pOffset = bIdx * 12;
        float animRx = anim[pOffset + 0], animRy = anim[pOffset + 1], animRz = anim[pOffset + 2];
        float animTx = anim[pOffset + 3], animTy = anim[pOffset + 4], animTz = anim[pOffset + 5];
        float animSx = anim[pOffset + 6], animSy = anim[pOffset + 7], animSz = anim[pOffset + 8];
        float skipChildrenFlag = anim[pOffset + 10];

        float px = bone.pivotX * 0.0625f, py = bone.pivotY * 0.0625f, pz = bone.pivotZ * 0.0625f;
        float dx = px - animTx * 0.0625f;
        float dy = py + animTy * 0.0625f;
        float dz = pz + animTz * 0.0625f;

        float cx, sx, cy, sy, cz, sz;
        FAST_SINCOS(animRx, &sx, &cx);
        FAST_SINCOS(animRy, &sy, &cy);
        FAST_SINCOS(animRz, &sz, &cz);

        Mat4 localMat(UNINITIALIZED);
        localMat.m[0] = (cz * cy) * animSx;
        localMat.m[1] = (sz * cy) * animSx;
        localMat.m[2] = (-sy) * animSx;
        localMat.m[3] = 0.0f;
        localMat.m[4] = (cz * sy * sx - sz * cx) * animSy;
        localMat.m[5] = (sz * sy * sx + cz * cx) * animSy;
        localMat.m[6] = (cy * sx) * animSy;
        localMat.m[7] = 0.0f;
        localMat.m[8] = (cz * sy * cx + sz * sx) * animSz;
        localMat.m[9] = (sz * sy * cx - cz * sx) * animSz;
        localMat.m[10] = (cy * cx) * animSz;
        localMat.m[11] = 0.0f;
        localMat.m[12] = dx - (localMat.m[0] * px + localMat.m[4] * py + localMat.m[8] * pz);
        localMat.m[13] = dy - (localMat.m[1] * px + localMat.m[5] * py + localMat.m[9] * pz);
        localMat.m[14] = dz - (localMat.m[2] * px + localMat.m[6] * py + localMat.m[10] * pz);
        localMat.m[15] = 1.0f;

        bool inheritedHidden = (bone.parentIdx != -1) && mesh->hiddenInherited[bone.parentIdx] != 0;
        bool selfHidden = inheritedHidden || (animSx == 0.0f || animSy == 0.0f || animSz == 0.0f);

        Mat4 &globalMat = mesh->globalTransforms[bIdx];
        Mat4 localNormalMat = localMat.normalMatrix4x4();
        Mat4 &globalNormalMat = mesh->globalNormals[bIdx];

        if (bone.parentIdx != -1) {
            globalMat = mesh->globalTransforms[bone.parentIdx];
            globalMat.mul(localMat);

            globalNormalMat = mesh->globalNormals[bone.parentIdx];
            globalNormalMat.mul(localNormalMat);
        } else {
            globalMat = localMat;
            globalNormalMat = localNormalMat;
        }

        BoneDataOut &out = outRaw[bIdx];
        std::memcpy(out.transform, globalMat.m, 64);
        std::memcpy(out.normal, globalNormalMat.m, 64);
        out.packedLight = bone.glow ? glowLight : packedLight;
        out.isHidden = selfHidden ? 1 : 0;
        out.pad[0] = out.pad[1] = 0;

        mesh->hiddenInherited[bIdx] = (selfHidden || skipChildrenFlag != 0.0f) ? 1 : 0;
    }

    env->ReleasePrimitiveArrayCritical(animArray, anim, JNI_ABORT);
}

static const JNINativeMethod gMethods[] = {
    {
        (char *) "nInitModelCache", (char *) "(Ljava/nio/ByteBuffer;)J",
        reinterpret_cast<void *>(Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nInitModelCache)
    },
    {
        (char *) "nDestroyModelCache", (char *) "(J)V",
        reinterpret_cast<void *>(Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nDestroyModelCache)
    },
    {
        (char *) "nComputeModelVertices", (char *) "(JLjava/lang/Object;[F[FIIIFFFF)V",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeModelVertices)
    },
    {
        (char *) "nBuildGpuMesh", (char *) "(Ljava/nio/ByteBuffer;[I)J",
        reinterpret_cast<void *>(Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nBuildGpuMesh)
    },
    {
        (char *) "nGetGpuMeshVertexBuffer", (char *) "(J)Ljava/nio/ByteBuffer;",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nGetGpuMeshVertexBuffer)
    },
    {
        (char *) "nGetGpuMeshIndexBuffer", (char *) "(J)Ljava/nio/ByteBuffer;",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nGetGpuMeshIndexBuffer)
    },
    {
        (char *) "nReleaseGpuMeshScratch", (char *) "(J)V",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nReleaseGpuMeshScratch)
    },
    {
        (char *) "nFreeGpuMesh", (char *) "(J)V",
        reinterpret_cast<void *>(Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nFreeGpuMesh)
    },
    {
        (char *) "nComputeBoneMatrices", (char *) "(J[F[F[FILjava/nio/ByteBuffer;)V",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeBoneMatrices)
    },
    {
        (char *) "nComputeBoneMatricesLocal", (char *) "(J[FILjava/nio/ByteBuffer;)V",
        reinterpret_cast<void *>(
            Java_com_elfmcys_yesstevemodel_geckolib3_geo_render_built_GeoModel_nComputeBoneMatricesLocal)
    },
};

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
#include <cpuid.h>
__attribute__((target("no-avx2,no-fma")))
static bool hasRequiredCpuSupport() {
    unsigned int eax, ebx, ecx, edx;

    if (!__get_cpuid(0, &eax, &ebx, &ecx, &edx)) return false;
    if (eax < 7) return false;

    if (!__get_cpuid(1, &eax, &ebx, &ecx, &edx)) return false;
    if (!(ecx & (1u << 12))) return false;
    if (!(ecx & (1u << 27))) return false;
    if (!(ecx & (1u << 28))) return false;

    unsigned int xcr0_lo, xcr0_hi;
    __asm__ volatile(".byte 0x0f, 0x01, 0xd0" : "=a"(xcr0_lo), "=d"(xcr0_hi) : "c"(0));
    if ((xcr0_lo & 0x6) != 0x6) return false;

    if (!__get_cpuid_count(7, 0, &eax, &ebx, &ecx, &edx)) return false;
    if (!(ebx & (1u << 5))) return false;

    return true;
}
#else
static bool hasRequiredCpuSupport() { return true; }
#endif

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    if (!hasRequiredCpuSupport()) {
        return JNI_ERR;
    }

    jclass clazzModel = env->FindClass("com/elfmcys/yesstevemodel/geckolib3/geo/render/built/GeoModel");
    if (clazzModel == nullptr) return JNI_ERR;
    if (env->RegisterNatives(clazzModel, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0) return JNI_ERR;

    jclass clazzRenderer = env->FindClass("com/elfmcys/yesstevemodel/geckolib3/geo/NativeModelRenderer");
    if (clazzRenderer != nullptr) {
        g_NativeModelRendererClass = (jclass) env->NewGlobalRef(clazzRenderer);
        g_submitVerticesID = env->GetStaticMethodID(g_NativeModelRendererClass, "submitVertices",
                                                    "(Ljava/lang/Object;ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V");
    }

    return JNI_VERSION_1_6;
}
} // extern "C"
