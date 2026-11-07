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
import java.util.HashMap;
import java.util.Map;

/** New Host sampler: Minecraft objects stop here; only immutable values reach the Core. */
public final class MinecraftHostSnapshotSampler {
    private MinecraftHostSnapshotSampler() { }
    private static java.util.function.BooleanSupplier parCoolPresent = () -> {
        throw new IllegalStateException("Core Host loader compatibility presence sampler was not registered");
    };
    public static void registerParCoolPresence(java.util.function.BooleanSupplier presence) {
        parCoolPresent = java.util.Objects.requireNonNull(presence);
    }

    public static SceneInputs sample(Minecraft client, LivingEntity entity, boolean preview) {
        var query = new HashMap<String, Double>();
        var values = new HashMap<String, MolangValue>();
        Vec3 velocity = entity.getDeltaMovement();
        query.put("ground_speed", velocity.horizontalDistance() * 20);
        query.put("vertical_speed", velocity.y * 20);
        query.put("health", (double) entity.getHealth());
        query.put("max_health", (double) entity.getMaxHealth());
        query.put("is_on_ground", entity.onGround() ? 1.0 : 0.0);
        query.put("is_sneaking", entity.isCrouching() ? 1.0 : 0.0);
        query.put("is_sprinting", entity.isSprinting() ? 1.0 : 0.0);
        query.put("is_swimming", entity.isSwimming() ? 1.0 : 0.0);
        query.put("is_in_water", entity.isInWater() ? 1.0 : 0.0);
        query.put("is_alive", entity.isAlive() ? 1.0 : 0.0);
        query.put("is_using_item", entity.isUsingItem() ? 1.0 : 0.0);
        query.put("is_riding", entity.isPassenger() ? 1.0 : 0.0);
        query.put("head_x_rotation", (double) entity.getXRot());
        query.put("head_y_rotation", (double) (entity.getYHeadRot() - entity.yBodyRot));
        query.put("time_of_day", (entity.level().getDayTime() % 24000L) / 24000.0);
        query.put("moon_phase", (double) entity.level().getMoonPhase());
        query.put("life_time", entity.tickCount / 20.0);
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
        values.put("query.position", vector(entity.position()));
        values.put("query.position_delta", vector(velocity));
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
        values.putAll(CoreVanillaController.snapshot(conditions, preview, vehicle != null && vehicle.isAlive(), parCoolPresent.getAsBoolean()));
        return new SceneInputs(query, new MolangBindings(values, Map.of()), !preview,
                client.hasSingleplayerServer() ? SceneInputs.SyncMode.LOCAL : SceneInputs.SyncMode.HOST);
    }

    private static MolangValue vector(Vec3 vector) {
        return MolangValue.struct(Map.of("x", MolangValue.number(vector.x), "y", MolangValue.number(vector.y), "z", MolangValue.number(vector.z)));
    }
}
