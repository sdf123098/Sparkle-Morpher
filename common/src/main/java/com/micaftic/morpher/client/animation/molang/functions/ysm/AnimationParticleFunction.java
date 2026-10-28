package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.client.animation.particle.AnimationParticleQueue;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.util.ParticleEffectUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Internal target of imported particle events; the third argument is deliberately lazy. */
public final class AnimationParticleFunction extends EntityFunction {
    @Override
    public boolean validateArgumentSize(int size) { return size == 3; }

    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        IContext<Entity> owner = context.entity();
        if (!owner.isClientSide() || owner.geoInstance().hasCustomTexture()
                || (ModelPreviewRenderer.isPreview() && !ModelPreviewRenderer.isWorldRender())) return false;
        try {
            String effect = decode(arguments.getAsString(context, 0));
            String locator = decode(arguments.getAsString(context, 1));
            var model = owner.geoInstance().getCurrentModel();
            if (model == null) return false;
            if (!locator.isEmpty() && model.getGeoModel().topLevelBones().stream()
                    .noneMatch(bone -> bone.getName().equals(locator))) {
                owner.logWarning("Unknown animation particle locator: {}", locator);
                return false;
            }
            Entity entity = owner.entity();
            if (!ParticleEffectUtil.canSpawnAnimationParticle(entity, effect)) return false;
            // Suppressed historical/inactive events must never execute the emitter's startup script.
            arguments.getValue(context, 2);
            float partialTick = owner.animationEvent().getPartialTick();
            java.util.function.Consumer<Vector3f> emitter = offset -> {
                var interpolated = entity.getPosition(partialTick);
                var worldOffset = new Vector3d(offset.x + interpolated.x - entity.getX(),
                        offset.y + interpolated.y - entity.getY(), offset.z + interpolated.z - entity.getZ());
                try {
                    ParticleEffectUtil.spawnAnimationParticle(entity, effect, worldOffset, owner.random());
                } catch (Exception invalidEffect) {
                    owner.logWarning("Cannot spawn animation particle {}: {}", effect, invalidEffect.getMessage());
                }
            };
            if (locator.isEmpty()) {
                emitter.accept(new Vector3f());
                return true;
            }
            long key = AnimationParticleQueue.sampleKey(owner.animationEvent().getTickCount(),
                    owner.animationEvent().getFrameTime());
            return model.animationParticles.enqueue(key, locator, emitter);
        } catch (Exception invalidEffect) {
            owner.logWarning("Invalid animation particle event: {}", invalidEffect.getMessage());
            return false;
        }
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
