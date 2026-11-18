package com.micaftic.morpher.client.core;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioPlayerManager;
import com.micaftic.morpher.client.animation.molang.AnimationProgramAnalyzer;
import com.micaftic.morpher.client.animation.molang.AnimationProgramPlan;
import com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime;
import com.micaftic.morpher.client.animation.molang.KipfelRuntime;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.render.ModelScenePose;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.Skeleton;
import dev.spm.core.scene.CodedAnimationCollection;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.CodedControllerDefinition;
import dev.spm.core.scene.CodedControllerSnapshot;
import dev.spm.core.scene.CodedPoseFrame;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangException;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.MolangProgram;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PoseAnimationController;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseControllerDefinition;
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Per-entity SPM animation orchestrator, replacing legacy {@code HostAnimationProcessor}.
 * Executes controller slots via {@code spm-core}'s {@link CodedAnimationCollection} and
 * {@link PoseAnimationController}, applies bone styles and procedural overrides, and writes
 * the result into {@link ModelScenePose}.
 */
public class HostAnimationProcessor<TEntity extends Entity> {

    private static final int FRAME_BUDGET_STEPS = 4_000_000;
    private static final int FRAME_BUDGET_DEPTH = 128;
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final AudioPlayerManager GLOBAL_AUDIO = new AudioPlayerManager();

    private final HostAnimatable<TEntity> animatable;
    private final AudioPlayerManager audioPlayerManager = new AudioPlayerManager();
    private final CoreBoneEffects boneEffects = new CoreBoneEffects();
    private final ConcurrentLinkedQueue<PendingExpression> pendingExpressions = new ConcurrentLinkedQueue<>();

    private final Map<String, HostMolangScript> extraFunctions = new LinkedHashMap<>();
    private final Map<String, List<HostMolangScript>> eventScripts = new LinkedHashMap<>();
    private final Map<String, MolangValue> adhocVariables = new LinkedHashMap<>();
    private final Map<AnimationProgramPlan, AnimationProgramRuntime> programs = new IdentityHashMap<>();

    private @Nullable PoseAnimationLibrary library;
    private @Nullable Skeleton skeleton;
    private @Nullable BonePose bindPose;
    private @Nullable CodedAnimationCollection codedCollection;
    private final Map<String, PoseAnimationController> jsonControllers = new LinkedHashMap<>();
    private @Nullable KipfelRuntime kipfelRuntime;

    private @Nullable RoamingStruct roamingStruct;
    private @Nullable com.micaftic.morpher.client.animation.molang.MolangWatchRegistry watchRegistry;
    private long frameCounter;
    private double lastSeekSeconds = -1.0;
    private float lastAudioTickTime;
    private boolean initialized;
    private long programGeneration;
    private @Nullable Object programWorld;
    private boolean programExternalWrites;
    private @Nullable BonePose lastPose;
    private final Map<String, HostPlayState.State> slotStates = new LinkedHashMap<>();

    private record PendingExpression(
            HostMolangScript script,
            boolean isClientPlayer,
            boolean executeBeforeAnimation,
            @Nullable Consumer<String> callback) {
    }

    public HostAnimationProcessor(HostAnimatable<TEntity> animatable) {
        this.animatable = animatable;
    }

