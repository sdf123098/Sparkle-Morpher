package com.elfmcys.yesstevemodel.geckolib3.geo.render.built;

import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.resource.models.GeometryDescription;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;
import org.joml.Vector3f;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Bedrock的.geo模型文件
 */
public class GeoModel {

    @NotNull
    public final List<GeoBone> bones;

    @NotNull
    public final IntList leftHandIds;

    @NotNull
    public final IntList rightHandIds;

    @NotNull
    public final IntList elytraIds;

    @NotNull
    public final IntList tacPistolIds;

    @NotNull
    public final IntList tacRifleIds;

    @NotNull
    public final IntList leftWaistIds;

    @NotNull
    public final IntList rightWaistIds;

    @NotNull
    public final IntList leftShoulderIds;

    @NotNull
    public final IntList rightShoulderIds;

    @NotNull
    public final IntList bladeIds;

    @NotNull
    public final IntList sheathIds;

    @NotNull
    public final IntList headIds;

    @NotNull
    public final IntList backpackIds;

    public final boolean hasCustomLeftHand;

    public final boolean hasCustomRightHand;

    public final boolean hasCustomLimbs;

    @NotNull
    private final GeometryDescription properties;

    public final float[] boneTransformData;

    private boolean[] translucentTexture;

    @NotNull
    public final List<IntList> extraLeftHandGroups = new ObjectArrayList<>();

    @NotNull
    public final List<IntList> extraRightHandGroups = new ObjectArrayList<>();

    @NotNull
    public final List<IntList> passengerGroups = new ObjectArrayList<>();

    public List<BakedBone> bakedBones;

    public int[] bakedBoneOrder = new int[0];
    public int[] bakedBoneRenderOrderAll = new int[0];
    public int[] bakedBoneRenderOrderLeftArm = new int[0];
    public int[] bakedBoneRenderOrderRightArm = new int[0];

    public static class BakedBone {
        public String name;
        public boolean glow;
        public int parentIdx = -1;
        public float pivotX, pivotY, pivotZ;
        public float rotX, rotY, rotZ;
        public List<BakedCube> cubes = new ObjectArrayList<>();
        public int partMask;
    }

    public static class BakedCube {
        public boolean cullable = false;
        //        public float pivotX, pivotY, pivotZ;
//        public float rotX, rotY, rotZ;
        public List<BakedQuad> quads = new ObjectArrayList<>();
    }

    public static class BakedQuad {
        public Vector3f[] positions = new Vector3f[4];
        public Vector2f[] uvs = new Vector2f[4];
        public Vector3f normal;
        public final float[] packedPositions = new float[12];
        public final float[] packedUvs = new float[8];
        public float normalX, normalY, normalZ;

        public void setNormal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            this.normal = new Vector3f(x, y, z);
        }

        public void setVertex(int index, float x, float y, float z, float u, float v) {
            int posOffset = index * 3;
            packedPositions[posOffset] = x;
            packedPositions[posOffset + 1] = y;
            packedPositions[posOffset + 2] = z;
            int uvOffset = index * 2;
            packedUvs[uvOffset] = u;
            packedUvs[uvOffset + 1] = v;
            positions[index] = new Vector3f(x, y, z);
            uvs[index] = new Vector2f(u, v);
        }

        public float x(int index) {
            return packedPositions[index * 3];
        }

        public float y(int index) {
            return packedPositions[index * 3 + 1];
        }

        public float z(int index) {
            return packedPositions[index * 3 + 2];
        }

        public float u(int index) {
            return packedUvs[index * 2];
        }

