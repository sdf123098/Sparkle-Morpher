package com.micaftic.morpher.client.core;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.client.animation.molang.AnimationProgramPlan;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.ModelDocument;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.AnimationDecodeLimits;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangException;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.ScriptBudget;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Host animation, controller, and extra-resource specifications backed directly by
 * {@link RawYsmModel} and {@code spm-core}'s {@link PoseAnimationLibrary} /
 * {@link PoseControllerDefinition}.
 */
public final class HostAnimationAssets {

    private static final int COMPILE_BUDGET_STEPS = 8_000_000;
    private static final int COMPILE_BUDGET_DEPTH = 128;
    private static final String PARTICLE_PREFIX = "ysm.animation_particle(";

    private HostAnimationAssets() {
    }

    public record BlendEntry(String animationName, @Nullable HostMolangScript weight) {
    }

    public record TransitionEntry(String targetState, HostMolangScript condition) {
    }

    public static final class ClipSpec {
        private final String name;
        private final RawYsmModel.RawAnimation raw;
        private final List<HostMolangScript> programExpressions;
        private @Nullable String sourceKey;
        private boolean fromPrimaryAssembly;
        private boolean stableKipfelAttachments;
        private @Nullable AnimationProgramPlan programPlan;

        public ClipSpec(
                String name,
                RawYsmModel.RawAnimation raw,
                List<HostMolangScript> programExpressions,
                @Nullable String sourceKey,
                boolean fromPrimaryAssembly,
                boolean stableKipfelAttachments) {
            this.name = Objects.requireNonNull(name);
            this.raw = Objects.requireNonNull(raw);
            this.programExpressions = List.copyOf(programExpressions);
            this.sourceKey = sourceKey;
            this.fromPrimaryAssembly = fromPrimaryAssembly;
            this.stableKipfelAttachments = stableKipfelAttachments;
        }

        public String name() {
            return this.name;
        }

        public RawYsmModel.RawAnimation raw() {
            return this.raw;
        }

        public double lengthSeconds() {
            return this.raw.length;
        }

        public CodedLoopMode loopMode() {
            return switch (this.raw.loopMode) {
                case 1 -> CodedLoopMode.LOOP;
                case 3 -> CodedLoopMode.HOLD_ON_LAST_FRAME;
                default -> CodedLoopMode.PLAY_ONCE;
            };
        }

        public @Nullable String sourceKey() {
            return this.sourceKey;
        }

        public void setSourceKey(@Nullable String sourceKey) {
            this.sourceKey = sourceKey;
        }

        public boolean isFromPrimaryAssembly() {
            return this.fromPrimaryAssembly;
        }

        public void setFromPrimaryAssembly(boolean fromPrimaryAssembly) {
            this.fromPrimaryAssembly = fromPrimaryAssembly;
        }

        public boolean isStableKipfelAttachments() {
            return this.stableKipfelAttachments;
        }

        public void setStableKipfelAttachments(boolean stableKipfelAttachments) {
            this.stableKipfelAttachments = stableKipfelAttachments;
        }

        public @Nullable AnimationProgramPlan programPlan() {
            return this.programPlan;
        }

        public void setProgramPlan(@Nullable AnimationProgramPlan programPlan) {
            this.programPlan = programPlan;
        }

        public List<HostMolangScript> programExpressions() {
            return this.programExpressions;
        }

