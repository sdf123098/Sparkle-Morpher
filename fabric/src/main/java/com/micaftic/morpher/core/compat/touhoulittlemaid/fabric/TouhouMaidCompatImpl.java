package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidModelSync;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.FeedbackData;
import com.micaftic.morpher.network.message.S2CSyncProjectileModelPacket;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.util.data.OrderedStringMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public final class TouhouMaidCompatImpl {

    private TouhouMaidCompatImpl() {
    }

    public static boolean isLoaded() {
        return TouhouLittleMaidAccess.isLoaded();
    }

    public static void init() {
    }

    public static boolean isMaidEntity(Entity entity) {
        return TouhouLittleMaidAccess.isMaid(entity);
    }

    public static void handleProjectileOwner(Projectile projectile, Entity entity) {
        VehicleModelCapability.get(entity).filter(VehicleModelCapability::isInitialized).ifPresent(maidState ->
                ProjectileModelCapability.get(projectile).ifPresent(projectileState -> {
                    projectileState.setModel(maidState.getOwnerModelId(),
                            new Object2FloatOpenHashMap<>(maidState.getMolangVars()));
                    NetworkHandler.sendToTrackingEntity(
                            new S2CSyncProjectileModelPacket(projectile.getId(), projectileState), projectile);
                }));
    }

    public static void registerAnimationRoulette(Entity entity, String str, int i) {
        VehicleModelCapability.get(entity).filter(VehicleModelCapability::isInitialized).ifPresent(state -> {
            if (i == -1) {
                state.setRouletteAnimation("");
                MaidModelSync.syncNow(entity, state);
                return;
            }
            ServerModelManager.getModelDefinition(state.getOwnerModelId()).ifPresent(data -> {
                OrderedStringMap<String, String> animations;
                ModelProperties properties = data.getLoadedModelData().getModelProperties();
                Map<String, OrderedStringMap<String, String>> classified = properties.getExtraAnimationClassify();
                if (StringUtils.isNotBlank(str) && classified.containsKey(str)) {
                    animations = classified.get(str);
                } else {
                    animations = properties.getExtraAnimation();
                }
                if (i >= 0 && i < animations.size()) {
                    state.setRouletteAnimation(animations.getKeyAt(i));
                    MaidModelSync.syncNow(entity, state);
                }
            });
        });
    }

    public static void applyFeedback(Entity entity, FeedbackData message) {
        VehicleModelCapability.get(entity).filter(VehicleModelCapability::isInitialized).ifPresent(state -> {
            state.getMolangVars().putAll(message.stringValues());
            MaidModelSync.syncNow(entity, state);
        });
    }

}
