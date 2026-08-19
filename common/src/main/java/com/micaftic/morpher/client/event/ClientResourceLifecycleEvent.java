package com.micaftic.morpher.client.event;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.architectury.event.events.client.ClientLifecycleEvent;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.capability.fabric.client.PlayerCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.ProjectileCapabilityClientStore;
import com.micaftic.morpher.capability.fabric.client.VehicleCapabilityClientStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientResourceLifecycleEvent {
    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_DISCONNECT.register(client -> cleanup("client disconnect"));
        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> cleanup("client stopping"));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> cleanupAfterWorldChange("client level changed"));
    }

    private static void cleanup(String reason) {
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
        PlayerCapabilityClientStore.clear(reason);
        ProjectileCapabilityClientStore.clear(reason);
        VehicleCapabilityClientStore.clear(reason);
    }

    private static void cleanupAfterWorldChange(String reason) {
        PlayerCapabilityClientStore.clear(reason);
        ProjectileCapabilityClientStore.clear(reason);
        VehicleCapabilityClientStore.clear(reason);
        ClientModelManager.restorePersistedModelSelection();
        ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.clear();

    }

    private static final Map<EntityRenderState, CapturedEntity> ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES = Collections.synchronizedMap(new IdentityHashMap<>());

    public record CapturedEntity(Entity entity, float partialTick, int packedLight) {}

    public static Map<EntityRenderState, CapturedEntity> getEntityRenderDispatcherCapturedEntities() {
        return ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES;
    }

}