        public boolean hasKeyframes() {
            if (this.raw.boneAnimations == null) {
                return false;
            }
            for (RawYsmModel.RawBoneAnimation bone : this.raw.boneAnimations) {
                if (!bone.rotation.isEmpty() || !bone.position.isEmpty() || !bone.scale.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasEvents() {
            return !this.raw.soundEffects.isEmpty() || !this.raw.timelineEvents.isEmpty();
        }

        public boolean hasTimelineEvents() {
            return !this.raw.timelineEvents.isEmpty();
        }

        public boolean isEmpty() {
            return this.raw.boneAnimations.isEmpty() && !hasEvents();
        }

        public boolean drivesBone(String boneName) {
            for (RawYsmModel.RawBoneAnimation bone : this.raw.boneAnimations) {
                if (bone.boneName.equals(boneName)
                        && (!bone.rotation.isEmpty() || !bone.position.isEmpty() || !bone.scale.isEmpty())) {
                    return true;
                }
            }
            return false;
        }

        public boolean drivesBoneRotation(String boneName) {
            for (RawYsmModel.RawBoneAnimation bone : this.raw.boneAnimations) {
                if (bone.boneName.equals(boneName) && !bone.rotation.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        public ClipSpec withBoneRemap(UnaryOperator<String> boneMapper) {
            boolean changed = false;
            List<RawYsmModel.RawBoneAnimation> mapped = new ArrayList<>(this.raw.boneAnimations.size());
            for (RawYsmModel.RawBoneAnimation bone : this.raw.boneAnimations) {
                String target = boneMapper.apply(bone.boneName);
                if (target != null && !target.equals(bone.boneName)) {
                    changed = true;
                    mapped.add(cloneBoneAnimation(bone, target));
                } else {
                    mapped.add(bone);
                }
            }
            if (!changed) {
                return this;
            }
            RawYsmModel.RawAnimation copy = shallowCopyHeader(this.raw, this.name);
            copy.boneAnimations = mapped;
            copy.soundEffects = this.raw.soundEffects;
            copy.timelineEvents = this.raw.timelineEvents;
            return new ClipSpec(this.name, copy, this.programExpressions, this.sourceKey,
                    this.fromPrimaryAssembly, this.stableKipfelAttachments);
        }

        public @Nullable ClipSpec deriveForBones(String newName, UnaryOperator<@Nullable String> targetBoneMapper) {
            if (isEmpty()) {
                return null;
            }
            LinkedHashMap<String, RawYsmModel.RawBoneAnimation> derivedBones = new LinkedHashMap<>();
            for (RawYsmModel.RawBoneAnimation bone : this.raw.boneAnimations) {
                String target = targetBoneMapper.apply(bone.boneName);
                if (target == null) {
                    continue;
                }
                RawYsmModel.RawBoneAnimation mapped = target.equals(bone.boneName)
                        ? bone
                        : cloneBoneAnimation(bone, target);
                if (target.equals(bone.boneName)) {
                    derivedBones.put(target, mapped);
                } else {
                    derivedBones.putIfAbsent(target, mapped);
                }
            }
            if (derivedBones.isEmpty()) {
                return null;
            }
            RawYsmModel.RawAnimation copy = shallowCopyHeader(this.raw, newName);
            copy.boneAnimations = new ArrayList<>(derivedBones.values());
            return new ClipSpec(newName, copy, List.of(), this.sourceKey, this.fromPrimaryAssembly, false);
        }

        public void clearTimelineEvents() {
            this.raw.timelineEvents.clear();
        }
    }

    public static final class AnimationClipSet {
        private final LinkedHashMap<String, ClipSpec> clips;

        public AnimationClipSet(Map<String, ClipSpec> clips) {
            this.clips = new LinkedHashMap<>(clips);
        }

        public Map<String, ClipSpec> getAnimations() {
            return Collections.unmodifiableMap(this.clips);
        }

        public @Nullable ClipSpec get(String name) {
            return this.clips.get(name);
        }

        public boolean isEmpty() {
            return this.clips.isEmpty();
        }
    }

    public static final class ControllerStateSpec {
        private final String name;
        private final RawYsmModel.RawControllerState raw;
        private final List<BlendEntry> animations;
        private final List<TransitionEntry> transitions;
        private final List<HostMolangScript> onEntry;
        private final List<HostMolangScript> onExit;

        public ControllerStateSpec(
                String name,
                RawYsmModel.RawControllerState raw,
                List<BlendEntry> animations,
                List<TransitionEntry> transitions,
                List<HostMolangScript> onEntry,
                List<HostMolangScript> onExit) {
            this.name = Objects.requireNonNull(name);
            this.raw = Objects.requireNonNull(raw);
            this.animations = List.copyOf(animations);
            this.transitions = List.copyOf(transitions);
            this.onEntry = List.copyOf(onEntry);
            this.onExit = List.copyOf(onExit);
        }

        public String name() {
            return this.name;
        }

        public RawYsmModel.RawControllerState raw() {
            return this.raw;
        }

        public List<BlendEntry> animations() {
            return this.animations;
        }

        public List<TransitionEntry> transitions() {
            return this.transitions;
        }

        public List<HostMolangScript> onEntry() {
            return this.onEntry;
        }

        public List<HostMolangScript> onExit() {
            return this.onExit;
        }

        public List<String> soundEffects() {
            return Collections.unmodifiableList(this.raw.soundEffects);
        }

        public float blendTransitionValue() {
            return this.raw.blendTransitionValue;
        }

        public Map<Float, Float> blendTransitions() {
            return Collections.unmodifiableMap(this.raw.blendTransitions);
        }

        public boolean blendViaShortestPath() {
            return this.raw.blendViaShortestPath;
        }

        public double resolveBlendSeconds(double transitionTimeSeconds) {
            if (!this.raw.blendTransitions.isEmpty()) {
                double resolved = 0.0;
                for (Map.Entry<Float, Float> entry : this.raw.blendTransitions.entrySet()) {
                    if (entry.getKey() <= transitionTimeSeconds) {
                        resolved = entry.getValue();
                    }
                }
                return Math.max(0.0, resolved);
            }
            return Math.max(0.0, this.raw.blendTransitionValue);
        }
    }

    public static final class ControllerSpec {
        private final String name;
        private final String initialState;
        private final Map<String, ControllerStateSpec> states;
        private final RawYsmModel.RawAnimationController raw;

        public ControllerSpec(
                String name,
                String initialState,
                Map<String, ControllerStateSpec> states,
                RawYsmModel.RawAnimationController raw) {
            this.name = Objects.requireNonNull(name);
            this.initialState = (initialState == null || initialState.isEmpty()) ? "default" : initialState;
            this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
            this.raw = Objects.requireNonNull(raw);
        }

        public String name() {
            return this.name;
        }

        public String initialState() {
            return this.initialState;
        }

        public Map<String, ControllerStateSpec> states() {
            return this.states;
        }

        public RawYsmModel.RawAnimationController raw() {
            return this.raw;
        }

        public @Nullable PoseControllerDefinition compile(PoseAnimationLibrary library, boolean mergeMultilineExpr) {
            try {
                ModelDocument.JsonObject json = controllerToCoreJson(this.raw, mergeMultilineExpr, false);
                ScriptBudget budget = new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken());
                return PoseControllerDefinition.decode(json, this.name, library, budget);
            } catch (RuntimeException ex) {
                YesSteveModel.LOGGER.warn("[SM-ANIM] Failed to compile controller '{}': {}", this.name, ex.getMessage());
                return null;
            }
        }
    }

    public static final class AnimationControllerSet {
        private final LinkedHashMap<String, ControllerSpec> controllers;

        public AnimationControllerSet(Map<String, ControllerSpec> controllers) {
            this.controllers = new LinkedHashMap<>(controllers);
        }

        public Map<String, ControllerSpec> getAnimationControllers() {
            return Collections.unmodifiableMap(this.controllers);
        }

        public @Nullable ControllerSpec get(String name) {
            return this.controllers.get(name);
        }

        public boolean isEmpty() {
            return this.controllers.isEmpty();
        }
    }

    public static final class ExtraResourceBundleSpec {
        private final Map<String, AudioTrackData> audioTracks;
        private final Map<String, HostMolangScript> functions;
        private final Map<String, Map<String, String>> translations;

        public ExtraResourceBundleSpec(
                Map<String, AudioTrackData> audioTracks,
                Map<String, HostMolangScript> functions,
                Map<String, Map<String, String>> translations) {
            this.audioTracks = audioTracks;
            this.functions = functions;
            this.translations = translations;
        }

        public Map<String, AudioTrackData> getAudioTracks() {
            return this.audioTracks;
        }

        public Map<String, HostMolangScript> getFunctions() {
            return this.functions;
        }

        public Map<String, Map<String, String>> getTranslations() {
            return this.translations;
        }
    }

    public record ProjectileBundleSpec(
            String[] textureNames,
            BuiltModel model,
            AnimationClipSet animations,
            AnimationControllerSet animationController,
            @Nullable OuterFileTexture texture) {
    }

    public record VehicleBundleSpec(
            String[] textureNames,
            BuiltModel model,
            AnimationClipSet animations,
            AnimationControllerSet animationController,
            @Nullable OuterFileTexture texture) {
    }

    /** Builds a {@link Skeleton} whose indices match {@link BuiltModel#topLevelBones()} 1:1. */
    public static Skeleton buildSkeleton(@Nullable BuiltModel model) {
        if (model == null || model.bones.isEmpty()) {
            return new Skeleton(List.of(), new int[0], new float[0]);
        }
        int count = model.bones.size();
        List<String> names = new ArrayList<>(count);
        Set<String> used = new HashSet<>(count * 2);
        int[] parents = new int[count];
        float[] pivots = new float[count * 3];
        for (int i = 0; i < count; i++) {
            ModelBone bone = model.bones.get(i);
            String base = (bone.getName() == null || bone.getName().isBlank()) ? "bone#" + i : bone.getName();
            String unique = used.add(base) ? base : base + "#" + i;
            if (!unique.equals(base)) {
                used.add(unique);
            }
            names.add(unique);
            int p = bone.parentIdx;
            parents[i] = (p >= 0 && p < count && p != i) ? p : -1;
            pivots[i * 3] = bone.getPivotX() * 0.0625f;
            pivots[i * 3 + 1] = bone.getPivotY() * 0.0625f;
            pivots[i * 3 + 2] = bone.getPivotZ() * 0.0625f;
        }
        return new Skeleton(names, parents, pivots);
    }

    /** Builds the Core bind pose matching {@link ModelBone} initial rotations, visibility, and glow. */
    public static BonePose buildBindPose(@Nullable BuiltModel model) {
        if (model == null || model.bones.isEmpty()) {
            return BonePose.identity(0);
        }
        int count = model.bones.size();
        float[] rotations = new float[count * 3];
        float[] translations = new float[count * 3];
        float[] scales = new float[count * 3];
        boolean[] ownHidden = new boolean[count];
        boolean[] childrenHidden = new boolean[count];
        int[] glow = new int[count];
        for (int i = 0; i < count; i++) {
            ModelBone bone = model.bones.get(i);
            int v = i * 3;
            rotations[v] = bone.getRotX();
            rotations[v + 1] = bone.getRotY();
            rotations[v + 2] = bone.getRotZ();
            scales[v] = 1.0f;
            scales[v + 1] = 1.0f;
            scales[v + 2] = 1.0f;
            ownHidden[i] = bone.isHidden() || bone.cubesAreHidden();
            childrenHidden[i] = bone.isHidden() || bone.childBonesAreHiddenToo();
            glow[i] = bone.glow() ? 15 : 255;
        }
        return new BonePose(rotations, translations, scales, ownHidden, childrenHidden, glow);
    }

    /**
     * Compiles a {@link PoseAnimationLibrary} via {@code spm-core}.
     * Falls back per-clip if any legacy clip has an invalid Molang snippet so valid clips still run.
     */
    public static PoseAnimationLibrary compileLibrary(
            String identity,
            Collection<ClipSpec> clips,
            Skeleton skeleton,
            BonePose bindPose) {
        if (clips.isEmpty()) {
            return PoseAnimationLibrary.fromModel(
                    emptyModelDocument(identity, skeleton, bindPose),
                    new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken()),
                    AnimationDecodeLimits.localYsm());
        }
        try {
            ModelDocument doc = buildModelDocument(identity, clips, skeleton, bindPose, false);
            return PoseAnimationLibrary.fromModel(
                    doc,
                    new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken()),
                    AnimationDecodeLimits.localYsm());
        } catch (RuntimeException bulkFailure) {
            List<ClipSpec> safeClips = new ArrayList<>(clips.size());
            for (ClipSpec clip : clips) {
                try {
                    ModelDocument single = buildModelDocument(identity, List.of(clip), skeleton, bindPose, false);
                    PoseAnimationLibrary.fromModel(
                            single,
                            new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken()),
                            AnimationDecodeLimits.localYsm());
                    safeClips.add(clip);
                } catch (RuntimeException clipFailure) {
                    YesSteveModel.LOGGER.warn(
                            "[SM-ANIM] Sanitizing animation '{}' in '{}' after Core decode error: {}",
                            clip.name(), identity, clipFailure.getMessage());
                    safeClips.add(clip);
                }
            }
            try {
                ModelDocument sanitizedDoc = buildModelDocument(identity, safeClips, skeleton, bindPose, true);
                return PoseAnimationLibrary.fromModel(
                        sanitizedDoc,
                        new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken()),
                        AnimationDecodeLimits.localYsm());
            } catch (RuntimeException fatal) {
                YesSteveModel.LOGGER.warn("[SM-ANIM] Falling back to empty PoseAnimationLibrary for '{}'", identity, fatal);
                return PoseAnimationLibrary.fromModel(
                        emptyModelDocument(identity, skeleton, bindPose),
                        new ScriptBudget(COMPILE_BUDGET_STEPS, COMPILE_BUDGET_DEPTH, new CancellationToken()),
                        AnimationDecodeLimits.localYsm());
            }
        }
    }