        public float v(int index) {
            return packedUvs[index * 2 + 1];
        }
    }

    public static int[] buildParentFirstBoneOrder(List<BakedBone> bones) {
        int count = bones == null ? 0 : bones.size();
        IntArrayList order = new IntArrayList(count);
        byte[] state = new byte[count];
        for (int i = 0; i < count; i++) {
            appendParentFirstBone(i, bones, state, order);
        }
        return order.toIntArray();
    }

    private static void appendParentFirstBone(int idx, List<BakedBone> bones, byte[] state, IntArrayList order) {
        if (idx < 0 || idx >= bones.size() || state[idx] == 2) {
            return;
        }
        if (state[idx] == 1) {
            return;
        }
        state[idx] = 1;
        int parentIdx = bones.get(idx).parentIdx;
        if (parentIdx != idx) {
            appendParentFirstBone(parentIdx, bones, state, order);
        }
        state[idx] = 2;
        order.add(idx);
    }

    public void buildPartMaskBoneRenderOrders() {
        this.bakedBoneRenderOrderAll = buildPartMaskBoneRenderOrder(0);
        this.bakedBoneRenderOrderLeftArm = buildPartMaskBoneRenderOrder(1);
        this.bakedBoneRenderOrderRightArm = buildPartMaskBoneRenderOrder(2);
    }

    public int[] getPartMaskBoneRenderOrder(int renderPartMask) {
        return switch (renderPartMask) {
            case 1 -> bakedBoneRenderOrderLeftArm;
            case 2 -> bakedBoneRenderOrderRightArm;
            default -> bakedBoneRenderOrderAll;
        };
    }

    private int[] buildPartMaskBoneRenderOrder(int renderPartMask) {
        if (bakedBones == null || bakedBones.isEmpty()) {
            return new int[0];
        }
        int[] baseOrder = bakedBoneOrder;
        if (baseOrder == null || baseOrder.length != bakedBones.size()) {
            baseOrder = buildParentFirstBoneOrder(bakedBones);
        }
        IntArrayList result = new IntArrayList(baseOrder.length);
        for (int boneIdx : baseOrder) {
            if (boneIdx < 0 || boneIdx >= bakedBones.size()) {
                continue;
            }
            BakedBone bone = bakedBones.get(boneIdx);
            if (renderPartMask == 0 || bone.partMask == renderPartMask || bone.partMask == 3) {
                result.add(boneIdx);
            }
        }
        return result.toIntArray();
    }

