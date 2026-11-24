package com.micaftic.morpher.client.core;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioPlayerManager;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.ControllerActionResolver;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.animation.controller.UnifiedPlayerActionController;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.animation.particle.AnimationParticleLocator;
import com.micaftic.morpher.client.compat.touhoulittlemaid.TouhouLittleMaidClientCompat;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.PlayerEntityFrameState;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.render.BoneIds;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.core.api.attribute.ForgeAttributes;
import com.micaftic.morpher.core.api.item.LanceActionState;
import com.micaftic.morpher.core.api.item.MaceActionState;
import com.micaftic.morpher.core.api.item.TridentActionState;
import com.micaftic.morpher.core.api.item.WeaponActionBridge;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;
import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.core.architectury.platform.Platform;
import com.micaftic.morpher.core.compat.bettercombat.BetterCombatCompat;
import com.micaftic.morpher.core.compat.carryon.CarryOnCompat;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.core.compat.create.CreateCompat;
import com.micaftic.morpher.core.compat.curios.CuriosCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.core.compat.immersivemelodies.ImmersiveMelodiesCompat;
import com.micaftic.morpher.core.compat.ironsspellbooks.SpellbooksCompat;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.core.compat.sbackpack.SBackpackCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.swem.SWEMCompat;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.mixin.client.ArrowEntityAccessor;
import com.micaftic.morpher.mixin.client.FishingHookAccessor;
import com.micaftic.morpher.mixin.client.ThrowableItemProjectileAccessor;
import com.micaftic.morpher.util.CameraUtil;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.ParticleEffectUtil;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import dev.spm.core.scene.CodedControllerCommand;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.SceneInputs;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.boat.ChestRaft;
import net.minecraft.world.entity.vehicle.boat.Raft;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Samples immutable {@link SceneInputs} for {@code spm-core} from a live {@link HostActionEvent}
 * and resolves lazy {@link MolangBindings.Call} queries/functions on demand.
 */
public final class HostMolangBindings {

    private static final float BOAT_CHEST_BODY_Z_OFFSET = -0.15f * 16.0f;
    private static final float BOAT_DEFAULT_BODY_Y_OFFSET = 0.0f;
    private static final float BOAT_RAFT_BODY_Y_OFFSET = -((0.8888889f - 1.0f / 3.0f) * 0.5625f * 16.0f + 1.0f);
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[][] CTRL_STATE_ENTRIES = {
            {ControllerActionResolver.DEATH, "ctrl." + ControllerActionResolver.DEATH},
            {ControllerActionResolver.RIPTIDE, "ctrl." + ControllerActionResolver.RIPTIDE},
            {ControllerActionResolver.SLEEP, "ctrl." + ControllerActionResolver.SLEEP},
            {ControllerActionResolver.SWIM, "ctrl." + ControllerActionResolver.SWIM},
            {ControllerActionResolver.CLIMB, "ctrl." + ControllerActionResolver.CLIMB},
            {ControllerActionResolver.CLIMBING, "ctrl." + ControllerActionResolver.CLIMBING},
            {ControllerActionResolver.LADDER_UP, "ctrl." + ControllerActionResolver.LADDER_UP},
            {ControllerActionResolver.LADDER_STILLNESS, "ctrl." + ControllerActionResolver.LADDER_STILLNESS},
            {ControllerActionResolver.LADDER_DOWN, "ctrl." + ControllerActionResolver.LADDER_DOWN},
            {ControllerActionResolver.FLY, "ctrl." + ControllerActionResolver.FLY},
            {ControllerActionResolver.ELYTRA_FLY, "ctrl." + ControllerActionResolver.ELYTRA_FLY},
            {ControllerActionResolver.SWIM_STAND, "ctrl." + ControllerActionResolver.SWIM_STAND},
            {ControllerActionResolver.ATTACKED, "ctrl." + ControllerActionResolver.ATTACKED},
            {ControllerActionResolver.JUMP, "ctrl." + ControllerActionResolver.JUMP},
            {ControllerActionResolver.SNEAK, "ctrl." + ControllerActionResolver.SNEAK},
            {ControllerActionResolver.SNEAKING, "ctrl." + ControllerActionResolver.SNEAKING},
            {ControllerActionResolver.RUN, "ctrl." + ControllerActionResolver.RUN},
            {ControllerActionResolver.WALK, "ctrl." + ControllerActionResolver.WALK},
            {ControllerActionResolver.IDLE, "ctrl." + ControllerActionResolver.IDLE}
    };
    private static final Map<String, EquipmentSlot> SLOT_MAP = Map.of(
            "chest", EquipmentSlot.CHEST,
            "feet", EquipmentSlot.FEET,
            "head", EquipmentSlot.HEAD,
            "legs", EquipmentSlot.LEGS,
            "mainhand", EquipmentSlot.MAINHAND,
            "offhand", EquipmentSlot.OFFHAND
    );

    private static final Map<String, HostMolangSink.VarSampler> EXTRA_VARS = new ConcurrentHashMap<>();
    private static final Map<String, HostMolangSink.FunctionHandler> EXTRA_FUNCTIONS = new ConcurrentHashMap<>();
    private static final List<Consumer<HostMolangSink>> PENDING_REGISTRARS = new CopyOnWriteArrayList<>();
    private static volatile boolean extensionsInitialized = false;

    private HostMolangBindings() {
    }

    public static void registerNamespace(String namespace, Consumer<HostMolangSink> registrar) {
        String prefix = namespace.endsWith(".") ? namespace : namespace + ".";
        HostMolangSink sink = new HostMolangSink() {
            @Override
            public void var(String name, VarSampler sampler) {
                EXTRA_VARS.put(prefix + name.toLowerCase(Locale.ROOT), sampler);
            }

            @Override
            public void function(String name, FunctionHandler handler) {
                EXTRA_FUNCTIONS.put(prefix + name.toLowerCase(Locale.ROOT), handler);
            }
        };
        registrar.accept(sink);
    }

    public static void addLazyExtension(Consumer<HostMolangSink> ctrlRegistrar) {
        PENDING_REGISTRARS.add(ctrlRegistrar);
    }

    private static HostMolangSink namespaceSink(String prefix) {
        return new HostMolangSink() {
            @Override
            public void var(String name, VarSampler sampler) {
                EXTRA_VARS.put(prefix + name.toLowerCase(Locale.ROOT), sampler);
            }

            @Override
            public void function(String name, FunctionHandler handler) {
                EXTRA_FUNCTIONS.put(prefix + name.toLowerCase(Locale.ROOT), handler);
            }
        };
    }

    private static void ensureExtensionsInitialized() {
        if (!extensionsInitialized) {
            synchronized (HostMolangBindings.class) {
                if (!extensionsInitialized) {
                    HostMolangSink ctrlSink = namespaceSink("ctrl.");
                    HostMolangSink tlmSink = namespaceSink("tlm.");
                    HostMolangSink ysmSink = namespaceSink("ysm.");

                    registerBuiltinCompatExtensions(ctrlSink, tlmSink, ysmSink);

                    for (Consumer<HostMolangSink> r : PENDING_REGISTRARS) {
                        r.accept(ctrlSink);
                    }
                    extensionsInitialized = true;
                }
            }
        }
    }