    public synchronized void initModel(
            @Nullable ModelScenePose scenePose,
            @Nullable PoseAnimationLibrary library,
            Map<String, HostMolangScript> extraFunctions,
            Map<String, List<HostMolangScript>> eventScripts) {
        reset();
        this.extraFunctions.putAll(extraFunctions);
        this.eventScripts.putAll(eventScripts);
        if (scenePose == null) {
            return;
        }
        this.skeleton = HostAnimationAssets.buildSkeleton(scenePose.getGeoModel());
        this.bindPose = HostAnimationAssets.buildBindPose(scenePose.getGeoModel());
        this.library = library != null
                ? library
                : HostAnimationAssets.compileLibrary("ysm:empty", List.of(), this.skeleton, this.bindPose);

        List<HostControllerSlot<?>> slots = this.animatable.getControllerSlots();
        List<CodedControllerDefinition> codedDefs = new ArrayList<>(slots.size());
        Map<String, MolangProgram> coreFunctions = toCorePrograms(extraFunctions);
        MolangHooks hooks = buildHooks(eventScripts);

        for (HostControllerSlot<?> slot : slots) {
            codedDefs.add(CodedControllerDefinition.hostDriven(
                    slot.getName(), slot.defaultTransitionTicks(), slot.isAdditive()));
            ControllerSpec jsonSpec = this.animatable.getControllerSpec(slot.getName());
            if (jsonSpec != null) {
                PoseControllerDefinition compiled = jsonSpec.compile(this.library, false);
                if (compiled != null) {
                    this.jsonControllers.put(slot.getName(),
                            new PoseAnimationController(this.library, compiled, this.bindPose, 12,
                                    this.animatable.getEntity().getUUID().getLeastSignificantBits(),
                                    coreFunctions, hooks));
                }
            }
        }
        this.codedCollection = new CodedAnimationCollection(
                this.library,
                this.bindPose,
                codedDefs,
                12,
                this.animatable.getEntity().getUUID().getLeastSignificantBits(),
                coreFunctions,
                hooks);
        this.initialized = true;
    }