//    static {
//        System.load("test.dll");
//    }

    public long nativeModelHandle = 0;

    public long gpuMeshHandle = 0;

    public static long nInitModelCache(ByteBuffer buffer) {
        return ModelAccelerationBridge.nInitModelCache(buffer);
    }

    public static void nDestroyModelCache(long handle) {
        ModelAccelerationBridge.nDestroyModelCache(handle);
    }

    public static void nComputeModelVertices(
            long handle, Object vertexConsumer,
            float[] matrixTransfer, float[] animTransfer,
            int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a) {
        ModelAccelerationBridge.nComputeModelVertices(handle, vertexConsumer, matrixTransfer, animTransfer, renderPartMask, packedLight, packedOverlay, r, g, b, a);
    }

    public static long nBuildGpuMesh(ByteBuffer buffer, int[] outMeta) {
        return ModelAccelerationBridge.nBuildGpuMesh(buffer, outMeta);
    }

    public static ByteBuffer nGetGpuMeshVertexBuffer(long pointer) {
        return ModelAccelerationBridge.nGetGpuMeshVertexBuffer(pointer);
    }

    public static ByteBuffer nGetGpuMeshIndexBuffer(long pointer) {
        return ModelAccelerationBridge.nGetGpuMeshIndexBuffer(pointer);
    }

    public static void nReleaseGpuMeshScratch(long pointer) {
        ModelAccelerationBridge.nReleaseGpuMeshScratch(pointer);
    }

    public static void nFreeGpuMesh(long pointer) {
        ModelAccelerationBridge.nFreeGpuMesh(pointer);
    }

    public static void nComputeBoneMatrices(long pointer, float[] rootPose, float[] rootNormal, float[] anim, int packedLight, ByteBuffer outBoneBuffer) {
        ModelAccelerationBridge.nComputeBoneMatrices(pointer, rootPose, rootNormal, anim, packedLight, outBoneBuffer);
    }

    public static void nComputeBoneMatricesLocal(long handle, float[] animArray, int packedLight, ByteBuffer outBoneBuffer) {
        ModelAccelerationBridge.nComputeBoneMatricesLocal(handle, animArray, packedLight, outBoneBuffer);
    }

    public void buildNativeCache() {
        if (bakedBones == null || bakedBones.isEmpty()) return;

        int totalBones = bakedBones.size();
        int totalCubes = 0;
        int totalQuads = 0;

        for (BakedBone bone : bakedBones) {
            totalCubes += bone.cubes.size();
            for (BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }

        int initBufferSize = 4 + (totalBones * 25) + (totalCubes * 5) + (totalQuads * 92);
        ByteBuffer buffer = MemoryUtil.memAlloc(initBufferSize).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, initBufferSize);
        try {
            buffer.putInt(bakedBones.size());
            for (BakedBone bone : bakedBones) {
                buffer.putInt(bone.parentIdx);
                buffer.putInt(bone.partMask);
                buffer.put((byte) (bone.glow ? 1 : 0));
                buffer.putFloat(bone.pivotX);
                buffer.putFloat(bone.pivotY);
                buffer.putFloat(bone.pivotZ);

                buffer.putInt(bone.cubes.size());
                for (BakedCube cube : bone.cubes) {
                    buffer.put((byte) (cube.cullable ? 1 : 0));
                    buffer.putInt(cube.quads.size());
                    for (BakedQuad quad : cube.quads) {
                        for (int v = 0; v < 4; v++) {
                            buffer.putFloat(quad.x(v));
                            buffer.putFloat(quad.y(v));
                            buffer.putFloat(quad.z(v));
                        }
                        for (int v = 0; v < 4; v++) {
                            buffer.putFloat(quad.u(v));
                            buffer.putFloat(quad.v(v));
                        }
                        // 3 floats *4=12
                        buffer.putFloat(quad.normalX);
                        buffer.putFloat(quad.normalY);
                        buffer.putFloat(quad.normalZ);
                    }
                }
            }

            buffer.position(0);
            this.nativeModelHandle = nInitModelCache(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
            ResourceLifecycleStats.onDirectBufferFreed(null, initBufferSize);
        }
    }

    public void freeNativeCache() {
        if (nativeModelHandle != 0) {
            nDestroyModelCache(nativeModelHandle);
            nativeModelHandle = 0;
        }
        freeGpuCache();
    }

    public boolean freeGpuCache() {
        if (gpuMeshHandle != 0) {
            GpuRenderPath.disposeMesh(this);
            return true;
        }
        return false;
    }

    public GeoModel(GeoBone[] geoBones, String[][] strArr, boolean[] zArr, @NotNull GeometryDescription properties, boolean[] zArr2) {
        this.bones = ObjectLists.unmodifiable(ObjectArrayList.wrap(geoBones));
        this.leftHandIds = resolveBoneIds(strArr[0]);
        this.rightHandIds = resolveBoneIds(strArr[1]);
        this.elytraIds = resolveBoneIds(strArr[2]);
        this.tacPistolIds = resolveBoneIds(strArr[3]);
        this.tacRifleIds = resolveBoneIds(strArr[4]);
        this.leftWaistIds = resolveBoneIds(strArr[5]);
        this.rightWaistIds = resolveBoneIds(strArr[6]);
        this.leftShoulderIds = resolveBoneIds(strArr[7]);
        this.rightShoulderIds = resolveBoneIds(strArr[8]);
        this.bladeIds = resolveBoneIds(strArr[9]);
        this.sheathIds = resolveBoneIds(strArr[10]);
        this.headIds = resolveBoneIds(strArr[11]);
        this.backpackIds = resolveBoneIds(strArr[12]);
        for (int i = 13; i <= 19; i++) {
            String[] strArr2 = strArr[i];
            if (strArr2.length > 0) {
                this.extraLeftHandGroups.add(resolveBoneIds(strArr2));
            }
        }
        for (int i = 20; i <= 26; i++) {
            String[] strArr3 = strArr[i];
            if (strArr3.length > 0) {
                this.extraRightHandGroups.add(resolveBoneIds(strArr3));
            }
        }
        for (int i = 27; i <= 34; i++) {
            String[] strArr4 = strArr[i];
            if (strArr4.length > 0) {
                this.passengerGroups.add(resolveBoneIds(strArr4));
            }
        }
        this.hasCustomLeftHand = zArr[0]; // has left hand?
        this.hasCustomRightHand = zArr[1]; // has right hand?
        this.hasCustomLimbs = zArr[2]; // has background
        this.translucentTexture = zArr2;
        this.properties = properties;
        this.boneTransformData = new AnimatedGeoModel(this).getMatrixData();
    }

    private static IntList resolveBoneIds(String[] strArr) {
        IntArrayList intArrayList = new IntArrayList(strArr.length);
        for (String str : strArr) {
            intArrayList.add(StringPool.computeIfAbsent(str));
        }
        return IntLists.unmodifiable(intArrayList);
    }

    @NotNull
    public List<GeoBone> topLevelBones() {
        return this.bones;
    }

    public float[] getBoneTransformData() {
        return this.boneTransformData;
    }

    @NotNull
    public GeometryDescription getProperties() {
        return this.properties;
    }

    public boolean isTranslucentTexture(int i) {
        if (i < 0 || i >= this.translucentTexture.length) {
            return false;
        }
        return this.translucentTexture[i];
    }

    public void setTranslucentTexture(int i, boolean translucent) {
        ensureTranslucentTextureCapacity(i);
        this.translucentTexture[i] = translucent;
    }

    private void ensureTranslucentTextureCapacity(int maxIndex) {
        if (maxIndex >= this.translucentTexture.length) {
            boolean[] expanded = new boolean[maxIndex + 1];
            System.arraycopy(this.translucentTexture, 0, expanded, 0, this.translucentTexture.length);
            this.translucentTexture = expanded;
        }
    }
}
