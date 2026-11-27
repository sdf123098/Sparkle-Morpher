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
import dev.spm.core.scene.CodedPlayDecision;
import dev.spm.core.scene.CodedPoseFrame;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangException;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.MolangProgram;
import dev.spm.core.scene.MolangValue;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Per-entity SPM animation orchestrator, replacing legacy {@code AnimationProcessor}.
 * Executes both coded and JSON controller slots natively in registration order inside
 * {@code spm-core 1.3.1}'s {@link CodedAnimationCollection}, drives adaptive
 * {@link AnimationProgramRuntime} programs when bound to active clips, applies bone styles
 * and procedural overrides, and writes the result into {@link ModelScenePose}.
 */
public class HostAnimationProcessor<TEntity extends Entity> {

    private static final int FRAME_BUDGET_STEPS = 4_000_000;
    private static final int FRAME_BUDGET_DEPTH = 128;
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final AudioPlayerManager GLOBAL_AUDIO = new AudioPlayerManager();

    public static boolean supportsNativeHybridControllers() {
        return true;
    }

    private final HostAnimatable<TEntity> animatable;
    private final AudioPlayerManager audioPlayerManager = new AudioPlayerManager();
    private final CoreBoneEffects boneEffects = new CoreBoneEffects();
    private final ConcurrentLinkedQueue<PendingExpression> pendingExpressions = new ConcurrentLinkedQueue<>();

    private final Map<String, HostMolangScript> extraFunctions = new LinkedHashMap<>();
    private final Map<String, List<HostMolangScript>> eventScripts = new LinkedHashMap<>();
    private final Map<String, MolangValue> adhocVariables = new LinkedHashMap<>();
    private final Set<MolangBindings.Call> knownCallRequests = new LinkedHashSet<>();
    private final Set<String> registeredHookVarNames = new LinkedHashSet<>();
    private final Map<String, Double> pendingScalarVars = new LinkedHashMap<>();
    private boolean pendingRoamingSync;
    private boolean pendingScalarSync;
    private final Map<AnimationProgramPlan, AnimationProgramRuntime> programs = new IdentityHashMap<>();
    private final Map<String, AnimationProgramPlan> activeSlotPrograms = new HashMap<>();

    private @Nullable PoseAnimationLibrary library;
    private @Nullable Skeleton skeleton;
    private @Nullable BonePose bindPose;
    private final Map<String, Integer> boneIndexByName = new HashMap<>();
    private final Map<String, ClipDrivenCache> clipDrivenCache = new HashMap<>();
    private final Map<String, ClipDrivenCache> stateDrivenCache = new HashMap<>();
    private final Map<String, ControllerSpec> activeControllerSpecs = new LinkedHashMap<>();
    private @Nullable CodedAnimationCollection codedCollection;
    private @Nullable KipfelRuntime kipfelRuntime;

    private record ClipDrivenCache(int[] rotIndices, int[] posIndices, int[] scaleIndices, Set<String> rotationBones) {
    }

    private @Nullable RoamingStruct roamingStruct;
    private @Nullable com.micaftic.morpher.client.animation.molang.MolangWatchRegistry watchRegistry;
    private long frameCounter;
    private long lastEvaluatedCodedStep = -1L;
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
        this.boneIndexByName.clear();
        this.clipDrivenCache.clear();
        this.stateDrivenCache.clear();
        this.activeControllerSpecs.clear();
        for (int i = 0; i < this.skeleton.boneCount(); i++) {
            this.boneIndexByName.put(this.skeleton.names().get(i), i);
        }
        this.library = library != null
                ? library
                : HostAnimationAssets.compileLibrary("ysm:empty", List.of(), this.skeleton, this.bindPose);

