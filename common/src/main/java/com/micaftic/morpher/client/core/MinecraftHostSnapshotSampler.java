package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.SceneInputs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.item.UseAnim;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import java.util.HashMap;
import java.util.Map;

/** New Host sampler: Minecraft objects stop here; only immutable values reach the Core. */
public final class MinecraftHostSnapshotSampler {
    private MinecraftHostSnapshotSampler() { }
    public record CompatibilityContext(Minecraft client, LivingEntity entity, boolean preview) { }
    private static final CoreCompatibilityRegistry<CompatibilityContext> COMPATIBILITY = new CoreCompatibilityRegistry<>();
    private static volatile java.util.function.BiFunction<LivingEntity, CoreQueryFunctions.Slot, CoreQueryFunctions.Equipment> equipmentProvider;
    /** Provider returns effective equipment, including cosmetic fallback, as immutable Host data. */
    public static void registerEquipmentProvider(java.util.function.BiFunction<LivingEntity, CoreQueryFunctions.Slot, CoreQueryFunctions.Equipment> provider) {
        equipmentProvider = java.util.Objects.requireNonNull(provider);
    }
    public static MinecraftCoreQueryResolver queryResolver(Minecraft client, LivingEntity entity, boolean cosmeticArmorInstalled) {
        var provider = equipmentProvider;
        return new MinecraftCoreQueryResolver(client, entity, cosmeticArmorInstalled
                ? CoreQueryEquipmentPolicy.Presence.PRESENT : CoreQueryEquipmentPolicy.Presence.ABSENT,
                provider == null ? null : slot -> provider.apply(entity, slot));
    }
    public static void registerCompatibilityPresence(java.util.function.Predicate<String> presence) {
        COMPATIBILITY.registerPresence(presence);
    }
    public static void registerCompatibilityProvider(CoreCompatibilityDefaults.Integration integration,
            CoreCompatibilityRegistry.Provider<CompatibilityContext> provider) {
        COMPATIBILITY.registerProvider(integration, provider);
    }