    @SuppressWarnings("unchecked")
    public synchronized void tickAnimation(
            HostActionEvent<HostAnimatable<TEntity>> event,
            boolean active,
            boolean shouldRenderOverlay) {
        if (!this.initialized || this.codedCollection == null || this.bindPose == null || this.skeleton == null) {
            return;
        }
        ModelScenePose scenePose = this.animatable.getCurrentModel();
        if (scenePose == null) {
            return;
        }

        Object world = this.animatable.entity.level();
        if (this.programWorld != null && this.programWorld != world) {
            this.programGeneration++;
            this.programs.values().forEach(AnimationProgramRuntime::close);
            this.programs.clear();
        }
        this.programWorld = world;

        float seekTime = event.currentTick;
        if (seekTime - this.lastAudioTickTime >= 1200.0f || this.lastAudioTickTime > seekTime) {
            this.audioPlayerManager.tick();
            GLOBAL_AUDIO.tick();
            this.lastAudioTickTime = seekTime;
        }

        double seekSeconds = Math.max(0.0, seekTime / 20.0);
        if (seekSeconds < this.lastSeekSeconds) {
            this.lastSeekSeconds = seekSeconds;
        } else {
            this.lastSeekSeconds = seekSeconds;
        }
        long nowNanos = (long) (seekSeconds * 1_000_000_000.0);
        long frameId = ++this.frameCounter;

        // 1. Run pre-animation pending GUI / watch scripts
        runPending(event, true);
        if (this.watchRegistry != null) {
            this.watchRegistry.evaluatePreAnimation(buildDirectContext(event, Map.of()));
        }

        // 2. Evaluate controller predicates (and JSON controller state overrides)
        Map<String, CodedControllerCommand> commands = new LinkedHashMap<>();
        Map<String, PoseFrame> activeJsonPoses = new LinkedHashMap<>();
        Map<MolangBindings.Call, MolangValue> sampledCalls = new HashMap<>();

        List<HostControllerSlot<?>> slots = this.animatable.getControllerSlots();
        SceneInputs baseInputs = HostMolangBindings.buildInputs(event, sampledCalls, active, Map.of());

        if (active) {
            for (HostControllerSlot<?> slot : slots) {
                HostControllerSlot<HostAnimatable<TEntity>> typedSlot =
                        (HostControllerSlot<HostAnimatable<TEntity>>) slot;
                PoseAnimationController jsonCtrl = this.jsonControllers.get(slot.getName());
                if (jsonCtrl != null) {
                    try {
                        PoseFrame jsonFrame = evaluateJsonWithRetry(jsonCtrl, frameId, nowNanos, baseInputs, event, sampledCalls);
                        String jsonState = jsonFrame.animation();
                        if (jsonState != null && !jsonState.startsWith("builtin")) {
                            activeJsonPoses.put(slot.getName(), jsonFrame);
                            commands.put(slot.getName(), new CodedControllerCommand(
                                    dev.spm.core.scene.CodedPlayDecision.STOP, "", dev.spm.core.scene.CodedLoopMode.CLIP_DEFAULT,
                                    slot.defaultTransitionTicks(), 1.0, false, false));
                            typedSlot.updateFeedback(jsonState, jsonFrame.animationTimeSeconds(), true, jsonFrame.finished());
                            this.animatable.setAnimationState(slot.getName(), HostPlayState.State.RUNNING);
                            continue;
                        }
                    } catch (RuntimeException ex) {
                        reportFailure(slot.getName(), ex);
                    }
                }
                try {
                    String soundEventKey = slot.getName().replace(".", "_ctrl_");
                    boolean hasSoundExpr = this.eventScripts.containsKey(soundEventKey)
                            && !this.eventScripts.get(soundEventKey).isEmpty();
                    CodedControllerCommand cmd = typedSlot.evaluateCommand(
                            (HostActionEvent<HostAnimatable<TEntity>>) event,
                            hasSoundExpr && checkSoundOverride(soundEventKey, event, sampledCalls));
                    commands.put(slot.getName(), cmd);
                } catch (RuntimeException ex) {
                    reportFailure(slot.getName(), ex);
                }
            }
        }

        // 3. Evaluate CodedAnimationCollection in spm-core
        BonePose pose = this.lastPose != null ? this.lastPose : this.bindPose;
        if (active) {
            CodedPoseFrame frame = evaluateCodedWithRetry(frameId, nowNanos, event, sampledCalls, commands);
            if (frame != null) {
                pose = frame.pose();
                // Layer any active JSON controller poses onto the coded pose
                if (!activeJsonPoses.isEmpty()) {
                    pose = overlayJsonPoses(pose, this.bindPose, activeJsonPoses.values());
                }
                // Apply bone color / transparency / glow effects
                try {
                    pose = this.boneEffects.apply(this.skeleton, pose, frame.effects());
                } catch (RuntimeException ex) {
                    reportFailure("bone_effects", ex);
                }
                // Sync controller snapshots back to slots
                Map<String, CodedControllerSnapshot> snapByName = new HashMap<>(frame.controllers().size() * 2);
                for (CodedControllerSnapshot s : frame.controllers()) {
                    snapByName.put(s.name(), s);
                }
                for (HostControllerSlot<?> slot : slots) {
                    if (activeJsonPoses.containsKey(slot.getName())) {
                        continue;
                    }
                    CodedControllerSnapshot snap = snapByName.get(slot.getName());
                    if (snap != null) {
                        slot.updateFeedback(snap.selectedClip(), snap.animationTimeSeconds(), snap.contributing(), false);
                        this.animatable.setAnimationState(slot.getName(),
                                snap.contributing() ? HostPlayState.State.RUNNING : HostPlayState.State.IDLE);
                    }
                }
                // Dispatch effects (sounds, particles)
                HostMolangBindings.dispatchEffects(this.animatable, event, this.audioPlayerManager, GLOBAL_AUDIO, scenePose, frame.effects());
                for (PoseFrame pf : activeJsonPoses.values()) {
                    HostMolangBindings.dispatchEffects(this.animatable, event, this.audioPlayerManager, GLOBAL_AUDIO, scenePose, pf.effects());
                }
                // Propagate roaming variable writes back into RoamingStruct for server sync
                syncRoamingFromCore();
                this.lastPose = pose;
            }
        }

        // 4. Write pose into ModelScenePose
        scenePose.writeBonePose(pose);
        if (active && this.library != null) {
            for (HostControllerSlot<?> slot : slots) {
                String clip = slot.getActiveClipName();
                if (slot.isPlaying() && !clip.isEmpty() && this.library.hasClip(clip)) {
                    scenePose.markDrivenRotationBones(this.library.clipSummary(clip).rotationBones());
                }
            }
        }

        // 5. Run procedural slot overrides (VehicleRotationController, VanillaHumanoidPoseSampler, etc.)
        for (HostControllerSlot<?> slot : slots) {
            @SuppressWarnings("unchecked")
            HostControllerSlot<HostAnimatable<TEntity>> typedSlot =
                    (HostControllerSlot<HostAnimatable<TEntity>>) slot;
            typedSlot.applyProceduralPose(event, scenePose);
        }

        // 6. Kipfel runtime physics post-pass if active on this model
        var attachment = this.animatable.getAnimation(com.micaftic.morpher.resource.bundle.KipfelCompatibility.HAIR);
        if (active && attachment != null && attachment.isStableKipfelAttachments() && this.library != null) {
            if (this.kipfelRuntime == null) {
                this.kipfelRuntime = new KipfelRuntime(this.animatable, this.library, this.skeleton, this.bindPose);
            }
            this.kipfelRuntime.update(event, scenePose, buildDirectContext(event, sampledCalls));
        }

        // 7. Run post-animation GUI / watch scripts
        runPending(event, false);
        if (this.watchRegistry != null) {
            this.watchRegistry.evaluatePostAnimation(buildDirectContext(event, sampledCalls));
        }
    }

