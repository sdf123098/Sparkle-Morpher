package com.micaftic.morpher.core.bridge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.render.BoneIds;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import dev.spm.core.api.BonePose;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.FormatHint;
import dev.spm.core.api.ImportRequest;
import dev.spm.core.api.ImportResult;
import dev.spm.core.api.ModelSource;
import dev.spm.core.api.ResourceBudget;
import dev.spm.core.api.ResourceKey;
import dev.spm.core.api.Skeleton;
import dev.spm.core.format.figura.FiguraAvatarRuntime;
import dev.spm.core.format.figura.FiguraAvatarRuntime.EffectCommand;
import dev.spm.core.format.figura.FiguraAvatarRuntime.FiguraFrame;
import dev.spm.core.format.figura.FiguraAvatarRuntime.HostSnapshot;
import dev.spm.core.lua.LuaBudget;
import dev.spm.core.parser.SpmModelParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host bridge binding an imported Figura archive ({@code avatar.json}, {@code .bbmodel},
 * {@code avatar.nbt}, and {@code .lua} scripts) to {@code spm-core}'s pure-Java MIT Lua 5.3 VM
 * ({@link FiguraAvatarRuntime}).
 */
public final class FiguraRuntimeBridge implements AutoCloseable {
    private final ImportResult imported;
    private final FiguraAvatarRuntime runtime;
    private final Map<String, Skeleton> skeletonsByKey;
    private long lastTickedGameTime = Long.MIN_VALUE;
    private boolean failed;

    private FiguraRuntimeBridge(ImportResult imported, FiguraAvatarRuntime runtime,
                                Map<String, Skeleton> skeletonsByKey) {
        this.imported = imported;
        this.runtime = runtime;
        this.skeletonsByKey = skeletonsByKey;
    }

    public static @Nullable FiguraRuntimeBridge create(byte @Nullable [] archiveBytes,
                                                       @Nullable String sourceName,
                                                       @Nullable String playerName) {
        if (archiveBytes == null || archiveBytes.length == 0) {
            return null;
        }
        String entry = (sourceName == null || sourceName.isBlank()) ? "avatar.zip" : sourceName;
        String safePlayer = (playerName == null || playerName.isBlank()) ? "Player" : playerName;
        CancellationToken cancellation = new CancellationToken();
        try {
            SpmModelParser parser = new SpmModelParser();
            ImportRequest request = new ImportRequest(
                    new ModelSource(
                            "figura:" + entry,
                            new ResourceKey(entry),
                            key -> new ByteArrayInputStream(archiveBytes)),
                    FormatHint.FIGURA,
                    DecodePolicy.localModel(),
                    new ResourceBudget(256L << 20),
                    cancellation);
            ImportResult imported = parser.importModel(request);
            Map<String, Skeleton> skeletons = new LinkedHashMap<>();
            for (ImportResult.Model m : imported.models()) {
                skeletons.put(m.key(), m.asset().document().skeleton());
            }
            FiguraAvatarRuntime runtime = FiguraAvatarRuntime.create(
                    imported,
                    LuaBudget.avatarDefault(cancellation),
                    HostSnapshot.idle(safePlayer));
            runtime.initialize();
            return new FiguraRuntimeBridge(imported, runtime, skeletons);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[SM][Figura] Failed to initialize Figura Lua runtime for '{}': {}", entry, t.toString());
            return null;
        }
    }

    public void tick(LivingEntity entity, boolean isHost) {
        if (failed || entity == null) {
            return;
        }
        long tickKey = entity.level() != null ? entity.level().getGameTime() : entity.tickCount;
        if (tickKey == lastTickedGameTime) {
            return;
        }
        lastTickedGameTime = tickKey;
        try {
            runtime.tick(sampleHostSnapshot(entity, isHost));
        } catch (Throwable t) {
            failed = true;
            YesSteveModel.LOGGER.warn("[SM][Figura] Lua tick terminated for {}: {}", entity.getName().getString(), t.toString());
        }
    }

    public @Nullable FiguraFrame renderAndApply(LivingEntity entity, boolean isHost, float partialTick,
                                                @Nullable ModelScenePose geoModel) {
        if (failed || entity == null) {
            return null;
        }
        try {
            FiguraFrame frame = runtime.render(partialTick, sampleHostSnapshot(entity, isHost));
            if (geoModel != null) {
                applyPoses(frame.modelPoses(), geoModel);
            }
            dispatchEffects(entity, frame.effects());
            for (String msg : frame.logs()) {
                YesSteveModel.LOGGER.debug("[SM][Figura][Lua] {}", msg);
            }
            return frame;
        } catch (Throwable t) {
            failed = true;
            YesSteveModel.LOGGER.warn("[SM][Figura] Lua render terminated for {}: {}", entity.getName().getString(), t.toString());
            return null;
        }
    }

