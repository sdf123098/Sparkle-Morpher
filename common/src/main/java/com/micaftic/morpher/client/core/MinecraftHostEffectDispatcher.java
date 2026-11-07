package com.micaftic.morpher.client.core;

import com.mojang.brigadier.StringReader;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Client-thread effects owned only by the new Core lane; never executes arbitrary commands. */
public final class MinecraftHostEffectDispatcher implements AutoCloseable {
    private final Map<String, SoundInstance> sounds = new HashMap<>();

    /** Per-attempt projection makes play/stop return values observe earlier accepted occurrences. */
    public final class Requests {
        private final java.util.Set<String> projected = new java.util.HashSet<>(sounds.keySet());
        public MolangValue sample(MolangBindings.Call call) {
            MolangValue validated = MinecraftHostEffectDispatcher.this.sample(call);
            return switch (call.name()) {
                case "ysm.play_sound" -> {
                    if (validated.truth()) projected.add(key(call.arguments().get(0)));
                    yield validated;
                }
                case "ysm.stop_sound" -> MolangValue.bool(!invalidNumericKey(call.arguments().get(0)) && projected.remove(key(call.arguments().get(0))));
                case "ysm.stop_all_sounds" -> { projected.clear(); yield validated; }
                default -> validated;
            };
        }
    }
    public Requests requests() {
        var manager = Minecraft.getInstance().getSoundManager();
        sounds.entrySet().removeIf(entry -> !manager.isActive(entry.getValue()));
        return new Requests();
    }

    /** Reject unsupported commands before any irreversible effect in the batch is dispatched. */
    public void validateBatch(List<EffectCommand> effects) {
        for (var effect : effects) {
            if (CoreBoneEffects.handles(effect)) continue; // Already published as immutable Host pose channels.
            if (effect.kind().equals("sound") && effect.arguments().isEmpty()) {
                var resource = ResourceLocation.tryParse(effect.resource());
                if (resource == null || !BuiltInRegistries.SOUND_EVENT.containsKey(resource))
                    throw new IllegalArgumentException("Unknown Core timeline sound: " + effect.resource());
            } else sample(new MolangBindings.Call(effect.resource(), effect.arguments()));
        }
    }

    /** Snapshot-side request resolution. Validation has no game-side effect. */
    public MolangValue sample(MolangBindings.Call call) {
        var args = call.arguments();
        return switch (call.name()) {
            case "ysm.play_sound" -> {
                requireArity(args, 2, 5);
                if (invalidNumericKey(args.get(0))) yield MolangValue.bool(false);
                if (text(args.get(1)).isBlank()) yield MolangValue.bool(false);
                var sound = ResourceLocation.tryParse(text(args.get(1)));
                int mode = args.size() > 2 ? (int) args.get(2).number() : 0;
                if (mode != 0) throw new IllegalArgumentException("Core Host sound modes other than positional mode 0 require an explicit adapter");
                yield MolangValue.bool(sound != null && BuiltInRegistries.SOUND_EVENT.containsKey(sound));
            }
            case "ysm.stop_sound" -> {
                requireArity(args, 1, 2);
                if (invalidNumericKey(args.get(0))) yield MolangValue.bool(false);
                if (args.size() == 2 && args.get(1).truth()) throw new IllegalArgumentException("Core Host global sound ownership is not implemented");
                yield MolangValue.bool(sounds.containsKey(key(args.get(0))));
            }
            case "ysm.stop_all_sounds" -> {
                requireArity(args, 0, 1);
                if (!args.isEmpty() && args.get(0).truth()) throw new IllegalArgumentException("Core Host global sound ownership is not implemented");
                yield MolangValue.bool(true);
            }
            case "ysm.particle", "ysm.abs_particle" -> {
                requireArity(args, 1, 10);
                if (text(args.get(0)).isBlank()) yield MolangValue.bool(false);
                particle(text(args.get(0)));
                if (args.size() > 8 && args.get(8).number() > 256) throw new IllegalArgumentException("Core Host particle count exceeds 256 per occurrence");
                if (args.size() > 9 && args.get(9).number() > 12000) throw new IllegalArgumentException("Core Host particle lifetime exceeds 12000 ticks");
                yield MolangValue.bool(!text(args.get(0)).isBlank());
            }
            default -> throw new IllegalArgumentException("Unregistered Core Host effect request: " + call.name());
        };
    }