    private static void registerBuiltinCompatExtensions(
            HostMolangSink ctrlSink,
            HostMolangSink tlmSink,
            HostMolangSink ysmSink) {
        ctrlSink.var("playing_extra_animation", event -> {
            if (event.getAnimatable() instanceof CustomPlayerEntity cpe) {
                return MolangValue.bool(cpe.isModelSwitching()
                        && cpe.getAnimationState(UnifiedPlayerActionController.CAP_CONTROLLER_KEY) != HostPlayState.State.IDLE);
            }
            return MolangValue.bool(false);
        });
        ctrlSink.var("state_continue", event -> MolangValue.number(2.0));
        ctrlSink.var("state_stop", event -> MolangValue.number(3.0));
        ctrlSink.var("state_pause", event -> MolangValue.number(4.0));
        ctrlSink.var("state_bypass", event -> MolangValue.number(5.0));
        ctrlSink.var("loop", event -> MolangValue.number(10.0));
        ctrlSink.var("play_once", event -> MolangValue.number(11.0));
        ctrlSink.var("hold_on_last_frame", event -> MolangValue.number(12.0));

        CarryOnCompat.registerBindings(ctrlSink);
        TacCompat.registerControllerFunctions(ctrlSink);
        SWEMCompat.registerControllerFunctions(ctrlSink);
        ParcoolCompat.registerBindings(ctrlSink);
        SlashBladeCompat.registerControllerFunctions(ctrlSink);
        SBackpackCompat.registerControllerFunctions(ctrlSink);
        CreateCompat.registerCreateFunctions(ctrlSink);
        BetterCombatCompat.registerBindings(ctrlSink);
        ImmersiveMelodiesCompat.registerBindings(ctrlSink);
        SpellbooksCompat.registerBindings(ctrlSink);

        TouhouLittleMaidClientCompat.registerMaidAnimStates(tlmSink);
        CuriosCompat.registerCuriosItems(ysmSink);
    }

    public static @Nullable MolangValue sampleExtensionVar(String namespacedKey, @Nullable LivingEntity entity) {
        if (namespacedKey == null || namespacedKey.isEmpty() || entity == null) {
            return null;
        }
        ensureExtensionsInitialized();
        HostMolangSink.VarSampler sampler = EXTRA_VARS.get(namespacedKey.toLowerCase(Locale.ROOT));
        if (sampler == null) {
            return null;
        }
        HostActionEvent<HostAnimatable<LivingEntity>> event = new HostActionEvent<>(
                new SampleAnimatable(entity),
                0.0f,
                0.0f,
                entity.tickCount,
                0.0f,
                0.0f,
                false,
                false,
                new HostEntityModelData());
        return sampler.sample(event);
    }

    private static final class SampleAnimatable extends HostAnimatable<LivingEntity> {
        private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

        private SampleAnimatable(LivingEntity entity) {
            super(entity);
        }

        @Override
        public Identifier getTextureLocation() {
            return DEFAULT_TEXTURE;
        }

        @Override
        public boolean isModelReady() {
            return false;
        }

        @Override
        public float getHeightScale() {
            return 1.0f;
        }

        @Override
        public float getWidthScale() {
            return 1.0f;
        }

        @Override
        public void registerAnimationControllers() {
        }

        @Override
        public @Nullable HostAnimationAssets.ClipSpec getAnimation(String name) {
            return null;
        }
    }

    public static SceneInputs buildInputs(
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            boolean effectsAllowed,
            Map<String, CodedControllerCommand> commands) {
        return buildInputs(event, sampledCalls, effectsAllowed, commands, Map.of());
    }

    public static SceneInputs buildInputs(
            HostActionEvent<?> event,
            Map<MolangBindings.Call, MolangValue> sampledCalls,
            boolean effectsAllowed,
            Map<String, CodedControllerCommand> commands,
            Map<dev.spm.core.scene.MolangHooks.Phase, List<MolangValue>> hookArguments) {
        ensureExtensionsInitialized();
        Entity entity = event.getAnimatable().getEntity();
        LivingEntity living = entity instanceof LivingEntity le ? le : null;
        Player player = entity instanceof Player p ? p : null;
        Minecraft client = Minecraft.getInstance();
        float partial = event.getPartialTick();
        HostEntityFrameState<?> tracker = event.getAnimatable().getPositionTracker();
        HostEntityModelData modelData = event.getModelData();

        Map<String, Double> queries = new LinkedHashMap<>(64);
        Map<String, MolangValue> values = new LinkedHashMap<>(128);

        boolean preview = CameraUtil.isThirdPersonModel(event.getAnimatable());
        Vec3 pos = entity.getPosition(partial);
        Vec3 vel = entity.getDeltaMovement();
        float groundSpeed = tracker.getGroundSpeed(entity, event);
        float verticalSpeed = tracker.getVerticalSpeed(entity);
        long dayTime = entity.level().getDefaultClockTime();

        queries.put("actor_count", (double) (client.level != null ? client.level.getEntityCount() : 1));
        queries.put("life_time", (double) (event.getAnimatable().getSeekTime() / 20.0));
        queries.put("head_x_rotation", (double) modelData.netHeadYaw);
        queries.put("head_y_rotation", (double) modelData.headPitch);
        queries.put("moon_phase", (double) Math.floorMod(dayTime / 24000L, 8L));
        queries.put("time_of_day", (double) ((((float) (dayTime + 6000L)) / 24000.0f) % 1.0f));
        queries.put("time_stamp", (double) dayTime);
        queries.put("delta_time", (double) tracker.getTimeDeltaSeconds());
        queries.put("yaw_speed", (double) getYawSpeed(entity));
        queries.put("cardinal_facing_2d", (double) entity.getDirection().get3DDataValue());
        queries.put("distance_from_camera", client.gameRenderer != null
                ? client.gameRenderer.getMainCamera().position().distanceTo(entity.position()) : 0.0);
        queries.put("eye_target_x_rotation", RenderContext.isOldHud() ? 0.0 : (double) entity.getViewXRot(partial));
        queries.put("eye_target_y_rotation", RenderContext.isOldHud() ? 0.0 : (double) entity.getViewYRot(partial));
        queries.put("ground_speed", (double) groundSpeed);
        queries.put("modified_distance_moved", (double) (living != null ? living.moveDist : 0.0f));
        queries.put("vertical_speed", (double) verticalSpeed);
        queries.put("walk_distance", (double) (living != null ? living.moveDist : 0.0f));
        queries.put("has_rider", entity.isVehicle() ? 1.0 : 0.0);
        int cameraType = CameraUtil.getCameraType(event.getAnimatable());
        queries.put("is_first_person", cameraType == CameraType.FIRST_PERSON.ordinal() ? 1.0 : 0.0);
        queries.put("is_in_water", entity.isInWater() ? 1.0 : 0.0);
        queries.put("is_in_water_or_rain", (entity.isInWater() || entity.level().isRainingAt(entity.blockPosition())) ? 1.0 : 0.0);
        queries.put("is_on_fire", entity.isOnFire() ? 1.0 : 0.0);
        queries.put("is_on_ground", entity.onGround() ? 1.0 : 0.0);
        queries.put("is_riding", entity.isPassenger() ? 1.0 : 0.0);
        queries.put("is_sneaking", (entity.onGround() && entity.getPose() == Pose.CROUCHING) ? 1.0 : 0.0);
        queries.put("is_spectator", entity.isSpectator() ? 1.0 : 0.0);
        queries.put("is_sprinting", entity.isSprinting() ? 1.0 : 0.0);
        queries.put("is_swimming", entity.isSwimming() ? 1.0 : 0.0);

        if (living != null) {
            queries.put("body_x_rotation", RenderContext.isOldHud() ? 0.0 : (double) Mth.lerp(event.getFrameTime(), living.xRotO, living.getXRot()));
            queries.put("body_y_rotation", RenderContext.isOldHud() ? 0.0 : (double) Mth.wrapDegrees(Mth.lerp(partial, living.yBodyRotO, living.yBodyRot)));
            boolean isLocalCap = !(event.getAnimatable() instanceof PlayerCapability cap) || cap.isLocalPlayerModel();
            float health = isLocalCap ? living.getHealth() : ((PlayerCapability) event.getAnimatable()).getPositionTracker().getHealth();
            float maxHealth = isLocalCap ? living.getMaxHealth() : ((PlayerCapability) event.getAnimatable()).getPositionTracker().getMaxHealth();
            queries.put("health", (double) health);
            queries.put("max_health", (double) maxHealth);
            queries.put("hurt_time", (double) living.hurtTime);
            queries.put("is_eating", living.getUseItem().getUseAnimation() == ItemUseAnimation.EAT ? 1.0 : 0.0);
            queries.put("is_playing_dead", living.isDeadOrDying() ? 1.0 : 0.0);
            queries.put("is_sleeping", living.isSleeping() ? 1.0 : 0.0);
            queries.put("is_using_item", InputStateKey.isUsingItem(living, InputStateKey.getUsedItemHand(living)) ? 1.0 : 0.0);
            queries.put("item_in_use_duration", InputStateKey.getTicksUsingItem(living) / 20.0);
            ItemStack useItem = living.getUseItem();
            queries.put("item_max_use_duration", useItem.isEmpty() ? 0.0 : useItem.getUseDuration(living) / 20.0);
            queries.put("item_remaining_use_duration", living.isUsingItem() ? living.getUseItemRemainingTicks() / 20.0 : 0.0);
            boolean swinging = isSwinging(event, living);
            queries.put("is_swinging", swinging ? 1.0 : 0.0);
            queries.put("swing_time", swinging ? getSwingTime(event, living) / 20.0 : 0.0);
            queries.put("attack_time", (double) getAttackTime(event, living));
            int armorCount = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (!CosmeticArmorHelper.getArmorItem(living, slot).isEmpty()) armorCount++;
            }
            queries.put("equipment_count", (double) armorCount);
        }