    private void applyPoses(Map<String, BonePose> poses, ModelScenePose geoModel) {
        for (Map.Entry<String, BonePose> entry : poses.entrySet()) {
            Skeleton skeleton = skeletonsByKey.get(entry.getKey());
            if (skeleton == null) continue;
            BonePose pose = entry.getValue();
            List<String> names = skeleton.names();
            float[] rots = pose.rotations();
            float[] trans = pose.translations();
            float[] scales = pose.scales();
            boolean[] ownHidden = pose.ownHidden();
            boolean[] childHidden = pose.childrenHidden();
            for (int i = 0; i < names.size(); i++) {
                ModelBoneView bone = geoModel.getBone(BoneIds.id(names.get(i)));
                if (bone == null) continue;
                int v = i * 3;
                bone.setRotationX(bone.getRotationX() + rots[v]);
                bone.setRotationY(bone.getRotationY() + rots[v + 1]);
                bone.setRotationZ(bone.getRotationZ() + rots[v + 2]);
                bone.setPositionX(bone.getPositionX() - trans[v] * 16.0f);
                bone.setPositionY(bone.getPositionY() + trans[v + 1] * 16.0f);
                bone.setPositionZ(bone.getPositionZ() + trans[v + 2] * 16.0f);
                bone.setScaleX(bone.getScaleX() * scales[v]);
                bone.setScaleY(bone.getScaleY() * scales[v + 1]);
                bone.setScaleZ(bone.getScaleZ() * scales[v + 2]);
                if (ownHidden[i] || childHidden[i]) {
                    bone.setCubesHidden(ownHidden[i]);
                    bone.setChildrenHidden(childHidden[i]);
                }
            }
        }
    }

    private void dispatchEffects(LivingEntity entity, List<EffectCommand> effects) {
        if (effects.isEmpty() || entity.level() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (EffectCommand cmd : effects) {
            if ("sound".equals(cmd.kind())) {
                Object idObj = cmd.arguments().get("id");
                if (idObj instanceof String id && !id.isBlank()) {
                    Identifier loc = Identifier.tryParse(id);
                    if (loc != null) {
                        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(loc).map(Holder.Reference::value).orElse(null);
                        if (sound != null) {
                            float vol = numberArg(cmd.arguments().get("volume"), 1.0f);
                            float pitch = numberArg(cmd.arguments().get("pitch"), 1.0f);
                            entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                                    sound, SoundSource.PLAYERS, vol, pitch, false);
                        }
                    }
                }
            } else if ("ping".equals(cmd.kind()) && mc.getConnection() != null) {
                YesSteveModel.LOGGER.debug("[SM][Figura] Queued ping effect: {}", cmd.arguments());
            }
        }
    }

    private static float numberArg(@Nullable Object value, float fallback) {
        return value instanceof Number num ? num.floatValue() : fallback;
    }

    private static HostSnapshot sampleHostSnapshot(LivingEntity entity, boolean isHost) {
        Vec3 vel = entity.getDeltaMovement();
        Minecraft mc = Minecraft.getInstance();
        double worldTime = entity.level() != null ? entity.level().getGameTime() / 20.0 : entity.tickCount / 20.0;
        float rain = entity.level() != null ? entity.level().getRainLevel(1.0f) : 0.0f;
        float fps = mc != null ? mc.getFps() : 60f;
        return new HostSnapshot(
                entity.getX(), entity.getY(), entity.getZ(),
                vel.x, vel.y, vel.z,
                entity.getYRot(), entity.getXRot(), entity.yBodyRot,
                entity.getHealth(), entity.getMaxHealth(),
                entity.isCrouching(), entity.isSprinting(), entity.isSwimming(),
                entity.isFallFlying(), entity.onGround(), entity.swinging,
                entity.getName().getString(),
                entity.getUUID().toString(),
                isHost,
                worldTime, rain, fps,
                itemId(entity.getMainHandItem()),
                itemId(entity.getOffhandItem()));
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "minecraft:air";
    }

    @Override
    public void close() {
        try {
            imported.close();
        } catch (RuntimeException ignored) {
        }
    }
}
