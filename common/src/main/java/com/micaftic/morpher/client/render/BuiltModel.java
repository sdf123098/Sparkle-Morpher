package com.micaftic.morpher.client.render;

import com.micaftic.morpher.resource.ModelOptimizationStats;
import com.micaftic.morpher.resource.models.GeometryDescription;
import dev.spm.core.api.Skeleton;
import dev.spm.core.api.VertexBatch;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Baked model geometry and hierarchy backed by {@code spm-core}'s {@link Skeleton} and
 * {@link VertexBatch} for CPU SIMD and GPU rendering.
 */
public class BuiltModel {

    @NotNull
    public final List<ModelBone> bones;

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

    @NotNull
    public final IntList leftSwordIds;

    @NotNull
    public final IntList rightSwordIds;

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

    public ModelOptimizationStats optimizationStats;
    public boolean conservativeRenderOnly;
    public boolean conservativeFirstPersonHands;

    public long gpuMeshHandle = 0;

    private volatile @Nullable ComputeCache computeCache;

    public record PartBatch(VertexBatch vertices, float[] uvs) {
    }

    public static final class ComputeCache {
        public final Skeleton skeleton;
        private final @Nullable PartBatch[] partBatches = new PartBatch[4];

        ComputeCache(Skeleton skeleton) {
            this.skeleton = skeleton;
        }