    public static SceneInputs sample(Minecraft client, LivingEntity entity, boolean preview,
                                     CoreMotionSnapshot motion, long nowNanos, boolean paused) {
        var query = new HashMap<String, Double>();
        var values = new HashMap<String, MolangValue>();
        var compatibility = COMPATIBILITY.sample(new CompatibilityContext(client, entity, preview));
        values.putAll(compatibility.values());
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 position = entity.getPosition(client.getTimer().getGameTimeDeltaPartialTick(false));
        var movement = motion.sample(nowNanos, paused, position.x, position.y, position.z);
        var head = new CoreHeadSnapshot(entity.getYHeadRot() - entity.yBodyRot, entity.getXRot());
        query.put("ground_speed", CoreQuerySampling.groundSpeed(velocity.x, velocity.z));
        query.put("vertical_speed", movement.verticalSpeed());
        query.put("health", (double) entity.getHealth());
        query.put("max_health", (double) entity.getMaxHealth());
        query.put("is_on_ground", entity.onGround() ? 1.0 : 0.0);
        query.put("is_sneaking", CoreQuerySampling.sneaking(entity.onGround(), entity.getPose() == Pose.CROUCHING) ? 1.0 : 0.0);
        query.put("is_sprinting", entity.isSprinting() ? 1.0 : 0.0);
        query.put("is_swimming", entity.isSwimming() ? 1.0 : 0.0);
        query.put("is_in_water", entity.isInWater() ? 1.0 : 0.0);
        query.put("is_alive", entity.isAlive() ? 1.0 : 0.0);
        query.put("is_using_item", entity.isUsingItem() ? 1.0 : 0.0);
        query.put("is_riding", entity.isPassenger() ? 1.0 : 0.0);
        // d253 QueryBinding intentionally exposes net head yaw under x, pitch under y.
        query.put("head_x_rotation", head.queryHeadXRotation());
        query.put("head_y_rotation", head.queryHeadYRotation());
        query.put("time_of_day", CoreQuerySampling.timeOfDay(entity.level().getDayTime()));
        query.put("moon_phase", (double) entity.level().getMoonPhase());
        query.put("life_time", entity.tickCount / 20.0);
        // Direct d253 QueryBinding getters. Controller completion and local tracker yaw are separate contexts.
        float partial = client.getTimer().getGameTimeDeltaPartialTick(false);
        query.put("actor_count", (double) client.level.getEntityCount());
        query.put("time_stamp", (double) entity.level().getDayTime());
        if (entity != client.player) query.put("yaw_speed", (double) (20 * (entity.getYRot() - entity.yRotO)));
        query.put("cardinal_facing_2d", (double) entity.getDirection().get3DDataValue());
        query.put("distance_from_camera", client.gameRenderer.getMainCamera().getPosition().distanceTo(entity.position()));
        query.put("eye_target_x_rotation", (double) entity.getViewXRot(partial));
        query.put("eye_target_y_rotation", (double) entity.getViewYRot(partial));
        query.put("modified_distance_moved", (double) entity.walkDist);
        query.put("walk_distance", (double) entity.moveDist);
        query.put("has_rider", entity.isVehicle() ? 1.0 : 0.0);
        query.put("is_first_person", !preview && entity == client.player && client.options.getCameraType().isFirstPerson() ? 1.0 : 0.0);
        query.put("is_in_water_or_rain", entity.isInWaterRainOrBubble() ? 1.0 : 0.0);
        query.put("is_on_fire", entity.isOnFire() ? 1.0 : 0.0);
        query.put("is_spectator", entity.isSpectator() ? 1.0 : 0.0);
        query.put("body_x_rotation", (double) Mth.lerp(partial, entity.xRotO, entity.getXRot()));
        query.put("body_y_rotation", CoreQuerySampling.bodyYaw(entity.yBodyRotO, entity.yBodyRot, partial));
        query.put("hurt_time", (double) entity.hurtTime);
        query.put("is_eating", entity.getUseItem().getUseAnimation() == UseAnim.EAT ? 1.0 : 0.0);
        query.put("is_playing_dead", entity.isDeadOrDying() ? 1.0 : 0.0);
        query.put("is_sleeping", entity.isSleeping() ? 1.0 : 0.0);
        query.put("item_in_use_duration", entity.getTicksUsingItem() / 20.0);
        query.put("item_max_use_duration", entity.getUseItem().isEmpty() ? 0.0 : entity.getUseItem().getUseDuration(entity) / 20.0);
        query.put("item_remaining_use_duration", entity.getUseItemRemainingTicks() / 20.0);
        boolean cosmeticArmorInstalled = COMPATIBILITY.isInstalled("cosmeticarmorreworked");
        values.put("host.compatibility.cosmeticarmor", MolangValue.bool(cosmeticArmorInstalled));
        var queryEquipment = queryResolver(client, entity, cosmeticArmorInstalled);
        int armorCount = 0;
        // d253 has six slots; 1.21.1 BODY is deliberately excluded from this source contract.
        for (var slot : java.util.List.of(CoreQueryFunctions.Slot.HEAD, CoreQueryFunctions.Slot.CHEST,
                CoreQueryFunctions.Slot.LEGS, CoreQueryFunctions.Slot.FEET))
            if (!queryEquipment.equipment(slot).empty()) armorCount++;
        query.put("equipment_count", (double) armorCount);
        if (entity instanceof Player player) {
            query.put("player_level", (double) player.experienceLevel);
            query.put("is_jumping", !player.getAbilities().flying && !player.isPassenger() && !player.onGround() && !player.isInWater() ? 1.0 : 0.0);
            float dx = (float) (Mth.lerp(partial, player.xCloakO, player.xCloak) - Mth.lerp(partial, player.xo, player.getX()));
            float dy = (float) (Mth.lerp(partial, player.yCloakO, player.yCloak) - Mth.lerp(partial, player.yo, player.getY()));
            float dz = (float) (Mth.lerp(partial, player.zCloakO, player.zCloak) - Mth.lerp(partial, player.zo, player.getZ()));
            float body = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO);
            query.put("cape_flap_amount", (double) CoreQuerySampling.capeFlap(dx, dy, dz,
                    Mth.sin(body * ((float) Math.PI / 180)), -Mth.cos(body * ((float) Math.PI / 180)),
                    Mth.sin(Mth.lerp(partial, player.walkDistO, player.walkDist) * 6),
                    Mth.lerp(partial, player.oBob, player.bob), player.isCrouching()));
        }
        if (entity instanceof AbstractClientPlayer player) {
            // 1.21.1's immutable PlayerSkin carries the loaded cape texture (older reference has separate cape getters).
            query.put("has_cape", !player.isInvisible() && player.isModelPartShown(PlayerModelPart.CAPE)
                    && player.getSkin().capeTexture() != null ? 1.0 : 0.0);
        }
        values.put("ysm.head_pitch", MolangValue.number(entity.getXRot()));
        values.put("ysm.head_yaw", MolangValue.number(entity.getYHeadRot() - entity.yBodyRot));
        values.put("ysm.dimension_name", MolangValue.text(entity.level().dimension().location().toString()));
        values.put("ysm.fps", MolangValue.number(client.getFps()));
        values.put("ysm.time_delta", MolangValue.number(1.0 / 60));
        values.put("ysm.block_light", MolangValue.number(entity.level().getBrightness(LightLayer.BLOCK, entity.blockPosition())));
        values.put("ysm.sky_light", MolangValue.number(entity.level().getBrightness(LightLayer.SKY, entity.blockPosition())));
        values.put("ysm.air_supply", MolangValue.number(entity.getAirSupply()));
        values.put("ysm.frozen_ticks", MolangValue.number(entity.getTicksFrozen()));
        values.put("ysm.delta_movement_length", MolangValue.number(velocity.length()));
        values.put("ysm.armor_value", MolangValue.number(entity.getArmorValue()));
        values.put("ysm.hurt_time", MolangValue.number(entity.hurtTime));
        values.put("ysm.food_level", MolangValue.number(entity instanceof Player player ? player.getFoodData().getFoodLevel() : 20));
        values.put("query.position", vector(position));
        values.put("query.position_delta", vector(new Vec3(movement.dx(), movement.dy(), movement.dz())));
        values.put("ysm.entity_type", MolangValue.text(entity instanceof Player ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
        values.put("ysm.is_player", MolangValue.bool(entity instanceof Player));
        values.put("ysm.is_passenger", MolangValue.bool(entity.isPassenger()));
        values.put("ysm.is_sleep", MolangValue.bool(entity.getPose() == Pose.SLEEPING));
        values.put("ysm.is_sneak", MolangValue.bool(entity.onGround() && entity.getPose() == Pose.CROUCHING));
        values.put("ysm.is_riptide", MolangValue.bool(entity.isAutoSpinAttack()));
        values.put("ysm.on_ladder", MolangValue.bool(entity.onClimbable()));
        values.put("ysm.eye_in_water", MolangValue.bool(entity.isUnderWater()));
        values.put("ysm.rendering_in_inventory", MolangValue.bool(preview));
        values.put("ysm.rendering_in_paperdoll", MolangValue.bool(false));
        values.put("ysm.swinging", MolangValue.bool(entity.swinging));
        values.put("ysm.swing_time", MolangValue.number(entity.swingTime));
        values.put("ysm.attack_time", MolangValue.number(entity.getAttackAnim(0)));
        values.put("ysm.person_view", MolangValue.number(preview ? 2 : client.options.getCameraType().ordinal()));
        if (client.player == entity) {
            values.put("ysm.input_vertical", MolangValue.number(client.player.input.forwardImpulse));
            values.put("ysm.input_horizontal", MolangValue.number(client.player.input.leftImpulse));
        }
        for (var slot : EquipmentSlot.values()) {
            String name = switch (slot) {
                case MAINHAND -> "mainhand"; case OFFHAND -> "offhand"; case HEAD -> "helmet";
                case CHEST -> "chest_plate"; case LEGS -> "leggings"; case FEET -> "boots";
                default -> null;
            };
            if (name != null) {
                var stack = entity.getItemBySlot(slot);
                values.put("ysm.has_" + name, MolangValue.number(stack.isEmpty() ? 0 : 1));
                values.put("host.equipment." + slot.getName(), MolangValue.text(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            }
        }
        boolean moving = Math.abs(entity.walkAnimation.speed(client.getTimer().getGameTimeDeltaPartialTick(false))) > 0.05f;
        var conditions = new java.util.HashSet<String>();
        if (entity.isDeadOrDying()) conditions.add("death");
        if (entity.isAutoSpinAttack()) conditions.add("riptide");
        if (entity.getPose() == Pose.SLEEPING) conditions.add("sleep");
        if (entity.isSwimming()) conditions.add("swim");
        if (entity.getPose() == Pose.SWIMMING) {
            conditions.add("climbing");
            if (moving) conditions.add("climb");
        }
        double vertical = 20 * (float) (entity.getY() - entity.yo);
        if (entity.onClimbable()) conditions.add(vertical > 0 ? "ladder_up" : vertical < 0 ? "ladder_down" : "ladder_stillness");
        if (entity instanceof Player player && player.getAbilities().flying) conditions.add("fly");
        if (entity.getPose() == Pose.FALL_FLYING && entity.isFallFlying()) conditions.add("elytra_fly");
        if (entity.isInWater() && !entity.onGround()) conditions.add("swim_stand");
        if (entity.hurtTime > 0) conditions.add("attacked");
        if (!entity.onGround() && !entity.isInWater()) conditions.add("jump");
        if (entity.onGround() && entity.getPose() == Pose.CROUCHING) {
            conditions.add("sneaking");
            if (moving) conditions.add("sneak");
        }
        if (entity.onGround() && entity.isSprinting()) conditions.add("run");
        if (entity.onGround() && moving) conditions.add("walk");
        var vehicle = entity.getVehicle();
        values.putAll(CoreVanillaController.snapshot(conditions, preview, vehicle != null && vehicle.isAlive(),
                compatibility.present(CoreCompatibilityDefaults.Integration.PARCOOL)));
        return new SceneInputs(query, new MolangBindings(values, Map.of()), !preview,
                client.hasSingleplayerServer() ? SceneInputs.SyncMode.LOCAL : SceneInputs.SyncMode.HOST);
    }

    private static MolangValue vector(Vec3 vector) {
        return MolangValue.struct(Map.of("x", MolangValue.number(vector.x), "y", MolangValue.number(vector.y), "z", MolangValue.number(vector.z)));
    }
}
