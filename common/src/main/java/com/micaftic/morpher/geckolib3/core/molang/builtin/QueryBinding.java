package com.micaftic.morpher.geckolib3.core.molang.builtin;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.core.controller.AnimationControllerContext;
import com.micaftic.morpher.audio.PlaybackFlags;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.client.entity.PlayerEntityFrameState;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import com.micaftic.morpher.geckolib3.core.molang.builtin.query.*;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.util.MovementQuery;
import com.micaftic.morpher.util.CameraUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class QueryBinding extends ContextBinding {

    public static final QueryBinding INSTANCE = new QueryBinding();

    private QueryBinding() {
        function("debug_output", new DebugOut());
        function("biome_has_all_tags", new BiomeHasAllTags());
        function("biome_has_any_tag", new BiomeHasAnyTag());
        function("relative_block_has_all_tags", new RelativeBlockHasAllTags());
        function("relative_block_has_any_tag", new RelativeBlockHasAnyTag());
        function("is_item_name_any", new IsItemNameAny());
        function("equipped_item_all_tags", new EquippedItemAllTags());
        function("equipped_item_any_tag", new EquipmentItemAnyTag());
        function("position", new Position());
        function("position_delta", new PositionDelta());
        function("rotation_to_camera", new RotationToCamera());

        function("max_durability", new MaxDurability());
        function("remaining_durability", new RemainingDurability());

        var("actor_count", ctx -> ctx.level().getEntityCount());

        var("anim_time", ctx -> animationControllerContext(ctx).map(AnimationControllerContext::animTime).orElse(0.0f));
        var("all_animations_finished", ctx -> getPlaybackFlags(ctx).map(PlaybackFlags::isPaused).orElse(false));
        var("any_animation_finished", ctx -> getPlaybackFlags(ctx).map(PlaybackFlags::isStopped).orElse(false));
        var("life_time", ctx -> ctx.geoInstance().getSeekTime() / 20.0d);
        var("head_x_rotation", ctx -> ctx.data().netHeadYaw);
        var("head_y_rotation", ctx -> ctx.data().headPitch);
        var("moon_phase", ctx -> getMoonPhase(ctx.level().getDefaultClockTime()));
        var("time_of_day", ctx -> MolangUtils.normalizeTime(ctx.level().getDefaultClockTime()));
        var("time_stamp", ctx -> ctx.level().getDefaultClockTime());
        var("delta_time", ctx -> MovementQuery.getTimeDeltaSeconds(ctx.geoInstance().getPositionTracker()));

        entityVar("yaw_speed", QueryBinding::getYawSpeed);
        entityVar("cardinal_facing_2d", ctx -> ctx.entity().getDirection().get3DDataValue());
        entityVar("distance_from_camera", QueryBinding::getDistanceFromCamera);
        entityVar("eye_target_x_rotation", ctx -> ctx.entity().getViewXRot(ctx.animationEvent().getPartialTick()));
        entityVar("eye_target_y_rotation", ctx -> ctx.entity().getViewYRot(ctx.animationEvent().getPartialTick()));
        entityVar("ground_speed", ctx -> MovementQuery.getGroundSpeed(ctx.entity(), ctx.geoInstance().getPositionTracker(), ctx.animationEvent()));
        entityVar("modified_distance_moved", ctx -> ctx.entity().moveDist);
        entityVar("vertical_speed", QueryBinding::getVerticalSpeed);
        entityVar("walk_distance", ctx -> ctx.entity().moveDist);
        entityVar("has_rider", ctx -> ctx.entity().isVehicle());
        entityVar("is_first_person", ctx -> CameraUtil.getCameraType(ctx) == CameraType.FIRST_PERSON.ordinal());
        entityVar("is_in_water", ctx -> ctx.entity().isInWater());
        entityVar("is_in_water_or_rain", ctx -> ctx.entity().isInWater() || ctx.entity().level().isRainingAt(ctx.entity().blockPosition()));
        entityVar("is_on_fire", ctx -> ctx.entity().isOnFire());
        entityVar("is_on_ground", ctx -> ctx.entity().onGround());
        entityVar("is_riding", ctx -> ctx.entity().isPassenger());
        entityVar("is_sneaking", ctx -> ctx.entity().onGround() && ctx.entity().getPose() == Pose.CROUCHING);
        entityVar("is_spectator", ctx -> ctx.entity().isSpectator());
        entityVar("is_sprinting", ctx -> ctx.entity().isSprinting());
        entityVar("is_swimming", ctx -> ctx.entity().isSwimming());

        livingEntityVar("body_x_rotation", ctx -> Mth.lerp(ctx.animationEvent().getFrameTime(), ctx.entity().xRotO, ctx.entity().getXRot()));
        livingEntityVar("body_y_rotation", ctx -> Mth.wrapDegrees(Mth.lerp(ctx.animationEvent().getPartialTick(), ctx.entity().yBodyRotO, ctx.entity().yBodyRot)));
        livingEntityVar("health", QueryBinding::getHealth);
        livingEntityVar("max_health", QueryBinding::getMaxHealth);
        livingEntityVar("hurt_time", ctx -> ctx.entity().hurtTime);
        livingEntityVar("is_eating", ctx -> ctx.entity().getUseItem().getUseAnimation() == ItemUseAnimation.EAT);
        livingEntityVar("is_playing_dead", ctx -> ctx.entity().isDeadOrDying());
        livingEntityVar("is_sleeping", ctx -> ctx.entity().isSleeping());
        livingEntityVar("is_using_item", ctx -> InputStateKey.isUsingItem(ctx.entity(), InputStateKey.getUsedItemHand(ctx.entity())));
        livingEntityVar("item_in_use_duration", ctx -> InputStateKey.getTicksUsingItem(ctx.entity()) / 20.0d);
        livingEntityVar("item_max_use_duration", ctx -> getItemMaxUseDuration(ctx.entity()) / 20.0d);
        livingEntityVar("item_remaining_use_duration", ctx -> getItemRemainingUseDuration(ctx.entity()) / 20.0d);
        livingEntityVar("is_swinging", QueryBinding::isSwinging);
        livingEntityVar("swing_time", ctx -> getSwingTime(ctx) / 20.0d);
        livingEntityVar("attack_time", QueryBinding::getAttackTime);
        livingEntityVar("equipment_count", ctx -> getEquipmentCount(ctx.entity()));

        playerEntityVar("cape_flap_amount", QueryBinding::getCapeFlapAmount);
        playerEntityVar("player_level", QueryBinding::getPlayerLevel);
        playerEntityVar("is_jumping", ctx -> !isFlying(ctx) && !ctx.entity().isPassenger() && !ctx.entity().onGround() && !ctx.entity().isInWater());

        clientPlayerEntityVar("has_cape", ctx -> hasCape(ctx.entity()));
    }

    private static Optional<AnimationControllerContext> animationControllerContext(IContext<?> context) {
        return Optional.ofNullable(context.animationControllerContext());
    }

    private static Optional<PlaybackFlags> getPlaybackFlags(IContext<?> context) {
        return Optional.ofNullable(context.getPlaybackFlags());
    }

    private static int getItemRemainingUseDuration(LivingEntity entity) {
        if (entity.isUsingItem()) {
            return entity.getUseItemRemainingTicks();
        }
        return InputStateKey.isUsingItem(entity, InputStateKey.getUsedItemHand(entity)) ? Math.max(1, 6 - InputStateKey.getTicksUsingItem(entity)) : 0;
    }

    private static boolean isSwinging(IContext<LivingEntity> context) {
        if (isLocalSwingTarget(context) && InputStateKey.isLocalAnyHandSwinging()) {
            return true;
        }
        return InputStateKey.isAnyHandSwinging(context.entity());
    }

    private static float getSwingTime(IContext<LivingEntity> context) {
        if (isLocalSwingTarget(context) && InputStateKey.getLocalSwingPulseTicks() > 0) {
            return Math.max(1.0f, InputStateKey.getLocalSwingPulseAge() + context.animationEvent().getFrameTime());
        }
        return InputStateKey.getSwingTicks(context.entity(), context.animationEvent().getFrameTime());
    }

    private static float getAttackTime(IContext<LivingEntity> context) {
        if (isLocalSwingTarget(context) && InputStateKey.getLocalSwingPulseTicks() > 0
                && InputStateKey.getLocalSwingingHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            return Math.min(1.0f, getSwingTime(context) / 6.0f);
        }
        return InputStateKey.getAttackProgress(context.entity(), context.animationEvent().getFrameTime());
    }

    private static boolean isLocalPlayerModel(IContext<? extends LivingEntity> context) {
        return context.geoInstance() instanceof PlayerCapability cap && cap.isLocalPlayerModel();
    }

    private static boolean isLocalSwingTarget(IContext<? extends LivingEntity> context) {
        return isLocalPlayerModel(context) || context.entity() instanceof Player;
    }

    private static int getMoonPhase(long dayTime) {
        return (int) Math.floorMod(dayTime / 24000L, 8L);
    }

    private static boolean isFlying(IContext<Player> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().isFlying();
            }
        }
        return context.entity().getAbilities().flying;
    }

    private static int getPlayerLevel(IContext<Player> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getExperienceLevel();
            }
        }
        return context.entity().experienceLevel;
    }

    private static Object getHealth(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getHealth();
            }
        }
        return context.entity().getHealth();
    }

    private static Object getMaxHealth(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getMaxHealth();
            }
        }
        return context.entity().getMaxHealth();
    }

    private static boolean hasCape(AbstractClientPlayer abstractClientPlayer) {
        return abstractClientPlayer.getSkin().cape() != null && !abstractClientPlayer.isInvisible() && abstractClientPlayer.isModelPartShown(PlayerModelPart.CAPE);
    }

    private static int getEquipmentCount(LivingEntity entity) {
        int i = 0;
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot.isArmor() && !CosmeticArmorHelper.getArmorItem(entity, equipmentSlot).isEmpty()) {
                i++;
            }
        }
        return i;
    }

    private static int getItemMaxUseDuration(LivingEntity entity) {
        ItemStack useItem = entity.getUseItem();
        if (useItem.isEmpty()) {
            return 0;
        }
        return useItem.getUseDuration(entity);
    }

    private static float getYawSpeed(IContext<Entity> context) {
        if (context.entity() instanceof LocalPlayer) {
            return PlayerEntityFrameState.getHeadYawDelta();
        }
        return 20.0f * Mth.wrapDegrees(context.entity().getYRot() - context.entity().yRotO);
    }

    private static float getDistanceFromCamera(IContext<Entity> context) {
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera().position();
        return Mth.sqrt((float) context.entity().distanceToSqr(cameraPosition));
    }

    private static float getVerticalSpeed(IContext<Entity> context) {
        return MovementQuery.getVerticalSpeed(context.entity(), context.geoInstance().getPositionTracker());
    }

    private static float getCapeFlapAmount(IContext<Player> context) {
        float gameTime = context.animationEvent().getFrameTime();
        Player player = context.entity();
        float fLerp = (float) (Mth.lerp(gameTime, 0.0d, 0.0d) - Mth.lerp(gameTime, player.xo, player.getX())); // MC 26.x: cloak fields removed
        float fLerp2 = (float) (Mth.lerp(gameTime, 0.0d, 0.0d) - Mth.lerp(gameTime, player.yo, player.getY()));
        float fLerp3 = (float) (Mth.lerp(gameTime, 0.0d, 0.0d) - Mth.lerp(gameTime, player.zo, player.getZ()));
        float f = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO);
        float fSin = Mth.sin(f * 0.017453292f);
        float f2 = -Mth.cos(f * 0.017453292f);
        float fClamp = Mth.clamp(fLerp2 * 10.0f, -6.0f, 32.0f);
        float fClamp2 = Mth.clamp(((fLerp * fSin) + (fLerp3 * f2)) * 100.0f, 0.0f, 150.0f);
        if (fClamp2 < 0.0f) {
            fClamp2 = 0.0f;
        }
        float fSin2 = fClamp;
        if (player.isCrouching()) {
            fSin2 += 25.0f;
        }
        return Mth.clamp(((6.0f + (fClamp2 / 2.0f)) + fSin2) / 108.0f, 0.0f, 1.0f);
    }
}
