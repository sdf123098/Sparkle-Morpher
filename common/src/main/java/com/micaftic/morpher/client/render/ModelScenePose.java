package com.micaftic.morpher.client.render;

import com.micaftic.morpher.client.animation.particle.AnimationParticleQueue;
import dev.spm.core.api.BonePose;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-entity evaluated pose & locator view for a {@link BuiltModel}, replacing legacy
 * {@code AnimatedGeoModel}.
 */
public final class ModelScenePose {
    public static final int MATRIX_STRIDE = 12;
    public static final int ABS_PIVOT_STRIDE = 4;

    private static final int ALL_HEAD_ID = BoneIds.id("AllHead");
    private static final int VIEW_LOCATOR_ID = BoneIds.id("ViewLocator");

    public final AnimationParticleQueue animationParticles = new AnimationParticleQueue();

    private final BuiltModel geoModel;
    private final List<ModelBoneView> orderedBones;
    private final Int2ReferenceMap<ModelBoneView> boneById;
    private final float[] matrixData;
    private final float[] absPivotData;

    private final List<ModelBoneView> headBones;
    private final List<ModelBoneView> leftHandBones;
    private final List<ModelBoneView> rightHandBones;
    private final List<ModelBoneView> leftSwordBones;
    private final List<ModelBoneView> rightSwordBones;
    private final List<ModelBoneView> elytraBones;
    private final List<ModelBoneView> tacPistolBones;
    private final List<ModelBoneView> tacRifleBones;
    private final List<ModelBoneView> leftWaistBones;
    private final List<ModelBoneView> rightWaistBones;
    private final List<ModelBoneView> leftShoulderBones;
    private final List<ModelBoneView> rightShoulderBones;
    private final List<ModelBoneView> bladeBones;
    private final List<ModelBoneView> sheathBones;
    private final List<ModelBoneView> backpackBones;
    private final @Nullable ModelBoneView allHeadBone;
    private final @Nullable ModelBoneView viewLocatorBone;

    private final List<List<ModelBoneView>> leftHandGroupChains = new ReferenceArrayList<>();
    private final List<List<ModelBoneView>> rightHandGroupChains = new ReferenceArrayList<>();
    private final List<List<ModelBoneView>> passengerGroupChains = new ReferenceArrayList<>();
    private final java.util.Set<String> drivenRotationBones = new java.util.HashSet<>();

    private @Nullable Object touhouMaidData;

    public ModelScenePose(BuiltModel model) {
        this.geoModel = model;
        List<ModelBone> bones = model.topLevelBones();
        this.matrixData = new float[MATRIX_STRIDE * bones.size()];
        this.absPivotData = new float[ABS_PIVOT_STRIDE * bones.size()];
        List<ModelBoneView> views = new ArrayList<>(bones.size());
        Int2ReferenceOpenHashMap<ModelBoneView> idMap = new Int2ReferenceOpenHashMap<>(bones.size());
        for (int i = 0; i < bones.size(); i++) {
            ModelBone def = bones.get(i);
            ModelBoneView view = new ModelBoneView(def, i, this.matrixData, i * MATRIX_STRIDE, this.absPivotData, i * ABS_PIVOT_STRIDE);
            views.add(view);
            idMap.put(def.getBoneId(), view);
        }
        for (int i = 0; i < bones.size(); i++) {
            int p = bones.get(i).parentIdx;
            if (p >= 0 && p < views.size() && p != i) {
                views.get(i).setParent(views.get(p));
            }
        }
        this.orderedBones = List.copyOf(views);
        this.boneById = Int2ReferenceMaps.unmodifiable(idMap);
        this.headBones = lookupBones(model.headIds);
        this.leftHandBones = lookupBones(model.leftHandIds);
        this.rightHandBones = lookupBones(model.rightHandIds);
        this.leftSwordBones = lookupBones(model.leftSwordIds);
        this.rightSwordBones = lookupBones(model.rightSwordIds);
        this.elytraBones = lookupBones(model.elytraIds);
        this.tacPistolBones = lookupBones(model.tacPistolIds);
        this.tacRifleBones = lookupBones(model.tacRifleIds);
        this.leftWaistBones = lookupBones(model.leftWaistIds);
        this.rightWaistBones = lookupBones(model.rightWaistIds);
        this.leftShoulderBones = lookupBones(model.leftShoulderIds);
        this.rightShoulderBones = lookupBones(model.rightShoulderIds);
        this.bladeBones = lookupBones(model.bladeIds);
        this.sheathBones = lookupBones(model.sheathIds);
        this.backpackBones = lookupBones(model.backpackIds);
        this.allHeadBone = this.boneById.get(ALL_HEAD_ID);
        this.viewLocatorBone = this.boneById.get(VIEW_LOCATOR_ID);
        for (IntList group : model.extraLeftHandGroups) {
            this.leftHandGroupChains.add(lookupBones(group));
        }
        for (IntList group : model.extraRightHandGroups) {
            this.rightHandGroupChains.add(lookupBones(group));
        }
        for (IntList group : model.passengerGroups) {
            this.passengerGroupChains.add(lookupBones(group));
        }
    }

