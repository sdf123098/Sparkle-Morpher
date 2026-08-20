package com.micaftic.morpher.client.event;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.capability.client.PlayerCapabilityClientStore;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientResourceLifecycleEvent {
    private static final Map<EntityRenderState, CapturedEntity> ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private static final int ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES_MAX = 8192;

    private ClientResourceLifecycleEvent() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> cleanup("client disconnect"));
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register((oldPlayer, newPlayer) -> cleanupAfterWorldChange("client player respawn"));
    }

    private static void cleanup(String reason) {
        GpuRenderPath.disposeAllMeshes(reason);
        AudioStreamCache.clearAll(reason);
        BlurStack.disposeAll(reason);
        PlayerCapabilityClientStore.clear();
        ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.clear();
    }

    private static void cleanupAfterWorldChange(String reason) {
        PlayerCapabilityClientStore.clear();
        ClientModelManager.restorePersistedModelSelection();
        ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.clear();
    }

    public static void captureEntity(EntityRenderState state, Entity entity, float partialTick, int packedLight) {
        // 异常保护：GUI 预览等路径可能只 extract 不 submit，防止映射无限增长。
        if (ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.size() > ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES_MAX) {
            ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.clear();
        }
        ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.put(state, new CapturedEntity(entity, partialTick, packedLight));
    }

    public static CapturedEntity consumeCapturedEntity(EntityRenderState state) {
        return ENTITY_RENDER_DISPATCHER_CAPTURED_ENTITIES.remove(state);
    }

    public record CapturedEntity(Entity entity, float partialTick, int packedLight) {
    }
}