    private static ModelDocument emptyModelDocument(String identity, Skeleton skeleton, BonePose bindPose) {
        String safeId = (identity == null || identity.isBlank()) ? "ysm:runtime" : identity;
        return new ModelDocument(safeId, "ysm-runtime", skeleton, List.of(), bindPose, List.of(), List.of(),
                new ModelDocument.JsonObject(Map.of()));
    }

    private static ModelDocument buildModelDocument(
            String identity,
            Collection<ClipSpec> clips,
            Skeleton skeleton,
            BonePose bindPose,
            boolean sanitizeInvalidExpressions) {
        Map<String, ModelDocument.JsonValue> animEntries = new LinkedHashMap<>(clips.size());
        for (ClipSpec clip : clips) {
            if (animEntries.size() >= 1024) {
                break;
            }
            String clipName = clip.name();
            if (clipName == null || clipName.isBlank()) {
                continue;
            }
            animEntries.put(clipName, rawAnimationToJson(clip.raw(), sanitizeInvalidExpressions));
        }
        Map<String, ModelDocument.JsonValue> root = new LinkedHashMap<>();
        root.put("animations", new ModelDocument.JsonObject(animEntries));
        String safeId = (identity == null || identity.isBlank()) ? "ysm:runtime" : identity;
        return new ModelDocument(safeId, "ysm-runtime", skeleton, List.of(), bindPose, List.of(), List.of(),
                new ModelDocument.JsonObject(root));
    }