        collectButtonHookVariableNames();
        if (this.roamingStruct != null) {
            this.adhocVariables.put("roaming", this.roamingStruct.toMolangStruct());
            this.pendingRoamingSync = true;
        }
        rebuildRuntimeControllers();
        this.initialized = true;
    }

    private void rebuildRuntimeControllers() {
        List<HostControllerSlot<?>> slots = this.animatable.getControllerSlots();
        List<CodedControllerDefinition> codedDefs = new ArrayList<>(slots.size());
        Map<String, MolangProgram> coreFunctions = toCorePrograms(this.extraFunctions);

        this.activeControllerSpecs.clear();
        this.stateDrivenCache.clear();

        MolangHooks hooks = buildHooks(this.eventScripts, this.registeredHookVarNames);
        long seed = this.animatable.getEntity().getUUID().getLeastSignificantBits();
        for (HostControllerSlot<?> slot : slots) {
            ControllerSpec jsonSpec = this.animatable.getControllerSpec(slot.getName());
            PoseControllerDefinition compiled = jsonSpec != null ? jsonSpec.compile(this.library) : null;
            if (compiled != null) {
                this.activeControllerSpecs.put(slot.getName(), jsonSpec);
                codedDefs.add(CodedControllerDefinition.hostDriven(
                        slot.getName(), slot.defaultTransitionTicks(), slot.isAdditive(), compiled));
            } else {
                codedDefs.add(CodedControllerDefinition.hostDriven(
                        slot.getName(), slot.defaultTransitionTicks(), slot.isAdditive()));
            }
        }
        this.codedCollection = new CodedAnimationCollection(
                this.library,
                this.bindPose,
                codedDefs,
                12,
                seed,
                coreFunctions,
                hooks);
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
            this.activeSlotPrograms.clear();
        }
        this.programWorld = world;

        float seekTime = event.currentTick;
        if (seekTime - this.lastAudioTickTime >= 1200.0f || this.lastAudioTickTime > seekTime) {
            this.audioPlayerManager.tick();
            GLOBAL_AUDIO.tick();
            this.lastAudioTickTime = seekTime;
        }

        double seekSeconds = Math.max(0.0, seekTime / 20.0);
        this.lastSeekSeconds = seekSeconds;
        long nowNanos = (long) (seekSeconds * 1_000_000_000.0);
        long frameId = ++this.frameCounter;

        // 1. Run pre-animation pending GUI / watch scripts
        runPending(event, true);
        if (this.watchRegistry != null) {
            this.watchRegistry.evaluatePreAnimation(buildDirectContext(event, Map.of()));
        }

        // 2. Evaluate controller predicates (JSON slots execute natively inside CodedAnimationCollection)
        Map<String, CodedControllerCommand> commands = new LinkedHashMap<>();
        HostMolangBindings.FrameCallCache callCache = HostMolangBindings.createCallCache(event);
        Map<MolangBindings.Call, MolangValue> sampledCalls = new HashMap<>(Math.max(16, this.knownCallRequests.size() * 2));
        for (MolangBindings.Call known : this.knownCallRequests) {
            sampledCalls.put(known, HostMolangBindings.resolveCall(event, known, callCache));
        }

        List<HostControllerSlot<?>> slots = this.animatable.getControllerSlots();
        if (active) {
            for (int slotIndex = 0, n = slots.size(); slotIndex < n; slotIndex++) {
                HostControllerSlot<?> slot = slots.get(slotIndex);
                HostControllerSlot<HostAnimatable<TEntity>> typedSlot =
                        (HostControllerSlot<HostAnimatable<TEntity>>) slot;
                try {
                    String soundEventKey = slot.getName().replace(".", "_ctrl_");
                    boolean hasSoundExpr = this.eventScripts.containsKey(soundEventKey)
                            && !this.eventScripts.get(soundEventKey).isEmpty();
                    CodedControllerCommand cmd = typedSlot.evaluateCommand(
                            (HostActionEvent<HostAnimatable<TEntity>>) event,
                            hasSoundExpr && checkSoundOverride(soundEventKey, event, sampledCalls, callCache));
                    commands.put(slot.getName(), cmd);
                } catch (RuntimeException ex) {
                    reportFailure(slot.getName(), ex);
                }
            }
            // Drive adaptive AnimationProgramRuntime instances for active clips carrying a verified programPlan,
            // publishing stabilized fixed-step outputs before CodedAnimationCollection evaluates the frame.
            syncActiveSlotPrograms(slots, commands, event);
        }

        Map<MolangHooks.Phase, List<MolangValue>> hookArgs = buildHookArguments();
        SceneInputs baseInputs = HostMolangBindings.buildInputs(event, sampledCalls, active, Map.of(), hookArgs);
        int baseInputsCallCount = sampledCalls.size();

        // 3. Evaluate unified CodedAnimationCollection (coded + hybrid JSON controllers) in spm-core 1.3.1
        BonePose pose = this.lastPose != null ? this.lastPose : this.bindPose;
        if (active) {
            CodedPoseFrame frame = evaluateCodedWithRetry(frameId, nowNanos, event, baseInputs, baseInputsCallCount, sampledCalls, callCache, commands, hookArgs);
            if (frame != null) {
                boolean advancedStep = frame.step() != this.lastEvaluatedCodedStep;
                this.lastEvaluatedCodedStep = frame.step();
                this.pendingRoamingSync = false;
                this.pendingScalarSync = false;
                this.pendingScalarVars.clear();
                if (!advancedStep && this.lastPose != null && frame.effects().isEmpty()) {
                    // Zero-step frame (e.g. 120-240Hz render frame between 60Hz animation ticks):
                    // reuse the already-evaluated pose without re-allocating.
                    pose = this.lastPose;
                } else {
                    pose = frame.pose();
                    Map<String, CodedControllerSnapshot> snapByName = new HashMap<>(frame.controllers().size() * 2);
                    for (CodedControllerSnapshot s : frame.controllers()) {
                        snapByName.put(s.name(), s);
                    }
                    for (int slotIndex = 0, n = slots.size(); slotIndex < n; slotIndex++) {
                        HostControllerSlot<?> slot = slots.get(slotIndex);
                        if (advancedStep) {
                            slot.acknowledgeCommandDelivery();
                        }
                        CodedControllerSnapshot snap = snapByName.get(slot.getName());
                        if (snap != null) {
                            slot.updateFeedback(snap.selectedClip(), snap.animationTimeSeconds(), snap.contributing(), false);
                            this.animatable.setAnimationState(slot.getName(),
                                    snap.contributing() ? HostPlayState.State.RUNNING : HostPlayState.State.IDLE);
                            if (!snap.contributing()) {
                                releaseSlotProgram(slot.getName());
                            }
                        }
                    }
                    // Apply bone color / transparency / glow effects
                    try {
                        pose = this.boneEffects.apply(this.skeleton, pose, frame.effects());
                    } catch (RuntimeException ex) {
                        reportFailure("bone_effects", ex);
                    }
                    // Dispatch effects (sounds, particles)
                    if (!frame.effects().isEmpty()) {
                        HostMolangBindings.dispatchEffects(this.animatable, event, this.audioPlayerManager, GLOBAL_AUDIO, scenePose, frame.effects());
                    }
                    // Propagate roaming variable writes back into RoamingStruct for server sync
                    if (advancedStep) {
                        syncRoamingFromCore();
                    }
                    this.lastPose = pose;
                }
            }
        }

        // 4. Write pose into ModelScenePose
        scenePose.writeBonePose(pose);
        if (active && this.library != null) {
            for (int i = 0, n = slots.size(); i < n; i++) {
                HostControllerSlot<?> slot = slots.get(i);
                String clip = slot.getActiveClipName();
                if (slot.isPlaying() && !clip.isEmpty()) {
                    ClipDrivenCache driven = resolveStateDrivenCache(slot.getName(), clip);
                    if (driven != null && !driven.rotationBones().isEmpty()) {
                        scenePose.markDrivenRotationBones(driven.rotationBones());
                    }
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

    private @Nullable CodedPoseFrame evaluateCodedWithRetry(
            long frameId,
            long nowNanos,
            HostActionEvent<?> event,
            SceneInputs baseInputs,
            int baseInputsCallCount,
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            HostMolangBindings.FrameCallCache callCache,
            Map<String, CodedControllerCommand> commands,
            Map<MolangHooks.Phase, List<MolangValue>> hookArgs) {
        for (int attempt = 0; attempt < 128; attempt++) {
            ScriptBudget budget = new ScriptBudget(FRAME_BUDGET_STEPS, FRAME_BUDGET_DEPTH, new CancellationToken());
            try {
                // First attempt reuses this frame's host sample when no new calls were discovered.
                SceneInputs inputs = (attempt == 0 && sampledCalls.size() == baseInputsCallCount)
                        ? new SceneInputs(baseInputs.queries(), baseInputs.bindings(), true,
                                baseInputs.syncMode(), baseInputs.hookArguments(), commands)
                        : HostMolangBindings.buildInputs(event, sampledCalls, true, commands, hookArgs);
                return this.codedCollection.evaluate(frameId, nowNanos, inputs, budget);
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElse(null);
                if (req == null || sampledCalls.containsKey(req)) {
                    reportFailure("coded_collection", missing);
                    return null;
                }
                this.knownCallRequests.add(req);
                sampledCalls.put(req, HostMolangBindings.resolveCall(event, req, callCache));
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
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            HostMolangBindings.FrameCallCache callCache) {
        List<HostMolangScript> list = this.eventScripts.get(eventKey);
        if (list == null || list.isEmpty()) {
            return false;
        }
        MolangValue result = evaluateScriptWithRetry(list.getFirst(), event, sampledCalls, callCache, null);
        int code = (int) result.number();
        return code == 2 || code == 3 || code == 4;
    }

    private MolangValue evaluateScriptWithRetry(
            HostMolangScript script,
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            @Nullable HostMolangBindings.FrameCallCache callCache,
            @Nullable Map<String, MolangValue> outVars) {
        if (script == null || !script.parsedSuccessfully()) {
            return MolangValue.missing();
        }
        if (script.isConstant()) {
            return MolangValue.number(script.constantValue());
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                MolangContext ctx = buildDirectContext(event, sampledCalls);
                MolangValue value = script.evaluateValue(ctx);
                if (outVars != null) {
                    outVars.putAll(ctx.typedVariables());
                }
                return value;
            } catch (MolangException missing) {
                var req = missing.bindingRequest().orElse(null);
                if (req == null || sampledCalls.containsKey(req)) {
                    return MolangValue.missing();
                }
                this.knownCallRequests.add(req);
                sampledCalls.put(req, HostMolangBindings.resolveCall(event, req, callCache));
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
        Map<String, MolangValue> vars = snapshotHostAndCoreVariables();
        ScriptBudget budget = new ScriptBudget(FRAME_BUDGET_STEPS, FRAME_BUDGET_DEPTH, new CancellationToken());
        return new MolangContext(inputs, vars, toCorePrograms(this.extraFunctions), budget);
    }

    private Map<String, MolangValue> snapshotHostAndCoreVariables() {
        Map<String, MolangValue> vars = new LinkedHashMap<>();
        if (this.codedCollection != null) {
            vars.putAll(this.codedCollection.typedVariables());
        }
        vars.putAll(this.adhocVariables);
        this.pendingScalarVars.forEach((k, v) -> vars.put(k, MolangValue.number(v)));
        if (this.roamingStruct != null) {
            vars.put("roaming", this.roamingStruct.toMolangStruct());
        }
        return vars;
    }

    private void runPending(HostActionEvent<?> event, boolean before) {
        if (this.pendingExpressions.isEmpty()) {
            return;
        }
        var it = this.pendingExpressions.iterator();
        Map<MolangBindings.Call, MolangValue> calls = new HashMap<>(Math.max(8, this.knownCallRequests.size() * 2));
        for (MolangBindings.Call known : this.knownCallRequests) {
            calls.put(known, HostMolangBindings.resolveCall(event, known));
        }
        while (it.hasNext()) {
            PendingExpression next = it.next();
            if (next.executeBeforeAnimation != before) {
                continue;
            }
            it.remove();
            String result;
            try {
                boolean roamingTouched = false;
                if (this.roamingStruct != null && HostMolangRegistry.isRoamingVariableAssignment(next.script.source())) {
                    // Also mirror immediate scalar assignment onto RoamingStruct if simple `v.roaming.x = val`
                    applyDirectRoamingAssignment(next.script.source());
                    this.adhocVariables.put("roaming", this.roamingStruct.toMolangStruct());
                    this.pendingRoamingSync = true;
                    roamingTouched = true;
                }
                Map<String, MolangValue> beforeVars = snapshotHostAndCoreVariables();
                Map<String, MolangValue> afterVars = new LinkedHashMap<>();
                MolangValue val = evaluateScriptWithRetry(next.script, event, calls, null, afterVars);
                recordVariableMutations(beforeVars, afterVars, roamingTouched);
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

    private void recordVariableMutations(
            Map<String, MolangValue> beforeVars,
            Map<String, MolangValue> afterVars,
            boolean roamingAlreadyTouched) {
        if (afterVars.isEmpty()) {
            return;
        }
        boolean unknownHookKey = false;
        for (Map.Entry<String, MolangValue> entry : afterVars.entrySet()) {
            String key = entry.getKey();
            MolangValue afterVal = entry.getValue();
            if ("roaming".equals(key)) {
                MolangValue beforeVal = beforeVars.get("roaming");
                if (afterVal != null && !afterVal.equals(beforeVal)) {
                    if (this.roamingStruct != null) {
                        this.roamingStruct.applyMolangDiff(afterVal);
                        this.adhocVariables.put("roaming", this.roamingStruct.toMolangStruct());
                    } else {
                        this.adhocVariables.put("roaming", afterVal);
                    }
                    this.pendingRoamingSync = true;
                }
                continue;
            }
            MolangValue beforeVal = beforeVars.get(key);
            if (afterVal == null || afterVal.equals(beforeVal)) {
                continue;
            }
            double num = afterVal.number();
            if (Double.isFinite(num)) {
                this.adhocVariables.put(key, MolangValue.number(num));
                this.pendingScalarVars.put(key, num);
                this.pendingScalarSync = true;
                if (key.matches("[a-z_][a-z0-9_]*") && this.registeredHookVarNames.add(key)) {
                    unknownHookKey = true;
                }
            }
        }
        if (roamingAlreadyTouched && this.roamingStruct != null) {
            this.adhocVariables.put("roaming", this.roamingStruct.toMolangStruct());
            this.pendingRoamingSync = true;
        }
        if (unknownHookKey && this.initialized && this.codedCollection != null) {
            // Preserve live Core variables and rebuild hook program so the newly discovered key syncs cleanly.
            this.codedCollection.typedVariables().forEach((k, v) -> {
                if (!"roaming".equals(k) && !this.pendingScalarVars.containsKey(k)) {
                    double n = v.number();
                    if (Double.isFinite(n)) {
                        this.pendingScalarVars.put(k, n);
                    }
                }
            });
            rebuildRuntimeControllers();
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
            this.activeSlotPrograms.clear();
        }
        this.pendingExpressions.add(new PendingExpression(script, isClientPlayer, executeBeforeAnimation, resultConsumer));
    }

    public synchronized void setRoamingProperties(@Nullable RoamingStruct struct) {
        this.roamingStruct = struct;
        if (struct != null) {
            this.adhocVariables.put("roaming", struct.toMolangStruct());
            this.pendingRoamingSync = true;
        } else {
            this.adhocVariables.remove("roaming");
            this.pendingRoamingSync = false;
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
        publishProgramScalars(runtime);
        return true;
    }

    public synchronized void releaseProgram(AnimationProgramPlan plan) {
        var runtime = this.programs.remove(plan);
        if (runtime != null) runtime.close();
    }

    synchronized int activeProgramCount() {
        return this.programs.size();
    }

    private void syncActiveSlotPrograms(
            List<HostControllerSlot<?>> slots,
            Map<String, CodedControllerCommand> commands,
            HostActionEvent<HostAnimatable<TEntity>> event) {
        if (this.programExternalWrites) {
            if (!this.activeSlotPrograms.isEmpty()) {
                this.activeSlotPrograms.clear();
            }
            return;
        }
        for (int i = 0, n = slots.size(); i < n; i++) {
            HostControllerSlot<?> slot = slots.get(i);
            String slotName = slot.getName();
            CodedControllerCommand cmd = commands.get(slotName);
            String clipName;
            if (cmd != null && cmd.decision() == CodedPlayDecision.STOP) {
                clipName = "";
            } else if (cmd != null && !cmd.animation().isEmpty()) {
                clipName = cmd.animation();
            } else if (slot.isPlaying()) {
                clipName = slot.getActiveClipName();
            } else {
                clipName = "";
            }
            HostAnimationAssets.ClipSpec clip = clipName.isEmpty() ? null : this.animatable.getAnimation(clipName);
            AnimationProgramPlan plan = clip != null ? clip.programPlan() : null;
            AnimationProgramPlan prev = this.activeSlotPrograms.get(slotName);
            if (prev != null && prev != plan) {
                this.activeSlotPrograms.remove(slotName);
                if (!this.activeSlotPrograms.containsValue(prev)) {
                    releaseProgram(prev);
                }
            }
            if (plan != null) {
                this.activeSlotPrograms.put(slotName, plan);
                runProgram(plan, event);
            }
        }
    }

    private void releaseSlotProgram(String slotName) {
        AnimationProgramPlan prev = this.activeSlotPrograms.remove(slotName);
        if (prev != null && !this.activeSlotPrograms.containsValue(prev)) {
            releaseProgram(prev);
        }
    }

    private void publishProgramScalars(AnimationProgramRuntime runtime) {
        AnimationProgramRuntime.Snapshot snap = runtime.snapshot();
        if (snap == null || snap.variables().isEmpty()) {
            return;
        }
        boolean unknownHookKey = false;
        Map<String, MolangValue> live = this.codedCollection != null ? this.codedCollection.typedVariables() : Map.of();
        for (Map.Entry<String, MolangValue> entry : snap.variables().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if ("roaming".equals(key) || !key.matches("[a-z_][a-z0-9_]*")) {
                continue;
            }
            MolangValue value = entry.getValue();
            if (value == null) {
                continue;
            }
            double num = value.number();
            if (!Double.isFinite(num)) {
                continue;
            }
            this.adhocVariables.put(key, MolangValue.number(num));
            MolangValue existing = live.get(key);
            if (existing == null || Double.compare(existing.number(), num) != 0) {
                this.pendingScalarVars.put(key, num);
                this.pendingScalarSync = true;
            }
            if (this.registeredHookVarNames.add(key)) {
                unknownHookKey = true;
            }
        }
        if (unknownHookKey && this.initialized && this.codedCollection != null) {
            this.codedCollection.typedVariables().forEach((k, v) -> {
                if (!"roaming".equals(k) && !this.pendingScalarVars.containsKey(k)) {
                    double n = v.number();
                    if (Double.isFinite(n)) {
                        this.pendingScalarVars.put(k, n);
                    }
                }
            });
            rebuildRuntimeControllers();
        }
    }

    public synchronized void reset() {
        this.kipfelRuntime = null;
        this.programWorld = null;
        this.programExternalWrites = false;
        this.programGeneration++;
        this.programs.values().forEach(AnimationProgramRuntime::close);
        this.programs.clear();
        this.activeSlotPrograms.clear();
        this.pendingExpressions.clear();
        this.extraFunctions.clear();
        this.eventScripts.clear();
        this.adhocVariables.clear();
        this.knownCallRequests.clear();
        this.registeredHookVarNames.clear();
        this.pendingScalarVars.clear();
        this.pendingRoamingSync = false;
        this.pendingScalarSync = false;
        this.activeControllerSpecs.clear();
        this.codedCollection = null;
        this.library = null;
        this.skeleton = null;
        this.bindPose = null;
        this.boneIndexByName.clear();
        this.clipDrivenCache.clear();
        this.stateDrivenCache.clear();
        this.lastPose = null;
        this.initialized = false;
        this.frameCounter = 0;
        this.lastEvaluatedCodedStep = -1L;
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

    private void collectButtonHookVariableNames() {
        var props = this.animatable.resolveModelProperties();
        if (props == null) {
            return;
        }
        var buttons = props.getExtraAnimationButtons();
        if (buttons == null) {
            return;
        }
        var matcher = java.util.regex.Pattern.compile("(?:v|variable)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*=").matcher("");
        for (var btn : buttons.values()) {
            if (btn == null || btn.getConfigForms() == null) {
                continue;
            }
            for (var form : btn.getConfigForms()) {
                if (form == null) {
                    continue;
                }
                scanHookVarNames(matcher, form.getValue(), this.registeredHookVarNames);
                if (form instanceof com.micaftic.morpher.client.gui.custom.configs.RadioConfig radio && radio.getLabels() != null) {
                    for (String expr : radio.getLabels().values()) {
                        scanHookVarNames(matcher, expr, this.registeredHookVarNames);
                    }
                }
            }
        }
    }

    private static void scanHookVarNames(java.util.regex.Matcher matcher, @Nullable String script, Set<String> out) {
        if (script == null || script.isEmpty()) {
            return;
        }
        matcher.reset(script);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!"roaming".equals(key) && key.matches("[a-z_][a-z0-9_]*")) {
                out.add(key);
            }
        }
    }

    private Map<MolangHooks.Phase, List<MolangValue>> buildHookArguments() {
        if (!this.pendingRoamingSync && !this.pendingScalarSync) {
            return Map.of();
        }
        MolangValue roamingVal = this.adhocVariables.get("roaming");
        boolean syncRoaming = this.pendingRoamingSync && roamingVal != null;
        boolean syncScalar = this.pendingScalarSync && !this.pendingScalarVars.isEmpty();
        if (!syncRoaming && !syncScalar) {
            return Map.of();
        }
        MolangValue scalarStruct = MolangValue.missing();
        if (syncScalar) {
            Map<String, MolangValue> fields = new LinkedHashMap<>();
            this.pendingScalarVars.forEach((k, v) -> fields.put(k, MolangValue.number(v)));
            scalarStruct = fields.isEmpty() ? MolangValue.missing() : new MolangValue.StructValue(fields);
        }
        List<MolangValue> args = List.of(
                MolangValue.number(syncRoaming ? 1.0 : 0.0),
                syncRoaming ? roamingVal : MolangValue.missing(),
                MolangValue.number(syncScalar && scalarStruct != MolangValue.missing() ? 1.0 : 0.0),
                scalarStruct);
        return Map.of(
                MolangHooks.Phase.INITIALIZE, args,
                MolangHooks.Phase.BEFORE_STEP, args);
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

    private static MolangHooks buildHooks(Map<String, List<HostMolangScript>> events, Set<String> hookVarNames) {
        StringBuilder syncSrc = new StringBuilder("args[0]?{v.roaming=args[1];}:0;");
        if (hookVarNames != null && !hookVarNames.isEmpty()) {
            syncSrc.append("args[2]?{");
            for (String key : hookVarNames) {
                syncSrc.append("v.").append(key).append("=args[3].").append(key).append("??v.").append(key).append(';');
            }
            syncSrc.append("}:0;");
        }
        MolangProgram syncProgram = MolangProgram.compile(syncSrc.toString());

        List<MolangProgram> initList = new ArrayList<>();
        initList.add(syncProgram);
        if (events != null) {
            initList.addAll(toProgramList(events.get("player_init")));
        }
        List<MolangProgram> updateList = new ArrayList<>();
        updateList.add(syncProgram);
        if (events != null) {
            updateList.addAll(toProgramList(events.get("player_update")));
        }
        return new MolangHooks(
                initList,
                updateList,
                List.of(),
                List.of(),
                events != null ? toProgramList(events.get("defer")) : List.of(),
                events != null ? toProgramList(events.get("sync")) : List.of());
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

    public record OrderedJsonLayer(
            int slotIndex,
            String slotName,
            PoseFrame frame,
            int @Nullable [] drivenRotIndices,
            int @Nullable [] drivenPosIndices,
            int @Nullable [] drivenScaleIndices) {
        public OrderedJsonLayer(int slotIndex, String slotName, PoseFrame frame) {
            this(slotIndex, slotName, frame, null, null, null);
        }
    }

    private @Nullable ClipDrivenCache resolveStateDrivenCache(String slotName, String stateOrClipName) {
        if (this.library == null || stateOrClipName == null || stateOrClipName.isEmpty()) {
            return null;
        }
        ControllerSpec spec = this.activeControllerSpecs.get(slotName);
        if (spec != null) {
            HostAnimationAssets.ControllerStateSpec state = spec.states().get(stateOrClipName);
            if (state != null) {
                String cacheKey = slotName + '\0' + stateOrClipName;
                ClipDrivenCache cached = this.stateDrivenCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
                List<HostAnimationAssets.BlendEntry> anims = state.animations();
                if (anims.isEmpty()) {
                    ClipDrivenCache empty = new ClipDrivenCache(new int[0], new int[0], new int[0], Set.of());
                    this.stateDrivenCache.put(cacheKey, empty);
                    return empty;
                }
                if (anims.size() == 1) {
                    ClipDrivenCache single = resolveClipDrivenCache(anims.getFirst().animationName());
                    ClipDrivenCache result = single != null
                            ? single
                            : new ClipDrivenCache(new int[0], new int[0], new int[0], Set.of());
                    this.stateDrivenCache.put(cacheKey, result);
                    return result;
                }
                Set<String> rotBones = new LinkedHashSet<>();
                Set<String> posBones = new LinkedHashSet<>();
                Set<String> scaleBones = new LinkedHashSet<>();
                for (HostAnimationAssets.BlendEntry entry : anims) {
                    String clipName = entry.animationName();
                    if (clipName != null && this.library.hasClip(clipName)) {
                        PoseAnimationLibrary.ClipSummary summary = this.library.clipSummary(clipName);
                        rotBones.addAll(summary.rotationBones());
                        posBones.addAll(summary.positionBones());
                        scaleBones.addAll(summary.scaleBones());
                    }
                }
                ClipDrivenCache built = new ClipDrivenCache(
                        resolveBoneIndexArray(rotBones, this.boneIndexByName),
                        resolveBoneIndexArray(posBones, this.boneIndexByName),
                        resolveBoneIndexArray(scaleBones, this.boneIndexByName),
                        Set.copyOf(rotBones));
                this.stateDrivenCache.put(cacheKey, built);
                return built;
            }
        }
        return resolveClipDrivenCache(stateOrClipName);
    }

    private @Nullable ClipDrivenCache resolveClipDrivenCache(String clipName) {
        if (this.library == null || clipName == null || clipName.isEmpty()) {
            return null;
        }
        ClipDrivenCache cached = this.clipDrivenCache.get(clipName);
        if (cached != null) {
            return cached;
        }
        if (!this.library.hasClip(clipName)) {
            return null;
        }
        PoseAnimationLibrary.ClipSummary summary = this.library.clipSummary(clipName);
        ClipDrivenCache built = new ClipDrivenCache(
                resolveBoneIndexArray(summary.rotationBones(), this.boneIndexByName),
                resolveBoneIndexArray(summary.positionBones(), this.boneIndexByName),
                resolveBoneIndexArray(summary.scaleBones(), this.boneIndexByName),
                summary.rotationBones());
        this.clipDrivenCache.put(clipName, built);
        return built;
    }

    private static int[] resolveBoneIndexArray(Set<String> bones, Map<String, Integer> boneIndexByName) {
        if (bones == null || bones.isEmpty()) {
            return new int[0];
        }
        int[] raw = new int[bones.size()];
        int count = 0;
        for (String boneName : bones) {
            Integer idx = boneIndexByName.get(boneName);
            if (idx != null && idx >= 0) {
                raw[count++] = idx;
            }
        }
        return count == raw.length ? raw : java.util.Arrays.copyOf(raw, count);
    }

    private static boolean @Nullable [] toDrivenMask(int @Nullable [] indices, int boneCount) {
        if (indices == null || indices.length == 0 || boneCount <= 0) {
            return null;
        }
        boolean[] mask = new boolean[boneCount];
        for (int idx : indices) {
            if (idx >= 0 && idx < boneCount) {
                mask[idx] = true;
            }
        }
        return mask;
    }

    /**
     * Composites JSON controller layers in exact controller registration order against the combined coded pose.
     * Each bone channel (rotation / position / scale) is overwritten by a JSON layer only if that JSON layer's
     * slot index is strictly higher than the highest coded slot that drove that bone channel (from clip track
     * summaries, with non-bind fallback). When a layer carries explicit driven bone indices for its active state,
     * channels whose evaluated value happens to equal the bind pose (e.g. {@code Wing.scale = [1,1,1]} or
     * {@code LeftLeg.pos = [0,0,0]}) are preserved rather than silently dropped.
     */
    public static BonePose compositeOrderedJsonPoses(
            BonePose codedPose,
            BonePose bind,
            List<OrderedJsonLayer> orderedLayers,
            int[] maxCodedRotSlot,
            int[] maxCodedPosSlot,
            int[] maxCodedScaleSlot,
            int fallbackCodedSlot) {
        float[] rot = codedPose.rotations();
        float[] pos = codedPose.translations();
        float[] scale = codedPose.scales();
        float[] br = bind.rotations();
        float[] bp = bind.translations();
        float[] bs = bind.scales();
        int boneCount = Math.min(rot.length, br.length) / 3;

        int[] effRotSlot = new int[boneCount];
        int[] effPosSlot = new int[boneCount];
        int[] effScaleSlot = new int[boneCount];
        for (int b = 0; b < boneCount; b++) {
            int i = b * 3;
            int rSlot = b < maxCodedRotSlot.length ? maxCodedRotSlot[b] : -1;
            int pSlot = b < maxCodedPosSlot.length ? maxCodedPosSlot[b] : -1;
            int sSlot = b < maxCodedScaleSlot.length ? maxCodedScaleSlot[b] : -1;
            if (rSlot < 0 && fallbackCodedSlot >= 0 && (rot[i] != br[i] || rot[i + 1] != br[i + 1] || rot[i + 2] != br[i + 2])) {
                rSlot = fallbackCodedSlot;
            }
            if (pSlot < 0 && fallbackCodedSlot >= 0 && (pos[i] != bp[i] || pos[i + 1] != bp[i + 1] || pos[i + 2] != bp[i + 2])) {
                pSlot = fallbackCodedSlot;
            }
            if (sSlot < 0 && fallbackCodedSlot >= 0 && (scale[i] != bs[i] || scale[i + 1] != bs[i + 1] || scale[i + 2] != bs[i + 2])) {
                sSlot = fallbackCodedSlot;
            }
            effRotSlot[b] = rSlot;
            effPosSlot[b] = pSlot;
            effScaleSlot[b] = sSlot;
        }

        for (OrderedJsonLayer layer : orderedLayers) {
            int layerSlot = layer.slotIndex();
            BonePose op = layer.frame().pose();
            float[] or = op.rotations();
            float[] oPos = op.translations();
            float[] os = op.scales();
            int count = Math.min(boneCount, or.length / 3);
            boolean[] drivenRot = toDrivenMask(layer.drivenRotIndices(), count);
            boolean[] drivenPos = toDrivenMask(layer.drivenPosIndices(), count);
            boolean[] drivenScale = toDrivenMask(layer.drivenScaleIndices(), count);
            for (int b = 0; b < count; b++) {
                int i = b * 3;
                if (layerSlot > effRotSlot[b]
                        && ((drivenRot != null && drivenRot[b])
                        || or[i] != br[i] || or[i + 1] != br[i + 1] || or[i + 2] != br[i + 2])) {
                    rot[i] = or[i];
                    rot[i + 1] = or[i + 1];
                    rot[i + 2] = or[i + 2];
                    effRotSlot[b] = layerSlot;
                }
                if (layerSlot > effPosSlot[b]
                        && ((drivenPos != null && drivenPos[b])
                        || oPos[i] != bp[i] || oPos[i + 1] != bp[i + 1] || oPos[i + 2] != bp[i + 2])) {
                    pos[i] = oPos[i];
                    pos[i + 1] = oPos[i + 1];
                    pos[i + 2] = oPos[i + 2];
                    effPosSlot[b] = layerSlot;
                }
                if (layerSlot > effScaleSlot[b]
                        && ((drivenScale != null && drivenScale[b])
                        || os[i] != bs[i] || os[i + 1] != bs[i + 1] || os[i + 2] != bs[i + 2])) {
                    scale[i] = os[i];
                    scale[i + 1] = os[i + 1];
                    scale[i + 2] = os[i + 2];
                    effScaleSlot[b] = layerSlot;
                }
            }
        }
        return new BonePose(rot, pos, scale, codedPose.ownHidden(), codedPose.childrenHidden(), codedPose.glow(), codedPose.colorsRgba());
    }

    public static boolean isPreLayerJsonController(String slotName) {
        if (slotName == null || slotName.isEmpty()) {
            return false;
        }
        return slotName.contains(".pre_parallel_")
                || slotName.endsWith(".pre_main")
                || slotName.endsWith(".post_main")
                || slotName.endsWith(".pre_hold")
                || slotName.startsWith("pre_parallel_");
    }

    public static BonePose compositeJsonPoses(
            BonePose codedPose,
            BonePose bind,
            Iterable<PoseFrame> preLayers,
            Iterable<PoseFrame> postLayers) {
        float[] rot = codedPose.rotations();
        float[] pos = codedPose.translations();
        float[] scale = codedPose.scales();
        float[] br = bind.rotations();
        float[] bp = bind.translations();
        float[] bs = bind.scales();
        int boneCount = Math.min(rot.length, br.length) / 3;

        // 1. Pre-layers underlay: only populate bones that coded controllers left at bind pose.
        // Operate per 3-axis vector group so Euler axes are never torn between two layers.
        boolean[] codedRotActive = null;
        boolean[] codedPosActive = null;
        boolean[] codedScaleActive = null;
        for (PoseFrame pf : preLayers) {
            if (codedRotActive == null) {
                codedRotActive = new boolean[boneCount];
                codedPosActive = new boolean[boneCount];
                codedScaleActive = new boolean[boneCount];
                for (int b = 0; b < boneCount; b++) {
                    int i = b * 3;
                    codedRotActive[b] = rot[i] != br[i] || rot[i + 1] != br[i + 1] || rot[i + 2] != br[i + 2];
                    codedPosActive[b] = pos[i] != bp[i] || pos[i + 1] != bp[i + 1] || pos[i + 2] != bp[i + 2];
                    codedScaleActive[b] = scale[i] != bs[i] || scale[i + 1] != bs[i + 1] || scale[i + 2] != bs[i + 2];
                }
            }
            BonePose op = pf.pose();
            float[] or = op.rotations();
            float[] oPos = op.translations();
            float[] os = op.scales();
            int count = Math.min(boneCount, or.length / 3);
            for (int b = 0; b < count; b++) {
                int i = b * 3;
                if (!codedRotActive[b] && (or[i] != br[i] || or[i + 1] != br[i + 1] || or[i + 2] != br[i + 2])) {
                    rot[i] = or[i];
                    rot[i + 1] = or[i + 1];
                    rot[i + 2] = or[i + 2];
                }
                if (!codedPosActive[b] && (oPos[i] != bp[i] || oPos[i + 1] != bp[i + 1] || oPos[i + 2] != bp[i + 2])) {
                    pos[i] = oPos[i];
                    pos[i + 1] = oPos[i + 1];
                    pos[i + 2] = oPos[i + 2];
                }
                if (!codedScaleActive[b] && (os[i] != bs[i] || os[i + 1] != bs[i + 1] || os[i + 2] != bs[i + 2])) {
                    scale[i] = os[i];
                    scale[i + 1] = os[i + 1];
                    scale[i + 2] = os[i + 2];
                }
            }
        }

        // 2. Post-layers overlay: override per 3-axis vector group whenever the post-layer moved a bone.
        for (PoseFrame pf : postLayers) {
            BonePose op = pf.pose();
            float[] or = op.rotations();
            float[] oPos = op.translations();
            float[] os = op.scales();
            int count = Math.min(boneCount, or.length / 3);
            for (int b = 0; b < count; b++) {
                int i = b * 3;
                if (or[i] != br[i] || or[i + 1] != br[i + 1] || or[i + 2] != br[i + 2]) {
                    rot[i] = or[i];
                    rot[i + 1] = or[i + 1];
                    rot[i + 2] = or[i + 2];
                }
                if (oPos[i] != bp[i] || oPos[i + 1] != bp[i + 1] || oPos[i + 2] != bp[i + 2]) {
                    pos[i] = oPos[i];
                    pos[i + 1] = oPos[i + 1];
                    pos[i + 2] = oPos[i + 2];
                }
                if (os[i] != bs[i] || os[i + 1] != bs[i + 1] || os[i + 2] != bs[i + 2]) {
                    scale[i] = os[i];
                    scale[i + 1] = os[i + 1];
                    scale[i + 2] = os[i + 2];
                }
            }
        }
        return new BonePose(rot, pos, scale, codedPose.ownHidden(), codedPose.childrenHidden(), codedPose.glow(), codedPose.colorsRgba());
    }

    private static void reportFailure(String controllerName, Throwable failure) {
        String key = controllerName + "|" + failure.getClass().getName() + "|" + failure.getMessage();
        if (REPORTED_FAILURES.add(key)) {
            YesSteveModel.LOGGER.warn("[SM-ANIM] Animation controller '{}' failed; keeping last valid pose", controllerName, failure);
        }
    }
}