        public @Nullable PartBatch batchForMask(List<BakedBone> bones, int[] order, int renderPartMask) {
            int slot = renderPartMask >= 0 && renderPartMask < partBatches.length ? renderPartMask : 0;
            PartBatch existing = partBatches[slot];
            if (existing != null) {
                return existing;
            }
            synchronized (this) {
                existing = partBatches[slot];
                if (existing != null) {
                    return existing;
                }
                PartBatch built = buildPartBatch(bones, order, renderPartMask);
                partBatches[slot] = built;
                return built;
            }
        }
    }

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
        public List<BakedQuad> quads = new ObjectArrayList<>();
    }

    public static class BakedQuad {
        public final float[] packedPositions = new float[12];
        public final float[] packedUvs = new float[8];
        public float normalX, normalY, normalZ;

        public void setNormal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
        }

        public void setVertex(int index, float x, float y, float z, float u, float v) {
            int posOffset = index * 3;
            packedPositions[posOffset] = x;
            packedPositions[posOffset + 1] = y;
            packedPositions[posOffset + 2] = z;
            int uvOffset = index * 2;
            packedUvs[uvOffset] = u;
            packedUvs[uvOffset + 1] = v;
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

        public float normalX() {
            return normalX;
        }

        public float normalY() {
            return normalY;
        }

        public float normalZ() {
            return normalZ;
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

    public void updateConservativeRenderFlags() {
        int bones = bakedBones == null ? 0 : bakedBones.size();
        int cubes = 0;
        int quads = 0;
        int armQuads = 0;
        int rotatedBones = 0;
        int negativeSizedFaces = optimizationStats == null ? 0 : optimizationStats.negativeSizedFaces;
        if (bakedBones != null) {
            for (BakedBone bone : bakedBones) {
                if (bone.rotX != 0.0f || bone.rotY != 0.0f || bone.rotZ != 0.0f) {
                    rotatedBones++;
                }
                for (BakedCube cube : bone.cubes) {
                    cubes++;
                    int cubeQuads = cube.quads.size();
                    quads += cubeQuads;
                    if (bone.partMask == 1 || bone.partMask == 2) {
                        armQuads += cubeQuads;
                    }
                }
            }
        }
        boolean hasRiskyFaces = negativeSizedFaces >= 4 || negativeSizedFaces * 3 >= Math.max(1, quads);
        boolean complexModel = bones >= 96 || cubes >= 512 || quads >= 2048 || rotatedBones >= 12;
        this.conservativeRenderOnly = hasRiskyFaces && complexModel;
        this.conservativeFirstPersonHands = hasRiskyFaces && (this.hasCustomLeftHand || this.hasCustomRightHand || this.hasCustomLimbs || armQuads > 0);
    }

    public boolean ensureConservativeRenderOnly() {
        if (!conservativeRenderOnly && optimizationStats != null && optimizationStats.negativeSizedFaces > 0) {
            updateConservativeRenderFlags();
            if (conservativeRenderOnly) {
                disableCullableCubes();
            }
        }
        return conservativeRenderOnly;
    }

    public void disableCullableCubes() {
        if (bakedBones == null) {
            return;
        }
        for (BakedBone bone : bakedBones) {
            for (BakedCube cube : bone.cubes) {
                cube.cullable = false;
            }
        }
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

    public synchronized @Nullable ComputeCache ensureComputeCache() {
        if (computeCache != null) {
            return computeCache;
        }
        if (bakedBones == null || bakedBones.isEmpty()) {
            return null;
        }
        int count = bakedBones.size();
        List<String> names = new ArrayList<>(count);
        int[] parents = new int[count];
        float[] pivots = new float[count * 3];
        for (int i = 0; i < count; i++) {
            BakedBone bone = bakedBones.get(i);
            String base = (bone.name == null || bone.name.isBlank()) ? "bone" : bone.name;
            names.add(base + "#" + i);
            int p = bone.parentIdx;
            parents[i] = (p >= 0 && p < count && p != i) ? p : -1;
            pivots[i * 3] = bone.pivotX * 0.0625f;
            pivots[i * 3 + 1] = bone.pivotY * 0.0625f;
            pivots[i * 3 + 2] = bone.pivotZ * 0.0625f;
        }
        Skeleton skeleton = new Skeleton(names, parents, pivots);
        computeCache = new ComputeCache(skeleton);
        return computeCache;
    }

    private static PartBatch buildPartBatch(List<BakedBone> bones, int[] order, int renderPartMask) {
        int quadCount = 0;
        for (int boneIdx : order) {
            if (boneIdx < 0 || boneIdx >= bones.size()) continue;
            BakedBone bone = bones.get(boneIdx);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) continue;
            for (BakedCube cube : bone.cubes) {
                quadCount += cube.quads.size();
            }
        }
        int vertexCount = quadCount * 4;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        int[] boneIndices = new int[vertexCount];
        int[] glow = new int[vertexCount];
        int[] vertexIds = new int[vertexCount];
        int v = 0;
        for (int boneIdx : order) {
            if (boneIdx < 0 || boneIdx >= bones.size()) continue;
            BakedBone bone = bones.get(boneIdx);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) continue;
            int boneGlow = bone.glow ? 15 : 255;
            for (BakedCube cube : bone.cubes) {
                for (BakedQuad quad : cube.quads) {
                    for (int k = 0; k < 4; k++) {
                        int p = v * 3;
                        positions[p] = quad.x(k);
                        positions[p + 1] = quad.y(k);
                        positions[p + 2] = quad.z(k);
                        normals[p] = quad.normalX;
                        normals[p + 1] = quad.normalY;
                        normals[p + 2] = quad.normalZ;
                        uvs[v * 2] = quad.u(k);
                        uvs[v * 2 + 1] = quad.v(k);
                        boneIndices[v] = boneIdx;
                        glow[v] = boneGlow;
                        vertexIds[v] = v;
                        v++;
                    }
                }
            }
        }
        return new PartBatch(new VertexBatch(positions, normals, boneIndices, glow, vertexIds), uvs);
    }

    public synchronized void freeNativeCache() {
        computeCache = null;
        freeGpuCache();
        RenderBackends.blaze3d().release(this);
    }

    public boolean freeGpuCache() {
        if (gpuMeshHandle != 0) {
            RenderBackends.gpu().release(this);
            return true;
        }
        return false;
    }

    public BuiltModel(ModelBone[] geoBones, String[][] strArr, boolean[] zArr, @NotNull GeometryDescription properties, boolean[] zArr2) {
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
        this.leftSwordIds = strArr.length > 35 ? resolveBoneIds(strArr[35]) : IntLists.emptyList();
        this.rightSwordIds = strArr.length > 36 ? resolveBoneIds(strArr[36]) : IntLists.emptyList();
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
        this.hasCustomLeftHand = zArr[0];
        this.hasCustomRightHand = zArr[1];
        this.hasCustomLimbs = zArr[2];
        this.translucentTexture = zArr2;
        this.properties = properties;
        this.boneTransformData = new ModelScenePose(this).getMatrixData();
    }

    private static IntList resolveBoneIds(String[] strArr) {
        IntArrayList intArrayList = new IntArrayList(strArr.length);
        for (String str : strArr) {
            intArrayList.add(BoneIds.id(str));
        }
        return IntLists.unmodifiable(intArrayList);
    }

    @NotNull
    public List<ModelBone> topLevelBones() {
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