    private static ModelDocument.JsonObject rawAnimationToJson(RawYsmModel.RawAnimation ra, boolean sanitizeInvalid) {
        Map<String, ModelDocument.JsonValue> map = new LinkedHashMap<>();
        if (Float.isInfinite(ra.length)) {
            map.put("animation_length", new ModelDocument.JsonString("Infinity"));
        } else if (Float.isFinite(ra.length) && ra.length >= 0.0f) {
            map.put("animation_length", new ModelDocument.JsonNumber(ra.length));
        }
        String loop = switch (ra.loopMode) {
            case 1 -> "loop";
            case 3 -> "hold_on_last_frame";
            default -> "play_once";
        };
        map.put("loop", new ModelDocument.JsonString(loop));

        if (ra.blendWeight instanceof Number n && Double.isFinite(n.doubleValue())) {
            map.put("blend_weight", new ModelDocument.JsonNumber(n.doubleValue()));
        } else if (ra.blendWeight instanceof String s && !s.isBlank()) {
            map.put("blend_weight", safeExprJson(s, sanitizeInvalid));
        }

        if (ra.boneAnimations != null && !ra.boneAnimations.isEmpty()) {
            Map<String, ModelDocument.JsonValue> bonesMap = new LinkedHashMap<>();
            for (RawYsmModel.RawBoneAnimation ba : ra.boneAnimations) {
                if (ba.boneName == null || ba.boneName.isBlank()) {
                    continue;
                }
                Map<String, ModelDocument.JsonValue> channels = new LinkedHashMap<>();
                if (!ba.rotation.isEmpty()) {
                    channels.put("rotation", keyframesToJson(ba.rotation, sanitizeInvalid));
                }
                if (!ba.position.isEmpty()) {
                    channels.put("position", keyframesToJson(ba.position, sanitizeInvalid));
                }
                if (!ba.scale.isEmpty()) {
                    channels.put("scale", keyframesToJson(ba.scale, sanitizeInvalid));
                }
                if (!channels.isEmpty()) {
                    bonesMap.put(ba.boneName, new ModelDocument.JsonObject(channels));
                }
            }
            if (!bonesMap.isEmpty()) {
                map.put("bones", new ModelDocument.JsonObject(bonesMap));
            }
        }

        if (ra.soundEffects != null && !ra.soundEffects.isEmpty()) {
            Map<String, ModelDocument.JsonValue> sfxMap = new LinkedHashMap<>();
            for (RawYsmModel.RawSoundEffect sfx : ra.soundEffects) {
                if (sfx.effectName == null || sfx.effectName.isBlank()) {
                    continue;
                }
                String key = formatTimeKey(sfx.timestamp);
                sfxMap.putIfAbsent(key, new ModelDocument.JsonObject(
                        Map.of("effect", new ModelDocument.JsonString(sfx.effectName))));
            }
            if (!sfxMap.isEmpty()) {
                map.put("sound_effects", new ModelDocument.JsonObject(sfxMap));
            }
        }

        if (ra.timelineEvents != null && !ra.timelineEvents.isEmpty()) {
            Map<String, List<ModelDocument.JsonValue>> timelineByTime = new LinkedHashMap<>();
            Map<String, List<ModelDocument.JsonValue>> particlesByTime = new LinkedHashMap<>();
            for (RawYsmModel.RawTimelineEvent tl : ra.timelineEvents) {
                String timeKey = formatTimeKey(tl.timestamp);
                for (String event : tl.events) {
                    if (event == null || event.isBlank()) {
                        continue;
                    }
                    ModelDocument.JsonObject particleObj = tryDecodeLoweredParticle(event, sanitizeInvalid);
                    if (particleObj != null) {
                        particlesByTime.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(particleObj);
                    } else {
                        timelineByTime.computeIfAbsent(timeKey, k -> new ArrayList<>())
                                .add(safeExprJson(event, sanitizeInvalid));
                    }
                }
            }
            if (!particlesByTime.isEmpty()) {
                Map<String, ModelDocument.JsonValue> pMap = new LinkedHashMap<>();
                for (var entry : particlesByTime.entrySet()) {
                    pMap.put(entry.getKey(), entry.getValue().size() == 1
                            ? entry.getValue().getFirst()
                            : new ModelDocument.JsonArray(entry.getValue()));
                }
                map.put("particle_effects", new ModelDocument.JsonObject(pMap));
            }
            if (!timelineByTime.isEmpty()) {
                Map<String, ModelDocument.JsonValue> tMap = new LinkedHashMap<>();
                for (var entry : timelineByTime.entrySet()) {
                    tMap.put(entry.getKey(), new ModelDocument.JsonArray(entry.getValue()));
                }
                map.put("timeline", new ModelDocument.JsonObject(tMap));
            }
        }
        return new ModelDocument.JsonObject(map);
    }