    private List<ModelBoneView> lookupBones(IntList ids) {
        if (ids.isEmpty()) {
            return ReferenceLists.emptyList();
        }
        ReferenceArrayList<ModelBoneView> list = new ReferenceArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            ModelBoneView bone = this.boneById.get(ids.getInt(i));
            if (bone != null) {
                list.add(bone);
            }
        }
        return ReferenceLists.unmodifiable(list);
    }

    /** Writes a {@code spm-core} {@link BonePose} into the flat matrix buffer (radians + YSM pixel units). */
    public void writeBonePose(BonePose pose) {
        this.drivenRotationBones.clear();
        if (pose == null) {
            for (ModelBoneView view : this.orderedBones) {
                view.resetToBind();
            }
            return;
        }
        int count = Math.min(this.orderedBones.size(), pose.boneCount());
        float[] rot = pose.rotations();
        float[] pos = pose.translations();
        float[] scale = pose.scales();
        boolean[] ownHidden = pose.ownHidden();
        boolean[] childrenHidden = pose.childrenHidden();
        for (int i = 0; i < count; i++) {
            int v = i * 3;
            int o = i * MATRIX_STRIDE;
            this.matrixData[o] = rot[v];
            this.matrixData[o + 1] = rot[v + 1];
            this.matrixData[o + 2] = rot[v + 2];
            // Core uses meters with +X right; host matrixData stores YSM pixels with -X.
            this.matrixData[o + 3] = -pos[v] * 16.0f;
            this.matrixData[o + 4] = pos[v + 1] * 16.0f;
            this.matrixData[o + 5] = pos[v + 2] * 16.0f;
            this.matrixData[o + 6] = scale[v];
            this.matrixData[o + 7] = scale[v + 1];
            this.matrixData[o + 8] = scale[v + 2];
            this.matrixData[o + 9] = ownHidden[i] ? 1.0f : 0.0f;
            this.matrixData[o + 10] = childrenHidden[i] ? 1.0f : 0.0f;
        }
    }

    public BuiltModel getGeoModel() {
        return this.geoModel;
    }

    public List<ModelBoneView> getOrderedBones() {
        return this.orderedBones;
    }

    public @Nullable ModelBoneView getBone(int boneId) {
        return this.boneById.get(boneId);
    }

    public @Nullable ModelBoneView getBone(String name) {
        return this.boneById.get(BoneIds.id(name));
    }

    public float[] getMatrixData() {
        return this.matrixData;
    }

    public float[] getAbsPivotData() {
        return this.absPivotData;
    }

    public @NotNull List<ModelBoneView> headBones() { return this.headBones; }
    public @NotNull List<ModelBoneView> leftHandBones() { return this.leftHandBones; }
    public @NotNull List<ModelBoneView> rightHandBones() { return this.rightHandBones; }
    public @NotNull List<ModelBoneView> leftSwordBones() { return this.leftSwordBones; }
    public @NotNull List<ModelBoneView> rightSwordBones() { return this.rightSwordBones; }
    public @NotNull List<ModelBoneView> elytraBones() { return this.elytraBones; }
    public @NotNull List<ModelBoneView> tacPistolBones() { return this.tacPistolBones; }
    public @NotNull List<ModelBoneView> tacRifleBones() { return this.tacRifleBones; }
    public @NotNull List<ModelBoneView> leftWaistBones() { return this.leftWaistBones; }
    public @NotNull List<ModelBoneView> rightWaistBones() { return this.rightWaistBones; }
    public @NotNull List<ModelBoneView> leftShoulderBones() { return this.leftShoulderBones; }
    public @NotNull List<ModelBoneView> rightShoulderBones() { return this.rightShoulderBones; }
    public @NotNull List<ModelBoneView> bladeBones() { return this.bladeBones; }
    public @NotNull List<ModelBoneView> sheathBones() { return this.sheathBones; }
    public @NotNull List<ModelBoneView> backpackBones() { return this.backpackBones; }
    public @Nullable ModelBoneView allHeadBone() { return this.allHeadBone; }
    public @Nullable ModelBoneView viewLocatorBone() { return this.viewLocatorBone; }
    public @NotNull List<List<ModelBoneView>> leftHandGroupChains() { return this.leftHandGroupChains; }
    public @NotNull List<List<ModelBoneView>> rightHandGroupChains() { return this.rightHandGroupChains; }
    public @NotNull List<List<ModelBoneView>> passengerGroupChains() { return this.passengerGroupChains; }

    public @Nullable Object getTouhouMaidData() { return this.touhouMaidData; }
    public void setTouhouMaidData(@Nullable Object touhouMaidData) { this.touhouMaidData = touhouMaidData; }

    public void markDrivenRotationBones(java.util.Collection<String> boneNames) {
        if (boneNames != null) {
            this.drivenRotationBones.addAll(boneNames);
        }
    }

    public boolean isRotationDriven(String boneName) {
        return boneName != null && this.drivenRotationBones.contains(boneName);
    }
}
