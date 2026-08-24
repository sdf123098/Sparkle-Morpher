package com.micaftic.morpher.model;

import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.core.compat.api.MaidModelService;
import net.minecraft.world.entity.player.Player;

/**
 * R11.2 {@link MaidModelService} adapter：委托 {@link ServerModelManager} 服务器模型目录。
 *
 * <p>兼容逻辑（TLM MaidModelSync 等）经 {@link com.micaftic.morpher.core.compat.api.CompatServices}
 * 取本服务，不再直接访问 ServerModelManager 内部静态。</p>
 */
public final class ServerModelManagerService implements MaidModelService {

    public static final ServerModelManagerService INSTANCE = new ServerModelManagerService();

    private ServerModelManagerService() {
    }

    @Override
    public boolean containsModel(String modelId) {
        return ServerModelManager.getServerModelInfo().containsKey(modelId);
    }

    @Override
    public boolean isAuthorized(String modelId, Player player) {
        return !ServerModelManager.getAuthModels().contains(modelId)
                || AuthModelsCapability.get(player).map(cap -> cap.containsModel(modelId)).orElse(false);
    }

    @Override
    public String resolveTextureOrDefault(String modelId, String requestedTexture) {
        return ServerModelManager.resolveTextureOrDefault(modelId, requestedTexture);
    }
}