        if (player != null) {
            boolean isLocalCap = !(event.getAnimatable() instanceof PlayerCapability cap) || cap.isLocalPlayerModel();
            queries.put("player_level", (double) (isLocalCap ? player.experienceLevel : ((PlayerCapability) event.getAnimatable()).getPositionTracker().getExperienceLevel()));
            boolean isFlying = event.getAnimatable() instanceof PlayerCapability cap ? (isLocalCap ? player.getAbilities().flying : cap.getPositionTracker().isFlying()) : player.getAbilities().flying;
            queries.put("is_jumping", (!isFlying && !player.isPassenger() && !player.onGround() && !player.isInWater()) ? 1.0 : 0.0);
            float dx = (float) -Mth.lerp(partial, player.xo, player.getX());
            float dy = (float) -Mth.lerp(partial, player.yo, player.getY());
            float dz = (float) -Mth.lerp(partial, player.zo, player.getZ());
            float bodyYaw = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO);
            float flap = CoreQuerySampling.capeFlap(dx, dy, dz,
                    Mth.sin(bodyYaw * ((float) Math.PI / 180.0f)),
                    -Mth.cos(bodyYaw * ((float) Math.PI / 180.0f)),
                    Mth.sin(player.moveDist * 6.0f),
                    0.0f,
                    player.isCrouching());
            queries.put("cape_flap_amount", (double) flap);
            if (player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
                queries.put("has_cape", (!player.isInvisible() && player.isModelPartShown(PlayerModelPart.CAPE) && clientPlayer.getSkin().cape() != null) ? 1.0 : 0.0);
            } else {
                queries.put("has_cape", 0.0);
            }
        }

        // Vector queries
        values.put("query.position", vectorStruct(pos));
        Vec3 resolvedDelta = tracker.getResolvedPositionDelta(entity);
        values.put("query.position_delta", vectorStruct(resolvedDelta));

        // ysm.* bindings
        values.put("ysm.head_yaw", MolangValue.number(modelData.netHeadYaw));
        values.put("ysm.head_pitch", MolangValue.number(modelData.headPitch));
        float moveSpeedFactor = living != null ? living.moveDist : 0.0f;
        float lifeSec = event.getAnimatable().getSeekTime() / 20.0f;
        values.put("ysm.tcos0", MolangValue.number(Math.cos(lifeSec * 103.2 * Math.PI / 180.0) * Math.min(1.0, moveSpeedFactor * 4.0) * 20.0));
        values.put("ysm.swim_amount", MolangValue.number(living != null && living.isSwimming() ? 1.0 : 0.0));
        values.put("ysm.item_use_normalized", MolangValue.number(living != null && living.isUsingItem() ? 1.0 : 0.0));
        values.put("ysm.map_angle", MolangValue.number(Math.max(0.0, Math.min(1.0, 1.0 - modelData.headPitch / 45.1))));
        values.put("ysm.is_holding_right", MolangValue.number(living != null && !living.getMainHandItem().isEmpty() ? 1.0 : 0.0));
        values.put("ysm.is_holding_left", MolangValue.number(living != null && !living.getOffhandItem().isEmpty() ? 1.0 : 0.0));
        values.put("ysm.target_x_rotation", MolangValue.number(modelData.headPitch));
        values.put("ysm.target_y_rotation", MolangValue.number(modelData.netHeadYaw));
        values.put("ysm.sleep_rotation", MolangValue.number(0.0));
        values.put("ysm.modified_move_speed", MolangValue.number(moveSpeedFactor * 20.0));
        values.put("ysm.weather", MolangValue.number(entity.level().isThundering() ? 2 : entity.level().isRaining() ? 1 : 0));
        values.put("ysm.dimension_name", MolangValue.text(entity.level().dimension().identifier().toString()));
        values.put("ysm.fps", MolangValue.number(client.getFps()));
        values.put("ysm.time_delta", MolangValue.number(tracker.getTimeDeltaSeconds()));
        values.put("ysm.ground_speed2", MolangValue.number(groundSpeed));
        values.put("ysm.input_vertical", MolangValue.number(getYawInterpolation(entity, resolvedDelta, event.getFrameTime())));
        values.put("ysm.input_horizontal", MolangValue.number(getPitchInterpolation(entity, resolvedDelta, event.getFrameTime())));
        values.put("ysm.person_view", MolangValue.number(cameraType));
        values.put("ysm.rendering_in_paperdoll", MolangValue.bool(RenderContext.isGuiPreview()));
        values.put("ysm.rendering_in_inventory", MolangValue.bool(preview));
        values.put("ysm.block_light", MolangValue.number(entity.level().getBrightness(LightLayer.BLOCK, entity.blockPosition())));
        values.put("ysm.sky_light", MolangValue.number(entity.level().getBrightness(LightLayer.SKY, entity.blockPosition())));
        values.put("ysm.is_passenger", MolangValue.bool(entity.isPassenger()));
        values.put("ysm.is_sleep", MolangValue.bool(entity.getPose() == Pose.SLEEPING));
        values.put("ysm.is_sneak", MolangValue.bool(entity.onGround() && entity.getPose() == Pose.CROUCHING));
        values.put("ysm.is_open_air", MolangValue.bool(isOpenAir(entity)));
        values.put("ysm.eye_in_water", MolangValue.bool(entity.isUnderWater()));
        values.put("ysm.frozen_ticks", MolangValue.number(entity.getTicksFrozen()));
        values.put("ysm.air_supply", MolangValue.number(entity.getAirSupply()));
        values.put("ysm.delta_movement_length", MolangValue.number(tracker.getDeltaMovementLength(entity)));

        AbstractBoat boat = getBoat(entity);
        values.put("ysm.boat_left_paddle", MolangValue.bool(boat != null && boat.getPaddleState(AbstractBoat.PADDLE_LEFT)));
        values.put("ysm.boat_right_paddle", MolangValue.bool(boat != null && boat.getPaddleState(AbstractBoat.PADDLE_RIGHT)));
        values.put("ysm.boat_left_rowing_time", MolangValue.number(boat != null ? -boat.getRowingTime(AbstractBoat.PADDLE_LEFT, partial) : 0.0));
        values.put("ysm.boat_right_rowing_time", MolangValue.number(boat != null ? -boat.getRowingTime(AbstractBoat.PADDLE_RIGHT, partial) : 0.0));
        values.put("ysm.boat_is_chest", MolangValue.bool(boat instanceof AbstractChestBoat));
        boolean raft = boat instanceof Raft || boat instanceof ChestRaft;
        values.put("ysm.boat_is_raft", MolangValue.bool(raft));
        values.put("ysm.boat_body_offset_y", MolangValue.number(raft ? BOAT_RAFT_BODY_Y_OFFSET : BOAT_DEFAULT_BODY_Y_OFFSET));
        double bodyOffsetZ = (boat instanceof AbstractChestBoat) ? BOAT_CHEST_BODY_Z_OFFSET : 0.0;
        values.put("ysm.boat_body_offset_z", MolangValue.number(bodyOffsetZ));
        values.put("ysm.boat_paddle_scale", MolangValue.number(1.0));
        values.put("ysm.boat_chest_passenger_offset", MolangValue.number(-bodyOffsetZ));

        if (living != null) {
            values.put("ysm.has_helmet", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.HEAD).isEmpty()));
            values.put("ysm.has_chest_plate", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.CHEST).isEmpty()));
            values.put("ysm.has_leggings", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.LEGS).isEmpty()));
            values.put("ysm.has_boots", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.FEET).isEmpty()));
            values.put("ysm.has_mainhand", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.MAINHAND).isEmpty()));
            values.put("ysm.has_offhand", MolangValue.bool(!CosmeticArmorHelper.getArmorItem(living, EquipmentSlot.OFFHAND).isEmpty()));
            values.put("ysm.has_elytra", MolangValue.bool(!CosmeticArmorHelper.getElytraItem(living).isEmpty()));
            values.put("ysm.is_riptide", MolangValue.bool(living.isAutoSpinAttack()));
            values.put("ysm.armor_value", MolangValue.number(living.getArmorValue()));
            values.put("ysm.hurt_time", MolangValue.number(living.hurtTime));
            float blinkPhase = (event.currentTick + (Math.abs(living.getUUID().getLeastSignificantBits()) % 10)) % 90.0f;
            values.put("ysm.is_close_eyes", MolangValue.bool(living.isSleeping() || (blinkPhase > 85.0f && blinkPhase < 90.0f)));
            values.put("ysm.on_ladder", MolangValue.bool(living.onClimbable()));
            values.put("ysm.ladder_facing", MolangValue.number(getLadderFacing(living)));
            values.put("ysm.arrow_count", MolangValue.number(living.getArrowCount()));
            values.put("ysm.stinger_count", MolangValue.number(living.getStingerCount()));
            String entityTypeName = getEntityTypeName(living);
            values.put("ysm.entity_type", MolangValue.text(entityTypeName));
            values.put("ysm.is_player", MolangValue.bool("player".equals(entityTypeName)));
            values.put("ysm.is_maid", MolangValue.bool("maid".equals(entityTypeName)));

            boolean isRemotePlayer = (event.getAnimatable() instanceof PlayerCapability cap) && !cap.isLocalPlayerModel();
            values.put("ysm.food_level", MolangValue.number(isRemotePlayer
                    ? ((PlayerCapability) event.getAnimatable()).getPositionTracker().getFoodLevel()
                    : (player != null ? player.getFoodData().getFoodLevel() : 20)));
            values.put("ysm.xxa", MolangValue.number(isRemotePlayer
                    ? ((PlayerCapability) event.getAnimatable()).getPositionTracker().getStrafeInput() : living.xxa));
            values.put("ysm.yya", MolangValue.number(isRemotePlayer
                    ? ((PlayerCapability) event.getAnimatable()).getPositionTracker().getVerticalInput() : living.yya));
            values.put("ysm.zza", MolangValue.number(isRemotePlayer
                    ? ((PlayerCapability) event.getAnimatable()).getPositionTracker().getForwardInput() : living.zza));

            ItemStack mainHand = living.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = living.getItemInHand(InteractionHand.OFF_HAND);
            values.put("ysm.mainhand_charged_crossbow", MolangValue.bool(mainHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(mainHand)));
            values.put("ysm.offhand_charged_crossbow", MolangValue.bool(offHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(offHand)));
            WeaponActionState ws = WeaponActionBridge.get(living, partial);
            values.put("ysm.weapon_type", MolangValue.number(switch (ws.kind()) {
                case TRIDENT -> 1; case LANCE -> 2; case MACE -> 3; case SPEAR -> 4; case NONE -> 0;
            }));
            values.put("ysm.weapon_is_trident", MolangValue.bool(ws.kind() == WeaponKind.TRIDENT));
            values.put("ysm.weapon_is_lance", MolangValue.bool(ws.kind() == WeaponKind.LANCE));
            values.put("ysm.weapon_is_spear", MolangValue.bool(ws.kind() == WeaponKind.SPEAR));
            values.put("ysm.weapon_is_mace", MolangValue.bool(ws.kind() == WeaponKind.MACE));
            values.put("ysm.weapon_attacking", MolangValue.bool(ws.trident().attacking() || ws.lance().jabbing() || ws.lance().lunging() || ws.mace().attacking()));
            values.put("ysm.weapon_using", MolangValue.bool(ws.trident().using() || ws.lance().using()));
            values.put("ysm.weapon_riding", MolangValue.bool(ws.lance().riding() || ws.mace().riding()));
            values.put("ysm.weapon_fall_flying", MolangValue.bool(ws.lance().fallFlying() || ws.mace().fallFlying()));
            values.put("ysm.weapon_speed", MolangValue.number(ws.speed()));
            values.put("ysm.weapon_attack_ticks", MolangValue.number(Math.max(ws.trident().attackTicks(), Math.max(ws.lance().attackTicks(), ws.mace().attackTicks()))));
            values.put("ysm.weapon_use_ticks", MolangValue.number(Math.max(ws.trident().useTicks(), ws.lance().useTicks())));
            TridentActionState trident = ws.trident();
            values.put("ysm.trident_holding", MolangValue.bool(trident.holding()));
            values.put("ysm.trident_using", MolangValue.bool(trident.using()));
            values.put("ysm.trident_throwing", MolangValue.bool(trident.throwing()));
            values.put("ysm.trident_riptide", MolangValue.bool(trident.riptide()));
            values.put("ysm.trident_attack", MolangValue.bool(trident.attacking()));
            values.put("ysm.trident_use_ticks", MolangValue.number(trident.useTicks()));
            values.put("ysm.trident_attack_ticks", MolangValue.number(trident.attackTicks()));
            LanceActionState lance = ws.lance();
            values.put("ysm.lance_holding", MolangValue.bool(lance.holding()));
            values.put("ysm.lance_using", MolangValue.bool(lance.using()));
            values.put("ysm.lance_charging", MolangValue.bool(lance.charging()));
            values.put("ysm.lance_jabbing", MolangValue.bool(lance.jabbing()));
            values.put("ysm.lance_lunging", MolangValue.bool(lance.lunging()));
            values.put("ysm.lance_riding", MolangValue.bool(lance.riding()));
            values.put("ysm.lance_riding_charge", MolangValue.bool(lance.ridingCharge()));
            values.put("ysm.lance_fall_flying", MolangValue.bool(lance.fallFlying()));
            values.put("ysm.lance_use_ticks", MolangValue.number(lance.useTicks()));
            values.put("ysm.lance_attack_ticks", MolangValue.number(lance.attackTicks()));
            values.put("ysm.lance_speed", MolangValue.number(lance.speed()));
            values.put("ysm.lance_charge_progress", MolangValue.number(lance.chargeProgress()));
            MaceActionState mace = ws.mace();
            values.put("ysm.mace_holding", MolangValue.bool(mace.holding()));
            values.put("ysm.mace_falling", MolangValue.bool(mace.falling()));
            values.put("ysm.mace_can_smash", MolangValue.bool(mace.canSmash()));
            values.put("ysm.mace_smashing", MolangValue.bool(mace.smashing()));
            values.put("ysm.mace_wind_bursting", MolangValue.bool(mace.windBursting()));
            values.put("ysm.mace_attacking", MolangValue.bool(mace.attacking()));
            values.put("ysm.mace_riding", MolangValue.bool(mace.riding()));
            values.put("ysm.mace_fall_flying", MolangValue.bool(mace.fallFlying()));
            values.put("ysm.mace_fall_distance", MolangValue.number(mace.fallDistance()));
            values.put("ysm.mace_vertical_speed", MolangValue.number(mace.verticalSpeed()));
            values.put("ysm.mace_attack_ticks", MolangValue.number(mace.attackTicks()));
            values.put("ysm.mace_smash_progress", MolangValue.number(mace.smashProgress()));

            values.put("ysm.is_fishing", MolangValue.bool(player != null ? player.fishing != null : TouhouLittleMaidCompat.isMaidSitting(living)));
            values.put("ysm.swinging", MolangValue.bool(isSwinging(event, living)));
            values.put("ysm.swing_time", MolangValue.number(getSwingTime(event, living)));
            values.put("ysm.swinging_arm", MolangValue.number(getSwingingHand(event, living) == InteractionHand.MAIN_HAND ? 0 : 1));
            values.put("ysm.attack_time", MolangValue.number(getAttackTime(event, living)));
            values.put("ysm.elytra_rot_x", MolangValue.number(Math.toDegrees(living.elytraAnimationState.getRotX(partial))));
            values.put("ysm.elytra_rot_y", MolangValue.number(Math.toDegrees(living.elytraAnimationState.getRotY(partial))));
            values.put("ysm.elytra_rot_z", MolangValue.number(Math.toDegrees(living.elytraAnimationState.getRotZ(partial))));
        }

        if (player != null) {
            values.put("ysm.item_is_charged", MolangValue.bool(player.getOffhandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getOffhandItem())));
            values.put("ysm.has_left_shoulder_parrot", MolangValue.bool(player.getShoulderParrotLeft().isPresent()));
            values.put("ysm.has_right_shoulder_parrot", MolangValue.bool(player.getShoulderParrotRight().isPresent()));
            values.put("ysm.left_shoulder_parrot_variant", MolangValue.text(parrotVariant(player.getShoulderParrotLeft())));
            values.put("ysm.right_shoulder_parrot_variant", MolangValue.text(parrotVariant(player.getShoulderParrotRight())));
            values.put("ysm.attack_damage", MolangValue.number(player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            values.put("ysm.attack_speed", MolangValue.number(player.getAttributeValue(Attributes.ATTACK_SPEED)));
            values.put("ysm.attack_knockback", MolangValue.number(player.getAttributeValue(Attributes.ATTACK_KNOCKBACK)));
            values.put("ysm.movement_speed", MolangValue.number(player.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            values.put("ysm.knockback_resistance", MolangValue.number(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
            values.put("ysm.luck", MolangValue.number(player.getAttributeValue(Attributes.LUCK)));
            values.put("ysm.block_reach", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.blockReach(), 4.5D)));
            values.put("ysm.entity_reach", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.entityReach(), 3.0D)));
            values.put("ysm.swim_speed", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.swimSpeed(), 1.0D)));
            values.put("ysm.entity_gravity", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.entityGravity(), 0.08D)));
            values.put("ysm.step_height_addition", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.stepHeightAddition(), 0.0D)));
            values.put("ysm.nametag_distance", MolangValue.number(ForgeAttributes.getValue(player, ForgeAttributes.nametagDistance(), 64.0D)));
            boolean shieldCooldown = event.getAnimatable() instanceof PlayerCapability cap && cap.getPositionTracker().isShieldBlocking();
            values.put("ysm.in_shield_block_cooldown", MolangValue.bool(shieldCooldown));
        }

        if (event.getAnimatable() instanceof CustomPlayerEntity customPlayer) {
            String tex = customPlayer.getCurrentTextureName();
            if (tex != null) {
                values.put("ysm.texture_name", MolangValue.text(tex));
            }
            boolean playingExtra = customPlayer.isModelSwitching()
                    && customPlayer.getAnimationState(HostControllerSlot.PLAYER_CAP_CONTROLLER) != HostPlayState.State.IDLE;
            values.put("ctrl.playing_extra_animation", MolangValue.bool(playingExtra));
        }
        values.put("ysm.first_person_mod_hide", MolangValue.bool(cameraType == CameraType.FIRST_PERSON.ordinal()));
        if (entity == client.player) {
            values.put("ysm.hit_target_id", MolangValue.text(getHitTargetId(client)));
            values.put("ysm.hit_target_type", MolangValue.text(""));
        }
        if (entity instanceof ThrowableItemProjectile tip && tip instanceof ThrowableItemProjectileAccessor accessor) {
            Identifier key = BuiltInRegistries.ITEM.getKey(accessor.invokeGetDefaultItem());
            if (key != null) {
                values.put("ysm.throwable_item", MolangValue.text(key.toString()));
            }
        }
        if (entity instanceof FishingHook hook) {
            Entity hooked = ((FishingHookAccessor) hook).getHookedIn();
            if (hooked != null) {
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(hooked.getType());
                if (key != null) {
                    values.put("ysm.hooked_in", MolangValue.text(key.toString()));
                }
            }
            values.put("ysm.is_biting", MolangValue.bool(((FishingHookAccessor) hook).isBiting()));
        }
        if (entity instanceof AbstractArrow arrow && arrow instanceof ProjectileStateAccessor accessor) {
            values.put("ysm.on_ground_time", MolangValue.number(accessor.ysm$getInGroundTime()));
            values.put("ysm.in_ground", MolangValue.bool(accessor.ysm$isInGround()));
            values.put("ysm.is_spectral_arrow", MolangValue.bool(arrow instanceof SpectralArrow));
            values.put("ysm.shoot_item_id", MolangValue.text(accessor.ysm$getOwnerItemId()));
        }

        // ctrl.* state predicates from authoritative ControllerActionResolver
        String currentCtrlState = ControllerActionResolver.resolve(event);
        for (String[] entry : CTRL_STATE_ENTRIES) {
            values.put(entry[1], MolangValue.bool(entry[0].equals(currentCtrlState)));
        }

        // External compat vars (ParCool, TaCZ, BetterCombat, CarryOn, SlashBlade, SWEM, Create, IM, ISS, TLM, Curios, SBackpack)
        for (Map.Entry<String, HostMolangSink.VarSampler> entry : EXTRA_VARS.entrySet()) {
            try {
                MolangValue v = entry.getValue().sample(event);
                if (v != null && v != MolangValue.missing()) {
                    values.put(entry.getKey(), v);
                }
            } catch (RuntimeException ignored) {
            }
        }

        var syncMode = client.hasSingleplayerServer() ? SceneInputs.SyncMode.LOCAL : SceneInputs.SyncMode.HOST;
        return new SceneInputs(
                queries,
                new MolangBindings(values, sampledCalls),
                effectsAllowed,
                syncMode,
                hookArguments != null ? hookArguments : Map.of(),
                commands);
    }

    public static MolangValue resolveCall(HostActionEvent<?> event, MolangBindings.Call call) {
        String name = call.name();
        List<MolangValue> args = call.arguments();
        Entity entity = event.getAnimatable().getEntity();
        LivingEntity living = entity instanceof LivingEntity le ? le : null;

        HostMolangSink.FunctionHandler ext = EXTRA_FUNCTIONS.get(name);
        if (ext != null) {
            try {
                MolangValue res = ext.invoke(event, args);
                return res == null ? MolangValue.missing() : res;
            } catch (RuntimeException ignored) {
                return MolangValue.missing();
            }
        }

        if (CoreQueryFunctions.supports(name) && living != null) {
            try {
                var resolver = MinecraftHostSnapshotSampler.queryResolver(Minecraft.getInstance(), living, false);
                return resolver.resolve(call);
            } catch (RuntimeException ignored) {
                return MolangValue.missing();
            }
        }

        return switch (name) {
            case "query.debug_output" -> MolangValue.missing();
            case "ysm.play_sound", "ysm.stop_sound", "ysm.stop_all_sounds", "ysm.particle", "ysm.abs_particle" ->
                    MolangValue.bool(true);
            case "ysm.relative_block_name_any" -> resolveRelativeBlockNameAny(entity, args);
            case "ysm.equipped_enchantment_level" -> resolveEquippedEnchantmentLevel(living, args);
            case "ysm.effect_level" -> resolveEffectLevel(event.getAnimatable(), entity, args);
            case "ysm.mod_version" -> resolveModVersion(args);
            case "ysm.keyboard" -> resolveKeyboard(args);
            case "ysm.mouse" -> resolveMouse(args);
            case "ysm.bone_rot" -> resolveBoneVec(event.getAnimatable(), args, BoneChannel.ROT);
            case "ysm.bone_pos" -> resolveBoneVec(event.getAnimatable(), args, BoneChannel.POS);
            case "ysm.bone_scale" -> resolveBoneVec(event.getAnimatable(), args, BoneChannel.SCALE);
            case "ysm.bone_pivot_abs" -> resolveBoneVec(event.getAnimatable(), args, BoneChannel.PIVOT_ABS);
            case "ysm.get_root_locator_offset" -> MolangValue.number(0.0);
            case "ysm.dump_equipped_item", "ysm.dump_relative_block" -> MolangValue.missing();
            case "ctrl.armor" -> resolveCtrlArmor(living, args);
            case "ctrl.ride" -> resolveCtrlRide(living, args);
            case "ctrl.hold" -> resolveHandRender(event, living, args, HandTestMode.HOLD);
            case "ctrl.swing" -> resolveHandRender(event, living, args, HandTestMode.SWING);
            case "ctrl.use" -> resolveHandRender(event, living, args, HandTestMode.USE);
            default -> MolangValue.missing();
        };
    }

    public static void dispatchEffects(
            HostAnimatable<?> animatable,
            HostActionEvent<?> event,
            AudioPlayerManager entityAudio,
            AudioPlayerManager globalAudio,
            ModelScenePose pose,
            List<dev.spm.core.scene.EffectCommand> effects) {
        Entity entity = animatable.getEntity();
        Minecraft mc = Minecraft.getInstance();
        long particleKey = com.micaftic.morpher.client.animation.particle.AnimationParticleQueue.sampleKey(
                event.getTickCount(), event.getFrameTime());
        for (var effect : effects) {
            List<MolangValue> args = effect.arguments();
            if ("sound".equals(effect.kind()) && args.isEmpty()) {
                entityAudio.playSound(animatable, 0, effect.resource(), false, null);
                continue;
            }
            if ("particle".equals(effect.kind()) && args.isEmpty()) {
                String particleName = effect.resource();
                Object locatorObj = effect.parameters().get("locator");
                String locator = locatorObj == null ? "" : locatorObj.toString();
                if (!particleName.isBlank() && pose != null && !locator.isEmpty() && entity != null) {
                    pose.animationParticles.enqueue(particleKey, locator, offset -> {
                        Vector3d posVec = new Vector3d(offset.x, offset.y, offset.z);
                        mc.execute(() -> {
                            try {
                                ParticleEffectUtil.spawnAnimationParticle(entity, particleName, posVec, entity.getRandom());
                            } catch (Exception ignored) {
                            }
                        });
                    });
                }
                continue;
            }
            switch (effect.resource()) {
                case "ysm.play_sound" -> {
                    if (args.size() >= 2 && entity != null) {
                        int soundId = idFromArg(args.get(0));
                        String soundName = textArg(args, 1);
                        int flags = args.size() >= 3 ? (int) args.get(2).number() : 0;
                        float volume = args.size() >= 4 ? (float) args.get(3).number() : 1.0f;
                        float pitch = args.size() >= 5 ? (float) args.get(4).number() : 1.0f;
                        if (!soundName.isBlank() && flags >= 0 && flags <= 7) {
                            AudioPlayerManager mgr = (flags & 2) == 2 ? globalAudio : entityAudio;
                            mgr.playSound(animatable, soundId, soundName, (flags & 1) == 1, instance -> {
                                if (instance != null) {
                                    instance.setVolume(Mth.clamp(volume, 0.0f, 1.0f));
                                    instance.setPitch(Mth.clamp(pitch, 0.0f, 2.0f));
                                    if ((flags & 4) == 4) {
                                        instance.setLooping(true);
                                    }
                                }
                            });
                        }
                    }
                }
                case "ysm.stop_sound" -> {
                    if (!args.isEmpty()) {
                        int soundId = idFromArg(args.get(0));
                        boolean global = args.size() >= 2 && args.get(1).truth();
                        (global ? globalAudio : entityAudio).stopSound(soundId);
                    }
                }
                case "ysm.stop_all_sounds" -> {
                    boolean global = !args.isEmpty() && args.get(0).truth();
                    (global ? globalAudio : entityAudio).stopAll();
                }
                case "ysm.particle", "ysm.abs_particle" -> {
                    if (entity != null && !args.isEmpty()) {
                        boolean abs = "ysm.abs_particle".equals(effect.resource());
                        mc.execute(() -> {
                            try {
                                ParticleEffectUtil.handleParticle(entity, args, abs, entity.getRandom());
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
                default -> {
                }
            }
        }
    }

    private enum BoneChannel { ROT, POS, SCALE, PIVOT_ABS }
    private enum HandTestMode { HOLD, SWING, USE }

    private static MolangValue resolveBoneVec(HostAnimatable<?> animatable, List<MolangValue> args, BoneChannel channel) {
        if (args.isEmpty()) return MolangValue.missing();
        String name = textArg(args, 0);
        if (name.isEmpty()) return MolangValue.missing();
        ModelScenePose pose = animatable.getCurrentModel();
        ModelBoneView bone = pose == null ? null : pose.getBone(BoneIds.id(name));
        if (bone == null) return MolangValue.missing();
        return switch (channel) {
            case ROT -> MolangValue.struct(Map.of(
                    "x", MolangValue.number(Math.toDegrees(bone.getRotationX())),
                    "y", MolangValue.number(Math.toDegrees(bone.getRotationY())),
                    "z", MolangValue.number(Math.toDegrees(bone.getRotationZ()))));
            case POS -> MolangValue.struct(Map.of(
                    "x", MolangValue.number(bone.getPositionX()),
                    "y", MolangValue.number(bone.getPositionY()),
                    "z", MolangValue.number(bone.getPositionZ())));
            case SCALE -> MolangValue.struct(Map.of(
                    "x", MolangValue.number(bone.getScaleX()),
                    "y", MolangValue.number(bone.getScaleY()),
                    "z", MolangValue.number(bone.getScaleZ())));
            case PIVOT_ABS -> MolangValue.struct(Map.of(
                    "x", MolangValue.number(bone.getAbsPivotX()),
                    "y", MolangValue.number(bone.getAbsPivotY()),
                    "z", MolangValue.number(bone.getAbsPivotZ())));
        };
    }

    private static MolangValue resolveRelativeBlockNameAny(Entity entity, List<MolangValue> args) {
        if (args.size() <= 3) return MolangValue.bool(false);
        double dx = args.get(0).number(), dy = args.get(1).number(), dz = args.get(2).number();
        if (Math.abs(dx) > 5.0 || Math.abs(dy) > 5.0 || Math.abs(dz) > 5.0) return MolangValue.missing();
        BlockPos pos = new BlockPos((int) Math.round(entity.getX() + dx - 0.5),
                (int) Math.round(entity.getY() + dy - 0.5),
                (int) Math.round(entity.getZ() + dz - 0.5));
        BlockState state = entity.level().getBlockState(pos);
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return MolangValue.bool(false);
        for (int i = 3; i < args.size(); i++) {
            Identifier expected = ResourceApi.parseNative(textArg(args, i));
            if (key.equals(expected)) return MolangValue.bool(true);
        }
        return MolangValue.bool(false);
    }

    private static MolangValue resolveEquippedEnchantmentLevel(@Nullable LivingEntity living, List<MolangValue> args) {
        if (living == null || args.size() < 2) return MolangValue.number(0);
        EquipmentSlot slot = SLOT_MAP.get(textArg(args, 0).toLowerCase(Locale.ENGLISH));
        if (slot == null) return MolangValue.number(0);
        ItemStack stack = CosmeticArmorHelper.getArmorItem(living, slot);
        if (stack.isEmpty()) return MolangValue.number(0);
        int level = 0;
        var registry = living.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (int i = 1; i < args.size(); i++) {
            Identifier id = ResourceApi.parseNative(textArg(args, i));
            if (id != null) {
                Enchantment raw = registry.get(id).map(Holder::value).orElse(null);
                if (raw != null) {
                    level += enchantments.getLevel(Holder.direct(raw));
                }
            }
        }
        return MolangValue.number(level);
    }

    private static MolangValue resolveEffectLevel(HostAnimatable<?> animatable, Entity entity, List<MolangValue> args) {
        int effects = 0;
        for (int i = 0; i < args.size(); i++) {
            Identifier effectId = ResourceApi.parseNative(textArg(args, i));
            if (effectId == null) continue;
            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectId).map(Holder::value).orElse(null);
            if (mobEffect == null) continue;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
            if (animatable instanceof PlayerCapability cap && !cap.isLocalPlayerModel()) {
                effects += cap.getPositionTracker().getEffectAmplifier(holder);
            } else if (entity instanceof LivingEntity living) {
                MobEffectInstance inst = living.getEffect(holder);
                if (inst != null) effects += inst.getAmplifier() + 1;
            } else if (entity instanceof Arrow arrow) {
                for (MobEffectInstance inst : ((ArrowEntityAccessor) (Object) arrow).getEffects()) {
                    if (inst.getEffect().value() == mobEffect) {
                        effects += inst.getAmplifier() + 1;
                        break;
                    }
                }
            }
        }
        return MolangValue.number(effects);
    }

    private static MolangValue resolveModVersion(List<MolangValue> args) {
        String modid = textArg(args, 0);
        if (modid.isEmpty() || !Platform.isModLoaded(modid)) return MolangValue.missing();
        return MolangValue.text(Platform.getMod(modid).getVersion());
    }

    private static MolangValue resolveKeyboard(List<MolangValue> args) {
        if (!InputUtil.isPlayerReady()) return MolangValue.bool(false);
        for (MolangValue arg : args) {
            int code = (int) arg.number();
            if (code >= 32 && code <= 348 && InputStateKey.keyStates[code]) {
                return MolangValue.bool(true);
            }
        }
        return MolangValue.bool(false);
    }

    private static MolangValue resolveMouse(List<MolangValue> args) {
        if (args.isEmpty() || !InputUtil.isPlayerReady()) return MolangValue.bool(false);
        int code = (int) args.get(0).number();
        return MolangValue.bool(code >= 0 && code <= 7 && InputStateKey.mouseStates[code]);
    }

    private static MolangValue resolveCtrlArmor(@Nullable LivingEntity entity, List<MolangValue> args) {
        if (entity == null || args.size() < 2) return MolangValue.number(0);
        EquipmentSlot slot = SLOT_MAP.get(textArg(args, 0).toLowerCase(Locale.ENGLISH));
        if (slot == null || !slot.isArmor()) return MolangValue.number(0);
        String id = textArg(args, 1);
        if (StringUtils.isBlank(id)) return MolangValue.number(0);
        ItemStack item = entity.getItemBySlot(slot);
        if (item.isEmpty() && "empty".equals(id)) return MolangValue.number(1);
        String target = id.substring(1);
        if (id.startsWith("$")) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item.getItem());
            return MolangValue.number(key != null && target.equals(key.toString()) ? 1 : 0);
        }
        if (id.startsWith("#")) {
            Identifier tagId = ResourceApi.parseNative(target);
            return MolangValue.number(tagId != null && item.is(TagKey.create(Registries.ITEM, tagId)) ? 1 : 0);
        }
        return MolangValue.number(0);
    }

    private static MolangValue resolveCtrlRide(@Nullable LivingEntity entity, List<MolangValue> args) {
        if (entity == null || args.size() < 2) return MolangValue.number(0);
        String type = textArg(args, 0);
        String id = textArg(args, 1);
        if (StringUtils.isBlank(id)) return MolangValue.number(0);
        Entity other = "vehicle".equals(type) ? entity.getVehicle() : "passenger".equals(type) ? entity.getFirstPassenger() : null;
        if (other == null || !other.isAlive()) return MolangValue.number(0);
        String target = id.substring(1);
        EntityType<?> entityType = other.getType();
        if (id.startsWith("$")) {
            Identifier expected = ResourceApi.parseNative(target);
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            return MolangValue.number(expected != null && expected.equals(key) ? 1 : 0);
        }
        if (id.startsWith("#")) {
            Identifier tagId = ResourceApi.parseNative(target);
            return MolangValue.number(tagId != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entityType).is(TagKey.create(Registries.ENTITY_TYPE, tagId)) ? 1 : 0);
        }
        return MolangValue.number(0);
    }

    private static MolangValue resolveHandRender(HostActionEvent<?> event, @Nullable LivingEntity entity, List<MolangValue> args, HandTestMode mode) {
        if (entity == null || args.size() < 2) return MolangValue.number(0);
        EquipmentSlot slot = SLOT_MAP.get(textArg(args, 0).toLowerCase(Locale.ENGLISH));
        if (slot == null || slot.isArmor()) return MolangValue.number(0);
        String id = textArg(args, 1);
        if (StringUtils.isBlank(id)) return MolangValue.number(0);
        ItemStack item = entity.getItemBySlot(slot);
        InteractionHand hand = slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean basePass = switch (mode) {
            case HOLD -> true;
            case SWING -> InputStateKey.isSwinging(entity, hand);
            case USE -> InputStateKey.isUsingItem(entity, hand);
        };
        boolean pulsePass = false;
        boolean localSwingTarget = (event.getAnimatable() instanceof PlayerCapability cap && cap.isLocalPlayerModel()) || entity instanceof Player;
        if (localSwingTarget) {
            pulsePass = (mode == HandTestMode.SWING && InputStateKey.isLocalSwinging(hand))
                    || (mode == HandTestMode.USE && InputStateKey.isUsingItem(entity, hand));
        }
        if (!basePass && !pulsePass) return MolangValue.number(0);
        if (item.isEmpty() && "empty".equals(id)) return MolangValue.number(1);
        String sub = id.substring(1);
        if (id.startsWith("$")) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item.getItem());
            return MolangValue.number(key != null && sub.equals(key.toString()) ? 1 : 0);
        }
        if (id.startsWith("#")) {
            Identifier tagId = ResourceApi.parseNative(sub);
            return MolangValue.number(tagId != null && item.is(TagKey.create(Registries.ITEM, tagId)) ? 1 : 0);
        }
        if (id.startsWith(":")) {
            String itemType = InnerClassify.getItemType(item);
            String legacyAlias = InnerClassify.getLegacyAlias(itemType);
            WeaponKind weaponKind = InnerClassify.getWeaponKind(item);
            if (weaponKind != WeaponKind.NONE) {
                boolean match = switch (weaponKind) {
                    case TRIDENT -> "spear".equals(sub) || "trident".equals(sub);
                    case LANCE, SPEAR -> "lance".equals(sub);
                    case MACE -> "mace".equals(sub);
                    case NONE -> false;
                };
                return MolangValue.number(match ? 1 : 0);
            }
            boolean match = (StringUtils.isNotBlank(itemType) && itemType.equals(sub))
                    || (StringUtils.isNotBlank(legacyAlias) && legacyAlias.equals(sub))
                    || item.getUseAnimation().name().toLowerCase(Locale.ENGLISH).equals(sub);
            return MolangValue.number(match ? 1 : 0);
        }
        return MolangValue.number(0);
    }

    private static int idFromArg(MolangValue val) {
        if (val instanceof MolangValue.TextValue text) {
            return BoneIds.id(text.value());
        }
        int num = -(int) val.number();
        return num > 0 ? 0 : num;
    }

    private static String textArg(List<MolangValue> args, int idx) {
        if (idx >= args.size()) return "";
        MolangValue v = args.get(idx);
        return v instanceof MolangValue.TextValue text ? text.value() : "";
    }

    private static MolangValue vectorStruct(Vec3 v) {
        return MolangValue.struct(Map.of(
                "x", MolangValue.number(v.x),
                "y", MolangValue.number(v.y),
                "z", MolangValue.number(v.z)));
    }

    private static float getYawSpeed(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) {
            return 20.0f * (entity.getYRot() - entity.yRotO);
        }
        return PlayerEntityFrameState.getHeadYawDelta();
    }

    private static boolean isOpenAir(Entity entity) {
        BlockPos pos = entity.blockPosition();
        return entity.level().canSeeSky(pos) && entity.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }

    private static int getLadderFacing(LivingEntity entity) {
        Optional<BlockPos> pos = entity.getLastClimbablePos();
        if (pos.isPresent()) {
            Optional<Direction> dir = entity.level().getBlockState(pos.get()).getOptionalValue(HorizontalDirectionalBlock.FACING);
            if (dir.isPresent()) return dir.get().get2DDataValue();
        }
        return 0;
    }

    private static String getEntityTypeName(LivingEntity entity) {
        if (entity instanceof Player) return "player";
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) return "";
        if ("touhou_little_maid".equals(key.getNamespace()) && "maid".equals(key.getPath())) return "maid";
        return key.toString();
    }

    private static String parrotVariant(Optional<Parrot.Variant> variant) {
        return variant.map(v -> v.name().toLowerCase(Locale.ENGLISH)).orElse("empty");
    }

    private static AbstractBoat getBoat(Entity entity) {
        if (entity instanceof AbstractBoat b) return b;
        return entity.getVehicle() instanceof AbstractBoat b ? b : null;
    }

    private static String getHitTargetId(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult block && block.getType() != HitResult.Type.MISS && mc.level != null) {
            Identifier key = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(block.getBlockPos()).getBlock());
            return key != null ? key.toString() : "";
        }
        if (hit instanceof EntityHitResult eHit) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(eHit.getEntity().getType());
            return key != null ? key.toString() : "";
        }
        return "";
    }

    private static boolean isLocalSwingTarget(HostActionEvent<?> event, LivingEntity entity) {
        return (event.getAnimatable() instanceof PlayerCapability cap && cap.isLocalPlayerModel()) || entity instanceof Player;
    }

    private static boolean isSwinging(HostActionEvent<?> event, LivingEntity entity) {
        if (isLocalSwingTarget(event, entity) && InputStateKey.isLocalAnyHandSwinging()) return true;
        return InputStateKey.isAnyHandSwinging(entity);
    }

    private static float getSwingTime(HostActionEvent<?> event, LivingEntity entity) {
        if (isLocalSwingTarget(event, entity) && InputStateKey.getLocalSwingPulseTicks() > 0) {
            return Math.max(1.0f, InputStateKey.getLocalSwingPulseAge() + event.getFrameTime());
        }
        return InputStateKey.getSwingTicks(entity, event.getFrameTime());
    }

    private static InteractionHand getSwingingHand(HostActionEvent<?> event, LivingEntity entity) {
        if (isLocalSwingTarget(event, entity) && InputStateKey.getLocalSwingPulseTicks() > 0) {
            return InputStateKey.getLocalSwingingHand();
        }
        return InputStateKey.getSwingingHand(entity);
    }

    private static float getAttackTime(HostActionEvent<?> event, LivingEntity entity) {
        if (isLocalSwingTarget(event, entity) && InputStateKey.getLocalSwingPulseTicks() > 0
                && InputStateKey.getLocalSwingingHand() == InteractionHand.MAIN_HAND) {
            return Math.min(1.0f, getSwingTime(event, entity) / 6.0f);
        }
        return InputStateKey.getAttackProgress(entity, event.getFrameTime());
    }

    private static double getYawInterpolation(Entity entity, Vec3 delta, float frameTime) {
        double dx = delta.x, dz = delta.z;
        if (Math.sqrt(dx * dx + dz * dz) < 1.0E-4) return 0.0;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dz, dx)) - (90.0f - Mth.wrapDegrees(-entity.getViewYRot(frameTime))));
        return Mth.cos((float) Math.toRadians(yaw));
    }

    private static double getPitchInterpolation(Entity entity, Vec3 delta, float frameTime) {
        double dx = delta.x, dz = delta.z;
        if (Math.sqrt(dx * dx + dz * dz) < 1.0E-4) return 0.0;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Mth.atan2(dz, dx)) - (90.0f - Mth.wrapDegrees(-entity.getViewYRot(frameTime))));
        return Mth.sin((float) Math.toRadians(yaw));
    }
}