    public void commit(Minecraft client, LivingEntity entity, EffectCommand effect, BooleanSupplier stillCurrent) {
        if (!client.isSameThread()) throw new IllegalStateException("Core effects require the client thread");
        if (!stillCurrent.getAsBoolean()) return;
        if (CoreBoneEffects.handles(effect)) return;
        var args = effect.arguments();
        if (effect.kind().equals("sound") && args.isEmpty()) {
            play(client, entity, "timeline:" + effect.sequence(), effect.resource(), 1, 1);
            return;
        }
        switch (effect.resource()) {
            case "ysm.play_sound" -> {
                if (sample(new MolangBindings.Call(effect.resource(), args)).truth()) {
                    play(client, entity, key(args.get(0)), text(args.get(1)), (float) number(args, 3, 1), (float) number(args, 4, 1));
                }
            }
            case "ysm.stop_sound" -> {
                if (invalidNumericKey(args.get(0))) return;
                var sound = sounds.remove(key(args.get(0)));
                if (sound != null) client.getSoundManager().stop(sound);
            }
            case "ysm.stop_all_sounds" -> close();
            case "ysm.particle", "ysm.abs_particle" -> {
                if (text(args.get(0)).isBlank()) return;
                ParticleOptions options = particle(text(args.get(0)));
                int authoredCount = Math.max(0, Math.min(256, (int) number(args, 8, 0)));
                int count = Math.max(1, authoredCount);
                double speed = number(args, 7, 0);
                int lifetime = Math.max(1, Math.min(12000, (int) number(args, 9, 20)));
                var random = entity.getRandom();
                for (int i = 0; i < count && stillCurrent.getAsBoolean(); i++) {
                    double dx = number(args, 4, 0), dy = number(args, 5, 0), dz = number(args, 6, 0);
                    Vec3 offset = new Vec3(number(args, 1, 0), number(args, 2, 0), number(args, 3, 0));
                    if (authoredCount > 0) offset = offset.add(random.nextGaussian() * dx, random.nextGaussian() * dy, random.nextGaussian() * dz);
                    if (effect.resource().equals("ysm.particle")) offset = offset.yRot((float) (-(authoredCount == 0 ? entity.yBodyRot : entity.getYRot()) * Math.PI / 180));
                    var created = client.particleEngine.createParticle(options, entity.getX() + offset.x,
                            entity.getY() + offset.y, entity.getZ() + offset.z,
                            speed * (authoredCount == 0 ? dx : random.nextGaussian()),
                            speed * (authoredCount == 0 ? dy : random.nextGaussian()),
                            speed * (authoredCount == 0 ? dz : random.nextGaussian()));
                    if (created != null) created.setLifetime(lifetime);
                }
            }
            default -> throw new IllegalArgumentException("Unregistered Core Host effect: " + effect.kind() + "/" + effect.resource());
        }
    }

    private void play(Minecraft client, LivingEntity entity, String key, String resource, float volume, float pitch) {
        var id = ResourceLocation.tryParse(resource);
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) throw new IllegalArgumentException("Unknown Core sound resource: " + resource);
        var previous = sounds.remove(key);
        if (previous != null) client.getSoundManager().stop(previous);
        var instance = new SimpleSoundInstance(BuiltInRegistries.SOUND_EVENT.get(id), SoundSource.PLAYERS,
                Math.clamp(volume, 0.001f, 1000), Math.clamp(pitch, 0.001f, 1000), RandomSource.create(), entity.getX(), entity.getY(), entity.getZ());
        sounds.put(key, instance);
        client.getSoundManager().play(instance);
    }

    @Override public void close() {
        var manager = Minecraft.getInstance().getSoundManager();
        sounds.values().forEach(manager::stop);
        sounds.clear();
    }
    private static ParticleOptions particle(String value) {
        var level = Minecraft.getInstance().level;
        if (level == null) throw new IllegalStateException("Core particle parsing requires the current world registry provider");
        try { return ParticleArgument.readParticle(new StringReader(value), level.registryAccess()); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new IllegalArgumentException("Invalid Core particle resource", invalid); }
    }
    private static String text(MolangValue value) {
        if (value instanceof MolangValue.TextValue text) return text.value();
        throw new IllegalArgumentException("Core effect expects text");
    }
    private static String key(MolangValue value) {
        if (value instanceof MolangValue.TextValue text) return "text:" + text.value();
        if (value instanceof MolangValue.NumberValue number && number.value() >= 0) return "number:" + (int) number.value();
        throw new IllegalArgumentException("Invalid Core sound owner key");
    }
    private static boolean invalidNumericKey(MolangValue value) {
        return value instanceof MolangValue.NumberValue number && number.value() < 0;
    }
    private static double number(List<MolangValue> args, int index, double fallback) {
        return index < args.size() ? args.get(index).number() : fallback;
    }
    private static void requireArity(List<MolangValue> args, int min, int max) {
        if (args.size() < min || args.size() > max) throw new IllegalArgumentException("Invalid Core Host effect arity");
    }
}
