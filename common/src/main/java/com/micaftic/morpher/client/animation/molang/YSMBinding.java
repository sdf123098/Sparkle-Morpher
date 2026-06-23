package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import com.micaftic.morpher.client.animation.molang.functions.ysm.*;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.core.compat.curios.CuriosCompat;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.util.MathInterpolation;
import com.micaftic.morpher.mixin.client.ArrowEntityAccessor;
import com.micaftic.morpher.mixin.client.FishingHookAccessor;
import com.micaftic.morpher.mixin.client.ThrowableItemProjectileAccessor;
import com.micaftic.morpher.util.CameraUtil;
import com.micaftic.morpher.geckolib3.util.MovementQuery;
import com.micaftic.morpher.util.data.LazySupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.boat.ChestRaft;
import net.minecraft.world.entity.vehicle.boat.Raft;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.micaftic.morpher.core.architectury.platform.Platform;
import com.micaftic.morpher.core.api.attribute.ForgeAttributes;
import com.micaftic.morpher.core.api.item.LanceActionState;
import com.micaftic.morpher.core.api.item.MaceActionState;
import com.micaftic.morpher.core.api.item.TridentActionState;
import com.micaftic.morpher.core.api.item.WeaponActionBridge;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

public class YSMBinding extends ContextBinding {

    public static final LazySupplier<YSMBinding> INSTANCE = new LazySupplier<>(YSMBinding::new);
    private static final float BOAT_CHEST_BODY_Z_OFFSET = -0.15f * 16.0f;
    private static final float BOAT_DEFAULT_BODY_Y_OFFSET = 0.0f;
    // Rafts seat passengers 5 model units higher, while vanilla raft paddles render 1 unit lower than boat paddles.
    private static final float BOAT_RAFT_BODY_Y_OFFSET = -((0.8888889f - 1.0f / 3.0f) * 0.5625f * 16.0f + 1.0f);
    private static final float BOAT_DEFAULT_PADDLE_SCALE = 1.0f;
    private static final float BOAT_RAFT_PADDLE_SCALE = 1.0f;