    public synchronized void setWatchRegistry(@Nullable com.micaftic.morpher.client.animation.molang.MolangWatchRegistry watchRegistry) {
        this.watchRegistry = watchRegistry;
    }

    private PoseFrame evaluateJsonWithRetry(
            PoseAnimationController controller,
            long frameId,
            long nowNanos,
            SceneInputs baseInputs,
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls) {
        ScriptBudget budget = new ScriptBudget(FRAME_BUDGET_STEPS, FRAME_BUDGET_DEPTH, new CancellationToken());
        for (int attempt = 0; attempt < 64; attempt++) {
            try {
                SceneInputs frozen = new SceneInputs(
                        baseInputs.queries(),
                        new MolangBindings(baseInputs.bindings().values(), sampledCalls),
                        baseInputs.effectsAllowed(),
                        baseInputs.syncMode());
                return controller.evaluate(frameId, nowNanos, frozen, budget);
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElseThrow(() -> missing);
                if (sampledCalls.containsKey(req)) {
                    throw missing;
                }
                sampledCalls.put(req, HostMolangBindings.resolveCall(event, req));
            }
        }
        throw new IllegalStateException("Exceeded JSON controller call retry limit");
    }

    private @Nullable CodedPoseFrame evaluateCodedWithRetry(
            long frameId,
            long nowNanos,
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            Map<String, CodedControllerCommand> commands) {
        ScriptBudget budget = new ScriptBudget(FRAME_BUDGET_STEPS, FRAME_BUDGET_DEPTH, new CancellationToken());
        for (int attempt = 0; attempt < 128; attempt++) {
            try {
                SceneInputs inputs = HostMolangBindings.buildInputs(event, sampledCalls, true, commands);
                return this.codedCollection.evaluate(frameId, nowNanos, inputs, budget);
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElse(null);
                if (req == null || sampledCalls.containsKey(req)) {
                    reportFailure("coded_collection", missing);
                    return null;
                }
                sampledCalls.put(req, HostMolangBindings.resolveCall(event, req));
            } catch (RuntimeException ex) {
                reportFailure("coded_collection", ex);
                return null;
            }
        }
        return null;
    }

    private boolean checkSoundOverride(
            String eventKey,
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls) {
        List<HostMolangScript> list = this.eventScripts.get(eventKey);
        if (list == null || list.isEmpty()) {
            return false;
        }
        MolangValue result = evaluateScriptWithRetry(list.getFirst(), event, sampledCalls);
        int code = (int) result.number();
        return code == 2 || code == 3 || code == 4;
    }

