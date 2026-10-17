package com.micaftic.morpher.geckolib3.core.processor;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioPlayerManager;
import com.micaftic.morpher.client.animation.molang.PhysicsManager;
import com.micaftic.morpher.geckolib3.core.manager.AnimationData;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.storage.IForeignVariableStorage;
import com.micaftic.morpher.geckolib3.core.molang.storage.VariableStorage;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.core.snapshot.BoneTopLevelSnapshot;
import com.micaftic.morpher.geckolib3.core.util.EulerNlerpScratch;
import com.micaftic.morpher.geckolib3.core.util.MathUtil;
import com.micaftic.morpher.geckolib3.core.controller.BoneTransformProvider;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.util.TransitionVector3f;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Struct;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class AnimationProcessor<TEntity extends Entity> {

    private static final int ROAMING_STRUCT_NAME = StringPool.computeIfAbsent("roaming");
    private static final Set<String> REPORTED_CONTROLLER_FAILURES = ConcurrentHashMap.newKeySet();

    private final AnimatableEntity<TEntity> animatable;

    private final ReferenceArrayList<BoneTopLevelSnapshot> bones = new ReferenceArrayList<>();

    private Object2ReferenceMap<String, List<IValue>> initExpressions = Object2ReferenceMaps.emptyMap();

    private final Int2ReferenceOpenHashMap<BoneTopLevelSnapshot> boneById = new Int2ReferenceOpenHashMap<>();

    private final ArrayDeque<BoneTopLevelSnapshot> modelRendererList = new ArrayDeque<>();

    private final VariableStorage animationStorage = new VariableStorage();

    private final AudioPlayerManager audioPlayerManager = new AudioPlayerManager();

    private final RandomSource random = new XoroshiroRandomSource(RandomSupport.generateUniqueSeed());

    private final ConcurrentLinkedQueue<PendingExpression> pendingExpressions = new ConcurrentLinkedQueue<>();

    private float lastAudioTickTime = 0.0f;

    private boolean needsInit = false;

    // tickAnimation 给每帧 forEachTransform 复用同一个 Consumer
    private final Consumer<BoneTransformProvider> transformConsumer = this::applyTransform;
    private ExpressionEvaluator<AnimationContext<?>> currentEvaluator;
    private float currentSeekTime;
    private boolean currentDeprecatedMode;
    private final EulerNlerpScratch rotScratch = new EulerNlerpScratch();
    private final Vector3f lastRotation = new Vector3f(), lastPosition = new Vector3f(), lastScale = new Vector3f();
    private com.micaftic.morpher.client.animation.molang.KipfelRuntime kipfelRuntime;
    private volatile long programGeneration;
    public long programGeneration() { return programGeneration; }
    private Object programWorld;
    private boolean programExternalWrites;
    private final java.util.Map<com.micaftic.morpher.client.animation.molang.AnimationProgramPlan,
            com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime> programs = new java.util.IdentityHashMap<>();

    public synchronized boolean runProgram(com.micaftic.morpher.client.animation.molang.AnimationProgramPlan plan, AnimationContext<?> context) {
        if (context.programGeneration != programGeneration) return true;
        var current = this.animatable.getAnimation(plan.animationName());
        // A delayed slot from an old model must neither recreate a runtime nor execute its migrated writes.
        if (current == null || current.programPlan != plan) return true;
        if (programExternalWrites || context.peerInput()) return false;
        var runtime = programs.computeIfAbsent(plan, key -> new com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime(key, programGeneration));
        runtime.update(context, programGeneration);
        return true;
    }

    public synchronized void releaseProgram(com.micaftic.morpher.client.animation.molang.AnimationProgramPlan plan) {
        var runtime = programs.remove(plan);
        if (runtime != null) runtime.close();
    }

    public Vector3f sampleKipfelRotation(String animation,int bone,Vector3f initial) {
        return kipfelRuntime == null ? null : kipfelRuntime.sample(animation,bone,initial);
    }

    public boolean isRotationActive(int boneId) {
        BoneTopLevelSnapshot snapshot = this.boneById.get(boneId);
        return snapshot != null && snapshot.isCurrentlyRunningRotationAnimation;
    }

    public AnimationProcessor(AnimatableEntity<TEntity> animatable) {
        this.animatable = animatable;
    }

    public synchronized void tickAnimation(AnimationEvent<AnimatableEntity<TEntity>> event, AnimationContext<?> context, boolean z, boolean z2) {
        if (context.programGeneration != programGeneration) return;
        Object world = this.animatable.entity.level();
        if (programWorld != null && programWorld != world) {
            programGeneration++;
            programs.values().forEach(com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime::close);
            programs.clear();
        }
        programWorld = world;
        context.programGeneration = programGeneration;
        context.setStorage(this.animationStorage);
        for (var program : programs.values()) program.publish(context);
        context.setRandom(this.random);
        context.setAudioPlayerManager(this.audioPlayerManager);
        ExpressionEvaluator<AnimationContext<?>> evaluator = ExpressionEvaluator.evaluator(context);
        float seekTime = event.currentTick;
        if (seekTime - this.lastAudioTickTime >= 1200.0f) {
            this.audioPlayerManager.tick();
            this.lastAudioTickTime = seekTime;
        } else if (this.lastAudioTickTime > seekTime) {
            this.lastAudioTickTime = seekTime;
        }
        preProcess(evaluator);
        if (programExternalWrites) context.programPoseValues = java.util.Map.of();
        var attachment = this.animatable.getAnimation(com.micaftic.morpher.resource.bundle.KipfelCompatibility.HAIR);
        if (z && attachment != null && attachment.stableKipfelAttachments) {
            if (kipfelRuntime == null) kipfelRuntime = new com.micaftic.morpher.client.animation.molang.KipfelRuntime(this.animatable);
            kipfelRuntime.update(event,context,evaluator);
        }
        AnimationData manager = this.animatable.getAnimationData();
        this.currentEvaluator = evaluator;
        this.currentSeekTime = seekTime;
        for (IAnimationController controller : manager.getAnimationControllers()) {
            if (this.needsInit) {
                controller.init(this.bones, this.initExpressions);
            }
            if (z) {
                try {
                    controller.process(event, evaluator, z2);
                } catch (RuntimeException exception) {
                    event.setController(null);
                    reportControllerFailure(controller, exception);
                }
            }
            this.currentDeprecatedMode = controller.isDeprecatedMode();
            controller.forEachTransform(this.transformConsumer);
        }
        this.currentEvaluator = null;
        this.needsInit = false;
        Iterator<BoneTopLevelSnapshot> iterator = this.modelRendererList.iterator();
        while (iterator.hasNext()) {
            BoneTopLevelSnapshot topLevelSnapshot = iterator.next();
            boolean runningAnimation = false;
            if (topLevelSnapshot.isCurrentlyRunningRotationAnimation) {
                runningAnimation = true;
                topLevelSnapshot.isCurrentlyRunningRotationAnimation = false;
                topLevelSnapshot.prevRotation = null;
            } else {
                if (topLevelSnapshot.prevRotation == null) {
                    topLevelSnapshot.prevRotation = new Vector3f(topLevelSnapshot.rotation);
                }
                float percentageReset = (seekTime - topLevelSnapshot.mostRecentResetRotationTick) / manager.getResetSpeed();
                if (percentageReset < 1.0f) {
                    runningAnimation = true;
                    MathUtil.nlerpEulerAngles(percentageReset, topLevelSnapshot.prevRotation, MathUtil.ZERO, topLevelSnapshot.bone.getInitialRotation(), topLevelSnapshot.rotation, this.rotScratch);
                } else {
                    topLevelSnapshot.rotation.set(MathUtil.ZERO);
                }
            }
            if (topLevelSnapshot.isCurrentlyRunningPositionAnimation) {
                runningAnimation = true;
                topLevelSnapshot.isCurrentlyRunningPositionAnimation = false;
                topLevelSnapshot.prevPosition = null;
            } else {
                if (topLevelSnapshot.prevPosition == null) {
                    topLevelSnapshot.prevPosition = new Vector3f(topLevelSnapshot.position);
                }
                float percentageReset = (seekTime - topLevelSnapshot.mostRecentResetPositionTick) / manager.getResetSpeed();
                if (percentageReset < 1.0f) {
                    runningAnimation = true;
                    MathUtil.lerpValues(percentageReset, topLevelSnapshot.prevPosition, MathUtil.ZERO, topLevelSnapshot.position);
                } else {
                    topLevelSnapshot.position.set(0.0f, 0.0f, 0.0f);
                }
            }
            if (topLevelSnapshot.isCurrentlyRunningScaleAnimation) {
                runningAnimation = true;
                topLevelSnapshot.isCurrentlyRunningScaleAnimation = false;
                topLevelSnapshot.prevScale = null;
            } else {
                if (topLevelSnapshot.prevScale == null) {
                    topLevelSnapshot.prevScale = new Vector3f(topLevelSnapshot.scale);
                }
                float percentageReset = (seekTime - topLevelSnapshot.mostRecentResetScaleTick) / manager.getResetSpeed();
                if (percentageReset < 1.0f) {
                    runningAnimation = true;
                    MathUtil.lerpValues(percentageReset, topLevelSnapshot.prevScale, MathUtil.ONE, topLevelSnapshot.scale);
                } else {
                    topLevelSnapshot.scale.set(1.0f, 1.0f, 1.0f);
                }
            }
            topLevelSnapshot.reset();
            if (!runningAnimation) {
                topLevelSnapshot.isCurrentlyRunningAnimation = false;
                iterator.remove();
            }
        }
        context.setPlaybackFlags(null);
        context.setAnimationControllerContext(null);
        postProcess(evaluator);
    }

    private static void reportControllerFailure(IAnimationController<?> controller, RuntimeException exception) {
        String failureKey = controller.getName() + '|' + exception.getClass().getName() + '|' + exception.getMessage();
        if (REPORTED_CONTROLLER_FAILURES.add(failureKey)) {
            YesSteveModel.LOGGER.warn("[SM-ANIM] Animation controller '{}' failed; keeping its last valid pose and continuing other controllers",
                    controller.getName(), exception);
        }
    }

    private void applyTransform(BoneTransformProvider provider) {
        final BoneTopLevelSnapshot snapshot = provider.getBoneTarget();
        if (!snapshot.isCurrentlyRunningAnimation) {
            snapshot.isCurrentlyRunningAnimation = true;
            this.modelRendererList.add(snapshot);
        }
        final ExpressionEvaluator<AnimationContext<?>> evaluator = this.currentEvaluator;
        final float seekTime = this.currentSeekTime;

        TransitionVector3f rot;
        PhysicsManager.pushScope(snapshot.boneId);
        try {
            rot = provider.getRotation(evaluator);
        } finally {
            PhysicsManager.popScope();
        }
        if (rot != null) {
            lastRotation.set(snapshot.rotation);
            Vector3f vector3f = snapshot.currentValue;
            if (!snapshot.isCurrentlyRunningRotationAnimation) {
                snapshot.isCurrentlyRunningRotationAnimation = true;
                snapshot.rotation.set(0.0f, 0.0f, 0.0f);
            }
            snapshot.mostRecentResetRotationTick = seekTime;
            if (this.currentDeprecatedMode) {
                vector3f.add(rot);
                snapshot.rotation.set(vector3f);
            } else {
                rot.applyRotationBlendTo(snapshot.rotation, snapshot.bone.getInitialRotation(), this.rotScratch);
                vector3f.set(snapshot.rotation);
            }
            // molang/物理可能产生 NaN/Inf（无效数学、物理震荡等）→ 消毒，防止骨骼矩阵炸开乱飘
            if (!finite(snapshot.rotation)) snapshot.rotation.set(lastRotation);
            vector3f.set(snapshot.rotation);
        }

        TransitionVector3f pos;
        PhysicsManager.pushScope(snapshot.boneId);
        try {
            pos = provider.getPosition(evaluator);
        } finally {
            PhysicsManager.popScope();
        }
        if (pos != null) {
            lastPosition.set(snapshot.position);
            if (!snapshot.isCurrentlyRunningPositionAnimation) {
                snapshot.isCurrentlyRunningPositionAnimation = true;
                snapshot.position.set(0.0f, 0.0f, 0.0f);
            }
            snapshot.mostRecentResetPositionTick = seekTime;
            pos.applyLinearBlendTo(snapshot.position);
            if (!finite(snapshot.position)) snapshot.position.set(lastPosition);
        }

        TransitionVector3f scale;
        PhysicsManager.pushScope(snapshot.boneId);
        try {
            scale = provider.getScale(evaluator);
        } finally {
            PhysicsManager.popScope();
        }
        if (scale != null) {
            lastScale.set(snapshot.scale);
            if (!snapshot.isCurrentlyRunningScaleAnimation) {
                snapshot.isCurrentlyRunningScaleAnimation = true;
                snapshot.scale.set(1.0f, 1.0f, 1.0f);
            }
            snapshot.mostRecentResetScaleTick = seekTime;
            scale.applyLinearBlendTo(snapshot.scale);
            if (!finite(snapshot.scale)) snapshot.scale.set(lastScale);
        }
    }

    private static boolean finite(Vector3f v) { return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z); }

    @Nullable
    public IBone getBone(int i) {
        BoneTopLevelSnapshot renderer = this.boneById.get(i);
        return renderer != null ? renderer.bone : null;
    }

    public int getBoneCount() {
        return this.bones.size();
    }

    public synchronized void reset() {
        this.kipfelRuntime = null;
        programWorld = null;
        programExternalWrites = false;
        programGeneration++;
        programs.values().forEach(com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime::close);
        programs.clear();
        this.boneById.clear();
        this.modelRendererList.clear();
        this.bones.clear();
        this.animationStorage.initialize(null);
        this.initExpressions = Object2ReferenceMaps.emptyMap();
        this.pendingExpressions.clear();
        this.audioPlayerManager.stopAll();
    }

    public void initBones(AnimatedGeoModel model, Object2ReferenceMap<String, List<IValue>> object2ReferenceMap) {
        reset();
        if (!model.bones().isEmpty()) {
            this.bones.ensureCapacity(model.bones().size());
            this.bones.add(null);
            int boneId = model.getGeoModel().bones.get(0).getBoneId();
            Int2ReferenceMaps.fastForEach(model.bones(), entry -> {
                BoneTopLevelSnapshot boneTopLevelSnapshot = new BoneTopLevelSnapshot(entry.getValue());
                this.boneById.put(entry.getValue().getBoneId(), boneTopLevelSnapshot);
                if (entry.getIntKey() == boneId) {
                    this.bones.set(0, boneTopLevelSnapshot);
                } else {
                    this.bones.add(boneTopLevelSnapshot);
                }
            });
        }
        this.needsInit = true;
        this.initExpressions = object2ReferenceMap;
    }

    public void setRoamingProperties(@Nullable Struct struct) {
        if (struct != null) {
            this.animationStorage.setScoped(ROAMING_STRUCT_NAME, struct);
        }
    }

    public boolean isDisabled() {
        return this.bones.isEmpty();
    }

    private void preProcess(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        Iterator<PendingExpression> it = this.pendingExpressions.iterator();
        while (it.hasNext()) {
            PendingExpression next = it.next();
            if (next.executeBeforeAnimation) {
                postProcess(next, evaluator);
                it.remove();
            }
        }
    }

    private void postProcess(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        Iterator<PendingExpression> it = this.pendingExpressions.iterator();
        while (it.hasNext()) {
            PendingExpression next = it.next();
            if (!next.executeBeforeAnimation) {
                postProcess(next, evaluator);
                it.remove();
            }
        }
    }

    private void postProcess(PendingExpression value, ExpressionEvaluator<AnimationContext<?>> evaluator) {
        String result;
        try {
            Object ret;
            try {
                evaluator.entity().setIsClientSide(value.isClientPlayer);
                ret = value.IValue.evalSafe(evaluator);
            } catch (Throwable th) {
                ret = result = "Error: " + th.getMessage();
                evaluator.entity().setIsClientSide(false);
            }
            if (value.callback == null) {
                evaluator.entity().setIsClientSide(false);
                return;
            }
            result = ret == null ? "null" : ret instanceof String ? "'" + ret + "'" : ret.toString();
            evaluator.entity().setIsClientSide(false);
            value.callback.accept(result);
        } catch (Throwable th2) {
            evaluator.entity().setIsClientSide(false);
            throw th2;
        }
    }

    public synchronized void execute(IValue value, boolean isClientPlayer, boolean executeBeforeAnimation, @Nullable Consumer<String> resultConsumer) {
        var facts = com.micaftic.morpher.client.animation.molang.AnimationProgramAnalyzer.inspect(value);
        if (facts.unknownEffect() || !facts.writes().isEmpty() || !facts.physicsNodes().isEmpty()) {
            programExternalWrites = true;
            programs.values().forEach(com.micaftic.morpher.client.animation.molang.AnimationProgramRuntime::close);
            programs.clear();
        }
        this.pendingExpressions.add(new PendingExpression(value, isClientPlayer, executeBeforeAnimation, resultConsumer));
    }

    public IForeignVariableStorage getPublicVariableStorage() {
        return this.animationStorage;
    }

    public void forEachPropertyName(Consumer<String> consumer) {
        this.animationStorage.forEachPropertyName(consumer);
    }

    private record PendingExpression(IValue IValue, boolean isClientPlayer, boolean executeBeforeAnimation, Consumer<String> callback) {
    }
}