    private static ModelDocument.JsonValue keyframesToJson(List<RawYsmModel.RawKeyframe> frames, boolean sanitizeInvalid) {
        Map<String, ModelDocument.JsonValue> map = new LinkedHashMap<>(frames.size());
        for (RawYsmModel.RawKeyframe rk : frames) {
            String timeKey = formatTimeKey(rk.timestamp);
            // Deduplicate colliding timestamps consistently with TreeMap ordering.
            if (map.containsKey(timeKey)) {
                continue;
            }
            String lerpMode = switch (rk.interpolationMode) {
                case RawYsmModel.RawKeyframe.INTERPOLATION_STEP -> "step";
                case RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM -> "catmullrom";
                default -> "linear";
            };
            Map<String, ModelDocument.JsonValue> entry = new LinkedHashMap<>(4);
            if (rk.hasPreData) {
                entry.put("pre", vectorToJson(rk.preData, sanitizeInvalid));
                entry.put("post", vectorToJson(rk.postData, sanitizeInvalid));
            } else {
                entry.put("vector", vectorToJson(rk.postData, sanitizeInvalid));
            }
            if (!"linear".equals(lerpMode)) {
                entry.put("lerp_mode", new ModelDocument.JsonString(lerpMode));
            }
            map.put(timeKey, new ModelDocument.JsonObject(entry));
        }
        return new ModelDocument.JsonObject(map);
    }