    private YSMBinding() {
        function("dump_equipped_item", new DumpEquippedItem());
        function("dump_relative_block", new DumpRelativeBlock());
        var("dump_mods", YSMBinding::dumpMods);
        entityVar("dump_effects", YSMBinding::dumpEffects);
        entityVar("dump_biome", YSMBinding::dumpBiome);
        function("mod_version", new ModVersion());
        function("equipped_enchantment_level", new EquippedEnchantmentLevel());
        function("effect_level", new EffectLevel());

        function("relative_block_name", new RelativeBlockName());
        function("relative_block_name_any", new RelativeBlockNameAny());

        function("bone_rot", new BoneRotation());
        function("bone_pos", new BonePosition());
        function("bone_scale", new BoneScale());
        function("bone_pivot_abs", new BonePivotAbs());

        var("head_yaw", ctx -> ctx.data().netHeadYaw);
        var("head_pitch", ctx -> ctx.data().headPitch);

        var("weather", ctx -> getWeather(ctx.level()));
        var("dimension_name", ctx -> ctx.level().dimension().registry().toString());
        var("fps", ctx -> Minecraft.getInstance().getFps());
        var("time_delta", ctx -> MovementQuery.getTimeDeltaSeconds(ctx.geoInstance().getPositionTracker()));
        entityVar("ground_speed2", YSMBinding::getGroundSpeed2);

        entityVar("input_vertical", MathInterpolation::getYawInterpolation);
        entityVar("input_horizontal", MathInterpolation::getPitchInterpolation);

        entityVar("person_view", CameraUtil::getCameraType);
        entityVar("rendering_in_paperdoll", ctx -> ModelPreviewRenderer.isExtraPlayer());
        entityVar("rendering_in_inventory", CameraUtil::isThirdPerson);
        entityVar("block_light", ctx -> ctx.level().getBrightness(LightLayer.BLOCK, ctx.entity().blockPosition()));
        entityVar("sky_light", ctx -> ctx.level().getBrightness(LightLayer.SKY, ctx.entity().blockPosition()));
        entityVar("is_passenger", ctx -> ctx.entity().isPassenger());
        entityVar("is_sleep", ctx -> ctx.entity().getPose() == Pose.SLEEPING);
        entityVar("is_sneak", ctx -> ctx.entity().onGround() && ctx.entity().getPose() == Pose.CROUCHING);
        entityVar("biome_category", ctx -> getBiomeCategory(ctx.entity()));
        entityVar("is_open_air", ctx -> isOpenAir(ctx.entity()));
        entityVar("eye_in_water", ctx -> ctx.entity().isUnderWater());
        entityVar("frozen_ticks", ctx -> ctx.entity().getTicksFrozen());
        entityVar("air_supply", ctx -> ctx.entity().getAirSupply());
        entityVar("delta_movement_length", ctx -> MovementQuery.getDeltaMovementLength(ctx.entity(), ctx.geoInstance().getPositionTracker()));
        entityVar("boat_left_paddle", ctx -> getBoatPaddleState(ctx, AbstractBoat.PADDLE_LEFT));
        entityVar("boat_right_paddle", ctx -> getBoatPaddleState(ctx, AbstractBoat.PADDLE_RIGHT));
        entityVar("boat_left_rowing_time", ctx -> getBoatRowingTime(ctx, AbstractBoat.PADDLE_LEFT));
        entityVar("boat_right_rowing_time", ctx -> getBoatRowingTime(ctx, AbstractBoat.PADDLE_RIGHT));
        entityVar("boat_is_chest", YSMBinding::isBoatChest);
        entityVar("boat_is_raft", YSMBinding::isBoatRaft);
        entityVar("boat_body_offset_y", YSMBinding::getBoatBodyOffsetY);
        entityVar("boat_body_offset_z", YSMBinding::getBoatBodyOffsetZ);
        entityVar("boat_paddle_scale", YSMBinding::getBoatPaddleScale);
        entityVar("boat_chest_passenger_offset", YSMBinding::getBoatChestPassengerOffset);
        livingEntityVar("has_helmet", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.HEAD));
        livingEntityVar("has_chest_plate", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.CHEST));
        livingEntityVar("has_leggings", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.LEGS));
        livingEntityVar("has_boots", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.FEET));
        livingEntityVar("has_mainhand", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.MAINHAND));
        livingEntityVar("has_offhand", ctx -> hasEquipment(ctx.entity(), EquipmentSlot.OFFHAND));
        livingEntityVar("has_elytra", ctx -> !CosmeticArmorHelper.getElytraItem(ctx.entity()).isEmpty());
        livingEntityVar("is_riptide", ctx -> ctx.entity().isAutoSpinAttack());
        livingEntityVar("armor_value", ctx -> ctx.entity().getArmorValue());
        livingEntityVar("hurt_time", ctx -> ctx.entity().hurtTime);
        livingEntityVar("is_close_eyes", ctx -> isCloseEyes(ctx.animationEvent(), ctx.entity()));
        livingEntityVar("on_ladder", ctx -> ctx.entity().onClimbable());
        livingEntityVar("ladder_facing", new LadderFacing());
        livingEntityVar("arrow_count", ctx -> ctx.entity().getArrowCount());
        livingEntityVar("stinger_count", ctx -> ctx.entity().getStingerCount());
        livingEntityVar("entity_type", YSMBinding::getEntityTypeName);
        livingEntityVar("is_player", ctx -> "player".equals(getEntityTypeName(ctx)));
        livingEntityVar("is_maid", ctx -> "maid".equals(getEntityTypeName(ctx)));
        livingEntityVar("food_level", YSMBinding::getFoodLevel);

        livingEntityVar("xxa", YSMBinding::getXxa);
        livingEntityVar("yya", YSMBinding::getYya);
        livingEntityVar("zza", YSMBinding::getZza);

        livingEntityVar("mainhand_charged_crossbow", ctx -> isChargedCrossbow(ctx, InteractionHand.MAIN_HAND));
        livingEntityVar("offhand_charged_crossbow", ctx -> isChargedCrossbow(ctx, InteractionHand.OFF_HAND));
        livingEntityVar("weapon_type", ctx -> getWeaponType(ctx));
        livingEntityVar("weapon_is_trident", ctx -> getWeaponState(ctx).kind() == WeaponKind.TRIDENT);
        livingEntityVar("weapon_is_lance", ctx -> getWeaponState(ctx).kind() == WeaponKind.LANCE);
        livingEntityVar("weapon_is_spear", ctx -> getWeaponState(ctx).kind() == WeaponKind.SPEAR);
        livingEntityVar("weapon_is_mace", ctx -> getWeaponState(ctx).kind() == WeaponKind.MACE);
        livingEntityVar("weapon_attacking", ctx -> isWeaponAttacking(getWeaponState(ctx)));
        livingEntityVar("weapon_using", ctx -> isWeaponUsing(getWeaponState(ctx)));
        livingEntityVar("weapon_riding", ctx -> isWeaponRiding(getWeaponState(ctx)));
        livingEntityVar("weapon_fall_flying", ctx -> isWeaponFallFlying(getWeaponState(ctx)));
        livingEntityVar("weapon_speed", ctx -> getWeaponState(ctx).speed());
        livingEntityVar("weapon_attack_ticks", ctx -> getWeaponAttackTicks(getWeaponState(ctx)));
        livingEntityVar("weapon_use_ticks", ctx -> getWeaponUseTicks(getWeaponState(ctx)));
        livingEntityVar("trident_holding", ctx -> getWeaponState(ctx).trident().holding());
        livingEntityVar("trident_using", ctx -> getWeaponState(ctx).trident().using());
        livingEntityVar("trident_throwing", ctx -> getWeaponState(ctx).trident().throwing());
        livingEntityVar("trident_riptide", ctx -> getWeaponState(ctx).trident().riptide());
        livingEntityVar("trident_attack", ctx -> getWeaponState(ctx).trident().attacking());
        livingEntityVar("trident_use_ticks", ctx -> getWeaponState(ctx).trident().useTicks());
        livingEntityVar("trident_attack_ticks", ctx -> getWeaponState(ctx).trident().attackTicks());
        livingEntityVar("lance_holding", ctx -> getWeaponState(ctx).lance().holding());
        livingEntityVar("lance_using", ctx -> getWeaponState(ctx).lance().using());
        livingEntityVar("lance_charging", ctx -> getWeaponState(ctx).lance().charging());
        livingEntityVar("lance_jabbing", ctx -> getWeaponState(ctx).lance().jabbing());
        livingEntityVar("lance_lunging", ctx -> getWeaponState(ctx).lance().lunging());
        livingEntityVar("lance_riding", ctx -> getWeaponState(ctx).lance().riding());
        livingEntityVar("lance_riding_charge", ctx -> getWeaponState(ctx).lance().ridingCharge());
        livingEntityVar("lance_fall_flying", ctx -> getWeaponState(ctx).lance().fallFlying());
        livingEntityVar("lance_use_ticks", ctx -> getWeaponState(ctx).lance().useTicks());
        livingEntityVar("lance_attack_ticks", ctx -> getWeaponState(ctx).lance().attackTicks());
        livingEntityVar("lance_speed", ctx -> getWeaponState(ctx).lance().speed());
        livingEntityVar("lance_charge_progress", ctx -> getWeaponState(ctx).lance().chargeProgress());
        livingEntityVar("mace_holding", ctx -> getWeaponState(ctx).mace().holding());
        livingEntityVar("mace_falling", ctx -> getWeaponState(ctx).mace().falling());
        livingEntityVar("mace_can_smash", ctx -> getWeaponState(ctx).mace().canSmash());
        livingEntityVar("mace_smashing", ctx -> getWeaponState(ctx).mace().smashing());
        livingEntityVar("mace_wind_bursting", ctx -> getWeaponState(ctx).mace().windBursting());
        livingEntityVar("mace_attacking", ctx -> getWeaponState(ctx).mace().attacking());
        livingEntityVar("mace_riding", ctx -> getWeaponState(ctx).mace().riding());
        livingEntityVar("mace_fall_flying", ctx -> getWeaponState(ctx).mace().fallFlying());
        livingEntityVar("mace_fall_distance", ctx -> getWeaponState(ctx).mace().fallDistance());
        livingEntityVar("mace_vertical_speed", ctx -> getWeaponState(ctx).mace().verticalSpeed());
        livingEntityVar("mace_attack_ticks", ctx -> getWeaponState(ctx).mace().attackTicks());
        livingEntityVar("mace_smash_progress", ctx -> getWeaponState(ctx).mace().smashProgress());

        livingEntityVar("is_fishing", YSMBinding::isFishing);
        livingEntityVar("swinging", YSMBinding::isSwinging);
        livingEntityVar("swing_time", YSMBinding::getSwingTime);
        livingEntityVar("swinging_arm", ctx -> getSwingingHand(ctx) == InteractionHand.MAIN_HAND ? 0 : 1);
        livingEntityVar("attack_time", YSMBinding::getAttackTime);
        playerEntityVar("texture_name", new TextureName());
        playerEntityVar("first_person_mod_hide", new FirstPersonModHide());

        playerEntityVar("has_left_shoulder_parrot", ctx -> hasShoulderParrot(ctx.entity(), true));
        playerEntityVar("has_right_shoulder_parrot", ctx -> hasShoulderParrot(ctx.entity(), false));

        playerEntityVar("left_shoulder_parrot_variant", ctx -> getShoulderParrotVariant(ctx.entity(), true));
        playerEntityVar("right_shoulder_parrot_variant", ctx -> getShoulderParrotVariant(ctx.entity(), false));

        playerEntityVar("attack_damage", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_DAMAGE));
        playerEntityVar("attack_speed", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_SPEED));
        playerEntityVar("attack_knockback", ctx -> ctx.entity().getAttributeValue(Attributes.ATTACK_KNOCKBACK));

        playerEntityVar("movement_speed", ctx -> ctx.entity().getAttributeValue(Attributes.MOVEMENT_SPEED));
        playerEntityVar("knockback_resistance", ctx -> ctx.entity().getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        playerEntityVar("luck", ctx -> ctx.entity().getAttributeValue(Attributes.LUCK));
        playerEntityVar("block_reach", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.blockReach(), 4.5D));
        playerEntityVar("entity_reach", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.entityReach(), 3.0D));
        playerEntityVar("swim_speed", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.swimSpeed(), 1.0D));
        playerEntityVar("entity_gravity", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.entityGravity(), 0.08D));
        playerEntityVar("step_height_addition", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.stepHeightAddition(), 0.0D));
        playerEntityVar("nametag_distance", ctx -> ForgeAttributes.getValue(ctx.entity(), ForgeAttributes.nametagDistance(), 64.0D));
        playerEntityVar("in_shield_block_cooldown", YSMBinding::isInShieldBlockCooldown);

        livingEntityVar("elytra_rot_x", YSMBinding::getElytraRotX);
        livingEntityVar("elytra_rot_y", YSMBinding::getElytraRotY);
        livingEntityVar("elytra_rot_z", YSMBinding::getElytraRotZ);

        localPlayerEntityVar("hit_target_id", YSMBinding::getHitTargetId);
        localPlayerEntityVar("hit_target_type", YSMBinding::getHitTargetType);

        function("first_order", new FirstOrderFunction());
        function("second_order", new SecondOrderFunction())
        ;
        function("particle", new Particle(false));
        function("abs_particle", new Particle(true));

        function("perlin_noise", new PerlinNoise());

        function("play_sound", new SoundFunction.PlaySoundFunction());
        function("stop_sound", new SoundFunction.StopSoundFunction());
        function("stop_all_sounds", new SoundFunction.StopAllSoundsFunction());

        function("keyboard", new InputKeyDetectionFunction.Keyboard());
        function("mouse", new InputKeyDetectionFunction.Mouse());
        function(MolangEventDispatcher.SYNC, new Sync());
        function(MolangEventDispatcher.DEFER, new Defer());
        projectileEntityVar("projectile_owner", ctx -> ctx.createChild(ctx.entity().getOwner()));
        throwableProjectileEntityVar("throwable_item", YSMBinding::getThrowableItemId);
        fishHookEntityVar("hooked_in", YSMBinding::getHookedEntityType);
        fishHookEntityVar("is_biting", ctx -> ((FishingHookAccessor) ctx.entity()).isBiting());
        abstractArrowEntityVar("on_ground_time", ctx -> ((ProjectileStateAccessor) ctx.entity()).ysm$getInGroundTime());
        abstractArrowEntityVar("in_ground", ctx -> ((ProjectileStateAccessor) ctx.entity()).ysm$isInGround());
        abstractArrowEntityVar("is_spectral_arrow", ctx -> ctx.entity() instanceof SpectralArrow);
        abstractArrowEntityVar("shoot_item_id", ctx -> ((ProjectileStateAccessor) ctx.entity()).ysm$getOwnerItemId());
        CuriosCompat.registerCuriosItems(this);
    }

    private static String getHitTargetId(IContext<LocalPlayer> context) {
        ClientLevel clientLevel;
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() == HitResult.Type.MISS || (clientLevel = Minecraft.getInstance().level) == null) {
                return StringPool.EMPTY;
            }
            Identifier key = BuiltInRegistries.BLOCK.getKey(clientLevel.getBlockState(blockHitResult.getBlockPos()).getBlock());
            if (key != null) {
                return key.toString();
            }
            return StringPool.EMPTY;
        }
        if (hitResult instanceof EntityHitResult) {
            Identifier key2 = BuiltInRegistries.ENTITY_TYPE.getKey(((EntityHitResult) hitResult).getEntity().getType());
            if (key2 != null) {
                return key2.toString();
            }
            return StringPool.EMPTY;
        }
        return StringPool.EMPTY;
    }

    private static String getHitTargetType(IContext<LocalPlayer> context) {
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (hitResult == null) {
            return StringPool.EMPTY;
        }
        switch (hitResult.getType()) {
            case BLOCK:
                break;
            case ENTITY:
                break;
        }
        return StringPool.EMPTY;
    }

    private static String getHookedEntityType(IContext<FishingHook> context) {
        Identifier key;
        Entity entity = ((FishingHookAccessor) context.entity()).getHookedIn();
        if (entity != null && (key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) != null) {
            return key.toString();
        }
        return StringPool.EMPTY;
    }

    private static String getThrowableItemId(IContext<ThrowableItemProjectile> context) {
        ThrowableItemProjectile throwableItemProjectile = context.entity();
        if (throwableItemProjectile instanceof ThrowableItemProjectileAccessor accessor) {
            Identifier key = BuiltInRegistries.ITEM.getKey(accessor.invokeGetDefaultItem());
            if (key != null) {
                return key.toString();
            }
            return StringPool.EMPTY;
        }
        return StringPool.EMPTY;
    }

    private static float getGroundSpeed2(IContext<Entity> context) {
        return MovementQuery.getGroundSpeed(context.entity(), context.geoInstance().getPositionTracker(), context.animationEvent());
    }

    private static boolean getBoatPaddleState(IContext<Entity> context, int paddle) {
        AbstractBoat boat = getBoat(context.entity());
        return boat != null && boat.getPaddleState(paddle);
    }

    private static float getBoatRowingTime(IContext<Entity> context, int paddle) {
        AbstractBoat boat = getBoat(context.entity());
        if (boat == null) {
            return 0.0f;
        }
        return -boat.getRowingTime(paddle, context.animationEvent().getPartialTick());
    }

    private static double getElytraRotX(IContext<LivingEntity> context) {
        return Math.toDegrees(context.entity().elytraAnimationState.getRotX(context.animationEvent().getPartialTick()));
    }

    private static double getElytraRotY(IContext<LivingEntity> context) {
        return Math.toDegrees(context.entity().elytraAnimationState.getRotY(context.animationEvent().getPartialTick()));
    }

    private static double getElytraRotZ(IContext<LivingEntity> context) {
        return Math.toDegrees(context.entity().elytraAnimationState.getRotZ(context.animationEvent().getPartialTick()));
    }

    private static boolean isBoatChest(IContext<Entity> context) {
        return getBoat(context.entity()) instanceof AbstractChestBoat;
    }

    private static boolean isBoatRaft(IContext<Entity> context) {
        AbstractBoat boat = getBoat(context.entity());
        return boat instanceof Raft || boat instanceof ChestRaft;
    }

    private static float getBoatBodyOffsetY(IContext<Entity> context) {
        AbstractBoat boat = getBoat(context.entity());
        return boat instanceof Raft || boat instanceof ChestRaft ? BOAT_RAFT_BODY_Y_OFFSET : BOAT_DEFAULT_BODY_Y_OFFSET;
    }

    private static float getBoatBodyOffsetZ(IContext<Entity> context) {
        AbstractBoat boat = getBoat(context.entity());
        if (boat instanceof AbstractChestBoat) {
            return BOAT_CHEST_BODY_Z_OFFSET;
        }
        return 0.0f;
    }

    private static float getBoatPaddleScale(IContext<Entity> context) {
        AbstractBoat boat = getBoat(context.entity());
        return boat instanceof Raft || boat instanceof ChestRaft ? BOAT_RAFT_PADDLE_SCALE : BOAT_DEFAULT_PADDLE_SCALE;
    }

    private static float getBoatChestPassengerOffset(IContext<Entity> context) {
        return -getBoatBodyOffsetZ(context);
    }

    private static AbstractBoat getBoat(Entity entity) {
        if (entity instanceof AbstractBoat boat) {
            return boat;
        }
        Entity vehicle = entity.getVehicle();
        return vehicle instanceof AbstractBoat boat ? boat : null;
    }

    private static float getXxa(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getStrafeInput();
            }
        }
        return context.entity().xxa;
    }

    private static float getYya(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getVerticalInput();
            }
        }
        return context.entity().yya;
    }

    private static float getZza(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return playerCapability.getPositionTracker().getForwardInput();
            }
        }
        return context.entity().zza;
    }

    private static boolean isInShieldBlockCooldown(IContext<Player> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability) {
            return ((PlayerCapability) abstractC0235x5da32a01Mo322x83eb685f).getPositionTracker().isShieldBlocking();
        }
        return false;
    }

    private static boolean isFishing(IContext<LivingEntity> context) {
        LivingEntity livingEntity = context.entity();
        if (livingEntity instanceof Player) {
            return ((Player) livingEntity).fishing != null;
        }
        return TouhouLittleMaidCompat.isMaidSitting(livingEntity);
    }

    private static boolean isChargedCrossbow(IContext<LivingEntity> context, InteractionHand interactionHand) {
        ItemStack itemInHand = context.entity().getItemInHand(interactionHand);
        return itemInHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemInHand);
    }

    private static WeaponActionState getWeaponState(IContext<LivingEntity> context) {
        return WeaponActionBridge.get(context.entity(), context.animationEvent().getPartialTick());
    }

    private static int getWeaponType(IContext<LivingEntity> context) {
        return switch (getWeaponState(context).kind()) {
            case TRIDENT -> 1;
            case LANCE -> 2;
            case MACE -> 3;
            case SPEAR -> 4;
            case NONE -> 0;
        };
    }

    private static boolean isWeaponAttacking(WeaponActionState state) {
        return state.trident().attacking() || state.lance().jabbing() || state.lance().lunging() || state.mace().attacking();
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

    private static InteractionHand getSwingingHand(IContext<LivingEntity> context) {
        if (isLocalSwingTarget(context) && InputStateKey.getLocalSwingPulseTicks() > 0) {
            return InputStateKey.getLocalSwingingHand();
        }
        return InputStateKey.getSwingingHand(context.entity());
    }

    private static float getAttackTime(IContext<LivingEntity> context) {
        if (isLocalSwingTarget(context) && InputStateKey.getLocalSwingPulseTicks() > 0
                && InputStateKey.getLocalSwingingHand() == InteractionHand.MAIN_HAND) {
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

    private static boolean isWeaponUsing(WeaponActionState state) {
        return state.trident().using() || state.lance().using();
    }

    private static boolean isWeaponRiding(WeaponActionState state) {
        return state.lance().riding() || state.mace().riding();
    }

    private static boolean isWeaponFallFlying(WeaponActionState state) {
        return state.lance().fallFlying() || state.mace().fallFlying();
    }

    private static float getWeaponAttackTicks(WeaponActionState state) {
        TridentActionState trident = state.trident();
        LanceActionState lance = state.lance();
        MaceActionState mace = state.mace();
        return Math.max(trident.attackTicks(), Math.max(lance.attackTicks(), mace.attackTicks()));
    }

    private static float getWeaponUseTicks(WeaponActionState state) {
        return Math.max(state.trident().useTicks(), state.lance().useTicks());
    }

    private static String getEntityTypeName(IContext<LivingEntity> context) {
        LivingEntity livingEntityMo327xaffeef43 = context.entity();
        if (livingEntityMo327xaffeef43 instanceof Player) {
            return "player";
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntityMo327xaffeef43.getType());
        if (key == null) {
            return StringPool.EMPTY;
        }
        if ("touhou_little_maid".equals(key.getNamespace()) && "maid".equals(key.getPath())) {
            return "maid";
        }
        return key.toString();
    }

    private static Object getFoodLevel(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isLocalPlayerModel()) {
                return Integer.valueOf(playerCapability.getPositionTracker().getFoodLevel());
            }
        }
        LivingEntity livingEntity = context.entity();
        if (livingEntity instanceof Player) {
            return Integer.valueOf(((Player) livingEntity).getFoodData().getFoodLevel());
        }
        return 20;
    }

    private static boolean isCloseEyes(AnimationEvent<?> event, LivingEntity livingEntity) {
        float blinkPhase = (event.getCurrentTick() + (Math.abs(livingEntity.getUUID().getLeastSignificantBits()) % 10)) % 90.0f;
        return livingEntity.isSleeping() || ((85.0f > blinkPhase ? 1 : (85.0f == blinkPhase ? 0 : -1)) < 0 && (blinkPhase > 90.0f ? 1 : (blinkPhase == 90.0f ? 0 : -1)) < 0);
    }

    private static boolean hasEquipment(LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
        return !CosmeticArmorHelper.getArmorItem(livingEntity, equipmentSlot).isEmpty();
    }

    private static int getWeather(ClientLevel clientLevel) {
        if (clientLevel.isThundering()) {
            return 2;
        }
        if (clientLevel.isRaining()) {
            return 1;
        }
        return 0;
    }

    @Deprecated
    private String getBiomeCategory(Entity entity) {
        return null;
    }

    private static Object dumpMods(IContext<?> context) {
        if (!context.isDebugMode()) {
            return null;
        }
        Platform.getMods().stream().sorted(Comparator.comparing(mod -> mod.getName())).forEach(mod -> {
            context.logWarningComponent(Component.literal("Mod: display ").append(ComponentUtils.copyOnClickText(mod.getName())).append(Component.literal("  id ").append(ComponentUtils.copyOnClickText(mod.getModId()))));
        });
        return null;
    }

    private static Object dumpEffects(IContext<Entity> context) {
        Collection<MobEffectInstance> activeEffects;
        if (!context.isDebugMode()) {
            return null;
        }
        if (context.entity() instanceof net.minecraft.world.entity.projectile.arrow.Arrow) {
            activeEffects = ((ArrowEntityAccessor) (Object) context.entity()).getEffects();
        } else if (context.entity() instanceof LivingEntity) {
            activeEffects = ((LivingEntity) context.entity()).getActiveEffects();
        } else {
            return null;
        }
        for (MobEffectInstance mobEffectInstance : activeEffects) {
            context.logWarningComponent(Component.literal("Effect: display ").append(ComponentUtils.copyOnClickText(mobEffectInstance.getEffect().value().getDisplayName().getString(99))).append(Component.literal("  name ").append(ComponentUtils.copyOnClickText(BuiltInRegistries.MOB_EFFECT.getKey(mobEffectInstance.getEffect().value()).toString()))).append("  lv=").append(String.valueOf(mobEffectInstance.getAmplifier() + 1)));
        }
        return null;
    }

    private static Object dumpBiome(IContext<Entity> context) {
        if (!context.isDebugMode()) {
            return null;
        }
        Holder<Biome> biome = context.entity().level().getBiome(context.entity().blockPosition());
        biome.unwrapKey().ifPresent(resourceKey -> {
            context.logWarningComponent(Component.literal("Name ").append(ComponentUtils.copyOnClickText(resourceKey.registry().toString())));
        });
        biome.tags().forEach(tagKey -> {
            context.logWarningComponent(Component.literal("Tag ").append(ComponentUtils.copyOnClickText(tagKey.registry().toString())));
        });
        return null;
    }

    private static boolean isOpenAir(Entity entity) {
        BlockPos blockPosBlockPosition = entity.blockPosition();
        return entity.level().canSeeSky(blockPosBlockPosition) && entity.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPosBlockPosition).getY() <= blockPosBlockPosition.getY();
    }

    public static String getShoulderParrotVariant(Player player, boolean leftShoulder) {
        Parrot.Variant variant = (leftShoulder ? player.getShoulderParrotLeft() : player.getShoulderParrotRight()).orElse(null);
        return variant != null ? variant.name().toLowerCase(Locale.ENGLISH) : "empty";
    }

    private static boolean hasShoulderParrot(Player player, boolean leftShoulder) {
        return (leftShoulder ? player.getShoulderParrotLeft() : player.getShoulderParrotRight()).isPresent();
    }
}