    private MolangValue evaluateScriptWithRetry(
            HostMolangScript script,
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls) {
        if (script == null || !script.parsedSuccessfully()) {
            return MolangValue.missing();
        }
        if (script.isConstant()) {
            return MolangValue.number(script.constantValue());
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                MolangContext ctx = buildDirectContext(event, sampledCalls);
                return script.evaluateValue(ctx);
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElse(null);
                if (req == null || sampledCalls.containsKey(req)) {
                    return MolangValue.missing();
                }
                sampledCalls.put(req, HostMolangBindings.resolveCall(event, req));
            } catch (RuntimeException ex) {
                return MolangValue.missing();
            }
        }
        return MolangValue.missing();
    }

    private MolangContext buildDirectContext(
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls) {
        SceneInputs inputs = HostMolangBindings.buildInputs(event, sampledCalls, false, Map.of());
        Map<String, Double> vars = new HashMap<>();
        if (this.codedCollection != null) {
            this.codedCollection.typedVariables().forEach((k, v) -> vars.put(k, v.number()));
        }
        this.adhocVariables.forEach((k, v) -> vars.put(k, v.number()));
        ScriptBudget budget = new ScriptBudget(FRAME_BUDGET_STEPS, FRAME_BUDGET_DEPTH, new CancellationToken());
        return new MolangContext(vars, vars, toCorePrograms(this.extraFunctions), budget);
    }

    private void runPending(HostActionEvent<?> event, boolean before) {
        var it = this.pendingExpressions.iterator();
        Map<MolangBindings.Call, MolangValue> calls = new HashMap<>();
        while (it.hasNext()) {
            PendingExpression next = it.next();
            if (next.executeBeforeAnimation != before) {
                continue;
            }
            it.remove();
            String result;
            try {
                if (this.roamingStruct != null && HostMolangRegistry.isRoamingVariableAssignment(next.script.source())) {
                    // Also mirror immediate scalar assignment onto RoamingStruct if simple `v.roaming.x = val`
                    applyDirectRoamingAssignment(next.script.source());
                }
                MolangValue val = evaluateScriptWithRetry(next.script, event, calls);
                if (val == null || val == MolangValue.missing()) {
                    result = "null";
                } else if (val instanceof MolangValue.TextValue text) {
                    result = "'" + text.value() + "'";
                } else if (val instanceof MolangValue.BooleanValue bool) {
                    result = Boolean.toString(bool.value());
                } else {
                    result = Double.toString(val.number());
                }
            } catch (Throwable th) {
                result = "Error: " + th.getMessage();
            }
            if (next.callback != null) {
                next.callback.accept(result);
            }
        }
    }

    private void applyDirectRoamingAssignment(String source) {
        if (this.roamingStruct == null || source == null) {
            return;
        }
        for (String stmt : source.split(";")) {
            String trimmed = stmt.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String lhs = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String rhs = trimmed.substring(eq + 1).trim();
            String prefix = lhs.startsWith("v.roaming.") ? "v.roaming." : lhs.startsWith("variable.roaming.") ? "variable.roaming." : null;
            if (prefix == null) continue;
            String varName = lhs.substring(prefix.length());
            try {
                float value = Float.parseFloat(rhs);
                this.roamingStruct.putFloat(varName, value);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void syncRoamingFromCore() {
        if (this.roamingStruct == null || this.codedCollection == null) {
            return;
        }
        MolangValue roaming = this.codedCollection.typedVariables().get("roaming");
        if (roaming != null) {
            this.roamingStruct.applyMolangDiff(roaming);
        }
    }

    public synchronized void execute(
            HostMolangScript script,
            boolean isClientPlayer,
            boolean executeBeforeAnimation,
            @Nullable Consumer<String> resultConsumer) {
        if (script == null) {
            return;
        }
        var facts = AnimationProgramAnalyzer.inspect(script);
        if (facts.unknownEffect() || !facts.writes().isEmpty() || !facts.physicsNodes().isEmpty()) {
            this.programExternalWrites = true;
            this.programs.values().forEach(AnimationProgramRuntime::close);
            this.programs.clear();
        }
        this.pendingExpressions.add(new PendingExpression(script, isClientPlayer, executeBeforeAnimation, resultConsumer));
    }

    public synchronized void setRoamingProperties(@Nullable RoamingStruct struct) {
        this.roamingStruct = struct;
        if (struct != null) {
            this.adhocVariables.put("roaming", struct.toMolangStruct());
        } else {
            this.adhocVariables.remove("roaming");
        }
    }

    public synchronized void forEachPropertyName(Consumer<String> consumer) {
        if (this.codedCollection != null) {
            this.codedCollection.typedVariables().keySet().forEach(consumer);
        }
        if (this.roamingStruct != null) {
            this.roamingStruct.forEachVar(consumer);
        }
    }

    public synchronized Map<String, MolangValue> typedVariables() {
        return this.codedCollection != null ? this.codedCollection.typedVariables() : Map.of();
    }

    public synchronized boolean runProgram(AnimationProgramPlan plan, HostActionEvent<?> event) {
        if (this.programExternalWrites) {
            return false;
        }
        var current = this.animatable.getAnimation(plan.animationName());
        if (current == null || current.programPlan() != plan) {
            return true;
        }
        var runtime = this.programs.computeIfAbsent(plan, key -> new AnimationProgramRuntime(key, this.programGeneration));
        runtime.update(this.animatable, event, this.programGeneration, this.adhocVariables);
        return true;
    }

    public synchronized void releaseProgram(AnimationProgramPlan plan) {
        var runtime = this.programs.remove(plan);
        if (runtime != null) runtime.close();
    }

    public synchronized void reset() {
        this.kipfelRuntime = null;
        this.programWorld = null;
        this.programExternalWrites = false;
        this.programGeneration++;
        this.programs.values().forEach(AnimationProgramRuntime::close);
        this.programs.clear();
        this.pendingExpressions.clear();
        this.extraFunctions.clear();
        this.eventScripts.clear();
        this.adhocVariables.clear();
        this.jsonControllers.clear();
        this.codedCollection = null;
        this.library = null;
        this.skeleton = null;
        this.bindPose = null;
        this.lastPose = null;
        this.initialized = false;
        this.frameCounter = 0;
        this.lastSeekSeconds = -1.0;
        this.slotStates.clear();
        this.audioPlayerManager.stopAll();
    }

    public boolean isDisabled() {
        return !this.initialized || this.codedCollection == null;
    }

    public int getBoneCount() {
        return this.skeleton != null ? this.skeleton.boneCount() : 0;
    }

    private static Map<String, MolangProgram> toCorePrograms(Map<String, HostMolangScript> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return Map.of();
        }
        Map<String, MolangProgram> out = new LinkedHashMap<>(scripts.size());
        scripts.forEach((k, v) -> {
            if (v != null && v.coreProgram() != null) {
                out.put(k, v.coreProgram());
            }
        });
        return out;
    }

    private static MolangHooks buildHooks(Map<String, List<HostMolangScript>> events) {
        if (events == null || events.isEmpty()) {
            return MolangHooks.empty();
        }
        return new MolangHooks(
                toProgramList(events.get("player_init")),
                toProgramList(events.get("player_update")),
                List.of(),
                List.of(),
                toProgramList(events.get("defer")),
                toProgramList(events.get("sync")));
    }

    private static List<MolangProgram> toProgramList(@Nullable List<HostMolangScript> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }
        List<MolangProgram> out = new ArrayList<>(scripts.size());
        for (HostMolangScript s : scripts) {
            if (s != null && s.coreProgram() != null) {
                out.add(s.coreProgram());
            }
        }
        return out;
    }

    private static BonePose overlayJsonPoses(BonePose base, BonePose bind, Iterable<PoseFrame> overlays) {
        float[] rot = base.rotations();
        float[] pos = base.translations();
        float[] scale = base.scales();
        float[] br = bind.rotations();
        float[] bp = bind.translations();
        float[] bs = bind.scales();
        for (PoseFrame pf : overlays) {
            BonePose op = pf.pose();
            float[] or = op.rotations();
            float[] oPos = op.translations();
            float[] os = op.scales();
            int len = Math.min(rot.length, or.length);
            for (int i = 0; i < len; i++) {
                if (or[i] != br[i]) rot[i] = or[i];
                if (oPos[i] != bp[i]) pos[i] = oPos[i];
                if (os[i] != bs[i]) scale[i] = os[i];
            }
        }
        return new BonePose(rot, pos, scale, base.ownHidden(), base.childrenHidden(), base.glow(), base.colorsRgba());
    }

    private static void reportFailure(String controllerName, Throwable failure) {
        String key = controllerName + "|" + failure.getClass().getName() + "|" + failure.getMessage();
        if (REPORTED_FAILURES.add(key)) {
            YesSteveModel.LOGGER.warn("[SM-ANIM] Animation controller '{}' failed; keeping last valid pose", controllerName, failure);
        }
    }
}
