package com.micaftic.morpher.geckolib3.core.processor;

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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class AnimationProcessor<TEntity extends Entity> {

    private static final int ROAMING_STRUCT_NAME = StringPool.computeIfAbsent("roaming");

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

    public boolean isRotationActive(int boneId) {
        BoneTopLevelSnapshot snapshot = this.boneById.get(boneId);
        return snapshot != null && snapshot.isCurrentlyRunningRotationAnimation;
    }

    public AnimationProcessor(AnimatableEntity<TEntity> animatable) {
        this.animatable = animatable;
    }

    public void tickAnimation(AnimationEvent<AnimatableEntity<TEntity>> event, AnimationContext<?> context, boolean z, boolean z2) {
        context.setStorage(this.animationStorage);
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
        AnimationData manager = this.animatable.getAnimationData();
        this.currentEvaluator = evaluator;
        this.currentSeekTime = seekTime;
        for (IAnimationController controller : manager.getAnimationControllers()) {
            if (this.needsInit) {
                controller.init(this.bones, this.initExpressions);
            }
            if (z) {
                controller.process(event, evaluator, z2);
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
        }

        TransitionVector3f pos;
        PhysicsManager.pushScope(snapshot.boneId);
        try {
            pos = provider.getPosition(evaluator);
        } finally {
            PhysicsManager.popScope();
        }
        if (pos != null) {
            if (!snapshot.isCurrentlyRunningPositionAnimation) {
                snapshot.isCurrentlyRunningPositionAnimation = true;
                snapshot.position.set(0.0f, 0.0f, 0.0f);
            }
            snapshot.mostRecentResetPositionTick = seekTime;
            pos.applyLinearBlendTo(snapshot.position);
        }

        TransitionVector3f scale;
        PhysicsManager.pushScope(snapshot.boneId);
        try {
            scale = provider.getScale(evaluator);
        } finally {
            PhysicsManager.popScope();
        }
        if (scale != null) {
            if (!snapshot.isCurrentlyRunningScaleAnimation) {
                snapshot.isCurrentlyRunningScaleAnimation = true;
                snapshot.scale.set(1.0f, 1.0f, 1.0f);
            }
            snapshot.mostRecentResetScaleTick = seekTime;
            scale.applyLinearBlendTo(snapshot.scale);
        }
    }

    @Nullable
    public IBone getBone(int i) {
        BoneTopLevelSnapshot renderer = this.boneById.get(i);
        return renderer != null ? renderer.bone : null;
    }

    public int getBoneCount() {
        return this.bones.size();
    }

    public void reset() {
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

    public void execute(IValue value, boolean isClientPlayer, boolean executeBeforeAnimation, @Nullable Consumer<String> resultConsumer) {
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
