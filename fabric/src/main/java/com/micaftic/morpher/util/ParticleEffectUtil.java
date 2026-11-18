package com.micaftic.morpher.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.spm.core.scene.MolangValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;

import java.util.concurrent.ExecutionException;

@Environment(EnvType.CLIENT)
public class ParticleEffectUtil {
    private static final Cache<String, ParticleOptions> particleCache = CacheBuilder.newBuilder().expireAfterAccess(java.time.Duration.ofSeconds(60)).build();

    public static boolean canSpawnAnimationParticle(Entity entity, String particleId) throws ExecutionException {
        return particleCache.get(particleId, () ->
                ParticleArgument.readParticle(new StringReader(particleId), entity.level().registryAccess())) != null;
    }

    public static void spawnAnimationParticle(Entity entity, String particleId, Vector3d offset, RandomSource random)
            throws ExecutionException, CommandSyntaxException {
        spawnParticles(entity, particleId, offset, new Vector3d(), 0, 0, 20, true, random);
    }

    public static boolean handleParticle(Entity entity, List<MolangValue> arguments, boolean isAbsolute, RandomSource random)
            throws ExecutionException, CommandSyntaxException {
        if (arguments.isEmpty()) {
            return false;
        }
        String particleId = arguments.get(0) instanceof MolangValue.TextValue text ? text.value() : "";
        if (StringUtils.isBlank(particleId)) {
            return false;
        }

        Vector3d offset = new Vector3d(0.0d, 0.0d, 0.0d);
        Vector3d delta = new Vector3d(0.0d, 0.0d, 0.0d);
        double speed = 0.0d;
        int count = 0;
        int lifetime = 20;
        int argCount = arguments.size();

        if (argCount > 1) offset.x = arguments.get(1).number();
        if (argCount > 2) offset.y = arguments.get(2).number();
        if (argCount > 3) offset.z = arguments.get(3).number();
        if (argCount > 4) delta.x = arguments.get(4).number();
        if (argCount > 5) delta.y = arguments.get(5).number();
        if (argCount > 6) delta.z = arguments.get(6).number();
        if (argCount > 7) speed = arguments.get(7).number();
        if (argCount > 8) count = Math.max((int) arguments.get(8).number(), 0);
        if (argCount > 9) lifetime = Math.max((int) arguments.get(9).number(), 1);

        spawnParticles(entity, particleId, offset, delta, speed, count, lifetime, isAbsolute, random);
        return true;
    }

    private static void spawnParticles(Entity entity, String particleId, Vector3d offset, Vector3d delta, double speed, int count, int lifetime, boolean isAbsolute, RandomSource random) throws ExecutionException, CommandSyntaxException {
        ParticleOptions particleOptions = particleCache.get(particleId, () -> {
            return ParticleArgument.readParticle(new StringReader(particleId), entity.level().registryAccess());
        });

        if (particleOptions == null) {
            return;
        }

        ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;

        if (count == 0) {
            Vec3 spawnPos = new Vec3(offset.x, offset.y, offset.z);
            if (!isAbsolute) {
                if (entity instanceof Player) {
                    spawnPos = spawnPos.yRot((-((Player) entity).yBodyRot) * 0.017453292f);
                } else {
                    spawnPos = spawnPos.yRot((-entity.getYRot()) * 0.017453292f);
                }
            }

            double x = entity.getX() + spawnPos.x();
            double y = entity.getY() + spawnPos.y();
            double z = entity.getZ() + spawnPos.z();
            double velocityX = speed * delta.x;
            double velocityY = speed * delta.y;
            double velocityZ = speed * delta.z;

            ((Executor) Minecraft.getInstance()).execute(() -> emitSingleParticle(particleEngine, particleOptions, x, y, z, velocityX, velocityY, velocityZ, lifetime));
            return;
        }

        for (int i = 0; i < count; i++) {
            emitParticle(entity, offset, delta, speed, lifetime, particleEngine, particleOptions, isAbsolute, random);
        }
    }

    private static void emitParticle(Entity entity, Vector3d offset, Vector3d delta, double speed, int lifetime, ParticleEngine particleEngine, ParticleOptions particleOptions, boolean isAbsolute, RandomSource random) {
        double spreadX = random.nextGaussian() * delta.x;
        double spreadY = random.nextGaussian() * delta.y;
        double spreadZ = random.nextGaussian() * delta.z;
        double velocityX = random.nextGaussian() * speed;
        double velocityY = random.nextGaussian() * speed;
        double velocityZ = random.nextGaussian() * speed;

        Vec3 spawnPos = new Vec3(offset.x + spreadX, offset.y + spreadY, offset.z + spreadZ);

        if (!isAbsolute) {
            spawnPos = spawnPos.yRot((-entity.getYRot()) * 0.017453292f);
        }

        double x = entity.getX() + spawnPos.x();
        double y = entity.getY() + spawnPos.y();
        double z = entity.getZ() + spawnPos.z();

        ((Executor) Minecraft.getInstance()).execute(() -> {
            emitSingleParticle(particleEngine, particleOptions, x, y, z, velocityX, velocityY, velocityZ, lifetime);
        });
    }

    private static void emitSingleParticle(ParticleEngine particleEngine, ParticleOptions particleOptions, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int lifetime) {
        Particle particle = particleEngine.createParticle(particleOptions, x, y, z, velocityX, velocityY, velocityZ);
        if (particle != null) {
            particle.setLifetime(lifetime);
        }
    }
}