    private static ModelDocument.JsonValue vectorToJson(Object[] data, boolean sanitizeInvalid) {
        if (data == null || data.length < 3) {
            return new ModelDocument.JsonVector3(0.0f, 0.0f, 0.0f);
        }
        if (data[0] instanceof Number x && data[1] instanceof Number y && data[2] instanceof Number z
                && Float.isFinite(x.floatValue()) && Float.isFinite(y.floatValue()) && Float.isFinite(z.floatValue())) {
            return new ModelDocument.JsonVector3(x.floatValue(), y.floatValue(), z.floatValue());
        }
        List<ModelDocument.JsonValue> list = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            Object v = data[i];
            if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
                list.add(new ModelDocument.JsonNumber(n.doubleValue()));
            } else if (v instanceof String s && !s.isBlank()) {
                list.add(safeExprJson(s, sanitizeInvalid));
            } else {
                list.add(new ModelDocument.JsonNumber(0.0));
            }
        }
        return new ModelDocument.JsonArray(list);
    }

    private static ModelDocument.JsonValue safeExprJson(String source, boolean sanitizeInvalid) {
        if (!sanitizeInvalid) {
            return new ModelDocument.JsonString(source);
        }
        HostMolangScript compiled = HostMolangScript.compile(source);
        return compiled.parsedSuccessfully()
                ? new ModelDocument.JsonString(source)
                : new ModelDocument.JsonNumber(0.0);
    }

    private static @Nullable ModelDocument.JsonObject tryDecodeLoweredParticle(String event, boolean sanitizeInvalid) {
        String trimmed = event.trim();
        if (!trimmed.startsWith(PARTICLE_PREFIX)) {
            return null;
        }
        try {
            int firstQuote = trimmed.indexOf('\'', PARTICLE_PREFIX.length());
            int secondQuote = trimmed.indexOf('\'', firstQuote + 1);
            int thirdQuote = trimmed.indexOf('\'', secondQuote + 1);
            int fourthQuote = trimmed.indexOf('\'', thirdQuote + 1);
            int braceOpen = trimmed.indexOf('{', fourthQuote + 1);
            int braceClose = trimmed.lastIndexOf('}');
            if (firstQuote < 0 || secondQuote < 0 || thirdQuote < 0 || fourthQuote < 0
                    || braceOpen < 0 || braceClose < braceOpen) {
                return null;
            }
            String effect = new String(Base64.getDecoder().decode(trimmed.substring(firstQuote + 1, secondQuote)), StandardCharsets.UTF_8);
            if (effect.isBlank()) {
                return null;
            }
            String locator = new String(Base64.getDecoder().decode(trimmed.substring(thirdQuote + 1, fourthQuote)), StandardCharsets.UTF_8);
            String preScript = trimmed.substring(braceOpen + 1, braceClose).trim();
            Map<String, ModelDocument.JsonValue> obj = new LinkedHashMap<>();
            obj.put("effect", new ModelDocument.JsonString(effect));
            if (!locator.isEmpty()) {
                obj.put("locator", new ModelDocument.JsonString(locator));
            }
            if (!preScript.isEmpty()) {
                obj.put("pre_effect_script", safeExprJson(preScript, sanitizeInvalid));
            }
            return new ModelDocument.JsonObject(obj);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ModelDocument.JsonObject controllerToCoreJson(
            RawYsmModel.RawAnimationController rac,
            boolean mergeMultilineExpr,
            boolean sanitizeInvalid) {
        Map<String, ModelDocument.JsonValue> statesJson = new LinkedHashMap<>();
        for (RawYsmModel.RawControllerState rs : rac.states) {
            Map<String, ModelDocument.JsonValue> stateObj = new LinkedHashMap<>();
            if (!rs.animations.isEmpty()) {
                List<ModelDocument.JsonValue> anims = new ArrayList<>(rs.animations.size());
                for (Map.Entry<String, String> entry : rs.animations) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) {
                        anims.add(new ModelDocument.JsonString(entry.getKey()));
                    } else {
                        anims.add(new ModelDocument.JsonObject(Map.of(
                                entry.getKey(), safeExprJson(entry.getValue(), sanitizeInvalid))));
                    }
                }
                stateObj.put("animations", new ModelDocument.JsonArray(anims));
            }
            if (!rs.transitions.isEmpty()) {
                List<ModelDocument.JsonValue> trans = new ArrayList<>(rs.transitions.size());
                for (Map.Entry<String, String> entry : rs.transitions) {
                    trans.add(new ModelDocument.JsonObject(Map.of(
                            entry.getKey(), safeExprJson(entry.getValue(), sanitizeInvalid))));
                }
                stateObj.put("transitions", new ModelDocument.JsonArray(trans));
            }
            if (!rs.onEntry.isEmpty()) {
                stateObj.put("on_entry", scriptListToJson(rs.onEntry, mergeMultilineExpr, sanitizeInvalid));
            }
            if (!rs.onExit.isEmpty()) {
                stateObj.put("on_exit", scriptListToJson(rs.onExit, mergeMultilineExpr, sanitizeInvalid));
            }
            if (!rs.soundEffects.isEmpty()) {
                List<ModelDocument.JsonValue> sounds = new ArrayList<>(rs.soundEffects.size());
                for (String s : rs.soundEffects) {
                    sounds.add(new ModelDocument.JsonString(s));
                }
                stateObj.put("sound_effects", new ModelDocument.JsonArray(sounds));
            }
            stateObj.put("blend_transition", new ModelDocument.JsonNumber(rs.blendTransitionValue));
            stateObj.put("blend_via_shortest_path", new ModelDocument.JsonBoolean(rs.blendViaShortestPath));
            statesJson.put(rs.name, new ModelDocument.JsonObject(stateObj));
        }
        Map<String, ModelDocument.JsonValue> ctrlObj = new LinkedHashMap<>();
        ctrlObj.put("initial_state", new ModelDocument.JsonString(
                (rac.initialState == null || rac.initialState.isEmpty()) ? "default" : rac.initialState));
        ctrlObj.put("states", new ModelDocument.JsonObject(statesJson));
        return new ModelDocument.JsonObject(Map.of(
                "animation_controllers", new ModelDocument.JsonObject(Map.of(rac.animationName, new ModelDocument.JsonObject(ctrlObj)))));
    }

    private static ModelDocument.JsonArray scriptListToJson(
            List<String> lines, boolean mergeMultilineExpr, boolean sanitizeInvalid) {
        List<ModelDocument.JsonValue> out = new ArrayList<>();
        if (mergeMultilineExpr) {
            String merged = String.join("\n", lines);
            out.add(safeExprJson(merged, sanitizeInvalid));
        } else {
            for (String line : lines) {
                out.add(safeExprJson(line, sanitizeInvalid));
            }
        }
        return new ModelDocument.JsonArray(out);
    }

    private static String formatTimeKey(float timestamp) {
        if (!Float.isFinite(timestamp) || timestamp < 0.0f) {
            return "0";
        }
        if (timestamp == 0.0f) {
            return "0";
        }
        return Float.toString(timestamp);
    }

    private static RawYsmModel.RawAnimation shallowCopyHeader(RawYsmModel.RawAnimation src, String name) {
        RawYsmModel.RawAnimation copy = new RawYsmModel.RawAnimation();
        copy.name = name;
        copy.length = src.length;
        copy.loopMode = src.loopMode;
        copy.blendWeight = src.blendWeight;
        copy.unkInt1 = src.unkInt1;
        copy.unkInt2 = src.unkInt2;
        copy.unkInt4 = src.unkInt4;
        return copy;
    }

    private static RawYsmModel.RawBoneAnimation cloneBoneAnimation(RawYsmModel.RawBoneAnimation src, String boneName) {
        RawYsmModel.RawBoneAnimation copy = new RawYsmModel.RawBoneAnimation();
        copy.boneName = boneName;
        copy.rotation = new ArrayList<>(src.rotation);
        copy.position = new ArrayList<>(src.position);
        copy.scale = new ArrayList<>(src.scale);
        return copy;
    }
}
