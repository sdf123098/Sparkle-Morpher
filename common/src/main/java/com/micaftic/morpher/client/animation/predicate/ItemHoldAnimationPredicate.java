package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.condition.ConditionSwing;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.core.api.item.WeaponKind;
import com.micaftic.morpher.core.compat.ironsspellbooks.SpellbooksCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.config.ConfigPolicies;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.StringUtils;

public class ItemHoldAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    private static final int SWING_START_MARKER = 1;
    private static int swingDebugLogs;
    private static int swingEntryDebugLogs;

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity livingEntity = event.getAnimatable().getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        ModelAssembly modelAssembly = event.getAnimatable().getModelAssembly();
        PlayerModelBundle animationBundle = modelAssembly == null ? null : modelAssembly.getAnimationBundle();
        if (animationBundle == null || animationBundle.getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return HostPlayState.Decision.STOP;
        }
        HostPlayState.Decision playState = SpellbooksCompat.resolvePlayState(event, livingEntity);
        if (playState != null) {
            return playState;
        }
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        if (!livingEntity.isSleeping() && SlashBladeCompat.isSlashBladeItem(livingEntity.getItemInHand(InteractionHand.MAIN_HAND))) {
            if (event.getController().isPlaying()) {
                event.getController().stopTransition();
            }
            String str = SlashBladeCompat.getComboAnimName(event);
            if (StringUtils.isNoneBlank(str)) {
                if (event.getAnimatable().getAnimation(str) != null) {
                    return IAnimationPredicate.playAnimationWithValid(event, str, CodedLoopMode.PLAY_ONCE, i);
                }
                return HostPlayState.Decision.CONTINUE;
            }
        }
        boolean hasLocalSwingPulse = isLocalSwingTarget(event, livingEntity) && InputStateKey.isLocalAnyHandSwinging();
        debugSwingEntry(event, livingEntity, hasLocalSwingPulse);
        if ((InputStateKey.isAnyHandSwinging(livingEntity) || hasLocalSwingPulse) && !livingEntity.isSleeping()) {
            if (!shouldStartSwingAnimation(event, livingEntity, hasLocalSwingPulse)) {
                return HostPlayState.Decision.CONTINUE;
            }
            event.getController().stopTransition();
            ConditionManager conditionManager = event.getAnimatable().getModelConfig();
            InteractionHand swingingHand = hasLocalSwingPulse ? InputStateKey.getLocalSwingingHand() : InputStateKey.getSwingingHand(livingEntity);
            ConditionSwing conditionSwing = swingingHand == InteractionHand.MAIN_HAND ? conditionManager.getSwingMainhand() : conditionManager.getSwingOffhand();
            if (conditionSwing != null) {
                String str2 = conditionSwing.doTest(livingEntity, swingingHand);
                if (StringUtils.isNoneBlank(str2)) {
                    debugSwingSelection(event, livingEntity, swingingHand, str2, hasLocalSwingPulse ? "local-condition" : "condition");
                    applyLanceSwingTickOverride(event, livingEntity, swingingHand);
                    return IAnimationPredicate.playAnimationWithLoop(event, str2, CodedLoopMode.PLAY_ONCE);
                }
            }
            String fallback = getFallbackSwingAnimation(event, livingEntity, swingingHand);
            if (fallback != null) {
                debugSwingSelection(event, livingEntity, swingingHand, fallback, hasLocalSwingPulse ? "local-fallback" : "fallback");
                applyLanceSwingTickOverride(event, livingEntity, swingingHand);
                return IAnimationPredicate.playAnimationWithLoop(event, fallback, CodedLoopMode.PLAY_ONCE);
            }
            debugSwingSelection(event, livingEntity, swingingHand, "none", hasLocalSwingPulse ? "local-missing" : "missing");
        }
        return HostPlayState.Decision.CONTINUE;
    }

    private static boolean shouldStartSwingAnimation(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, boolean hasLocalSwingPulse) {
        if (hasLocalSwingPulse) {
            boolean pulseJustStarted = event.getAnimatable().getPositionTracker()
                    .markSwingPulseProcessed(InputStateKey.getLocalSwingPulseSequence());
            return pulseJustStarted
                    && event.getAnimatable().getPositionTracker().markProcessed(SWING_START_MARKER);
        }
        return entity.swingTime == 0
                && event.getAnimatable().getPositionTracker().markProcessed(SWING_START_MARKER);
    }

    private static boolean isLocalSwingTarget(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity) {
        return isLocalPlayerModel(event);
    }

    private static boolean isLocalPlayerModel(HostActionEvent<LivingAnimatable<?>> event) {
        if (event.getAnimatable() instanceof PlayerCapability cap && cap.isLocalPlayerModel()) {
            return true;
        }
        return InputStateKey.isLocalPlayerEntity(event.getAnimatable().getEntity());
    }

    private static void applyLanceSwingTickOverride(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, InteractionHand hand) {
        if (InputStateKey.isLocalPlayerEntity(entity) && isLanceLike(InnerClassify.getWeaponKind(entity.getItemInHand(hand)))) {
            event.getController().setAnimationTickOverride(InputStateKey.getSwingAnimationTicks(entity, event.getPartialTick()));
        }
    }

    private static boolean isLanceLike(WeaponKind kind) {
        return kind == WeaponKind.LANCE || kind == WeaponKind.SPEAR;
    }

    private static void debugSwingEntry(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, boolean hasLocalSwingPulse) {
        if (!ConfigPolicies.diagnostics().inputStateDebugLog()
                || InputStateKey.getLocalSwingPulseTicks() <= 0
                || swingEntryDebugLogs++ >= 80) {
            return;
        }
        boolean capLocal = event.getAnimatable() instanceof PlayerCapability cap && cap.isLocalPlayerModel();
        YesSteveModel.LOGGER.info("[SM-INPUT] swing-entry model={} animatable={} entityId={} entityClass={} isPlayer={} capLocal={} localEntity={} hasLocalPulse={} vanillaSwinging={} swingArm={} swingTime={} attackAnim={} localPulse={} localAge={}",
                event.getAnimatable().getModelId(),
                event.getAnimatable().getClass().getSimpleName(),
                entity.getId(),
                entity.getClass().getSimpleName(),
                entity instanceof Player,
                capLocal,
                InputStateKey.isLocalPlayerEntity(entity),
                hasLocalSwingPulse,
                entity.swinging,
                entity.swingingArm,
                entity.swingTime,
                entity.getAttackAnim(0.0f),
                InputStateKey.getLocalSwingPulseTicks(),
                InputStateKey.getLocalSwingPulseAge());
    }

    private static String getFallbackSwingAnimation(HostActionEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, InteractionHand swingingHand) {
        String defaultName = swingingHand == InteractionHand.MAIN_HAND ? "swing_hand" : "swing_offhand";
        if (event.getAnimatable().getAnimation(defaultName) != null) {
            return defaultName;
        }
        if (swingingHand == InteractionHand.MAIN_HAND && event.getAnimatable().getAnimation("attack_empty") != null) {
            return "attack_empty";
        }
        return null;
    }

    private static void debugSwingSelection(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, InteractionHand hand, String animation, String source) {
        if (!ConfigPolicies.diagnostics().inputStateDebugLog() || swingDebugLogs++ >= 120) {
            return;
        }
        YesSteveModel.LOGGER.info("[SM-INPUT] swing-predicate source={} animation={} hand={} entityId={} model={} item={} vanillaSwinging={} swingTime={} attackAnim={} localPulse={} hasAnimation={}",
                source,
                animation,
                hand,
                entity.getId(),
                event.getAnimatable().getModelId(),
                entity.getItemInHand(hand).getItem(),
                entity.swinging,
                entity.swingTime,
                entity.getAttackAnim(0.0f),
                InputStateKey.getLocalSwingPulseTicks(),
                !"none".equals(animation) && event.getAnimatable().getAnimation(animation) != null);
    }
}
