package com.micaftic.morpher.core.compat.api;

import net.minecraft.world.entity.player.Player;

/**
 * R11.1/R11.2 Compat API — 服务端模型目录 hook。
 *
 * <p>核心只定义 hook；具体实现由 adapter 提供（{@code ServerModelManagerService}），
 * 经 {@link CompatServices} 注册。兼容逻辑（如 TLM {@code MaidModelSync}）只依赖本接口，
 * 不直接访问 {@code ServerModelManager} 内部静态。</p>
 */
public interface MaidModelService {

    /** 服务器模型目录是否包含该模型。 */
    boolean containsModel(String modelId);

    /** 玩家是否有权使用该模型（认证模型需玩家持有）。 */
    boolean isAuthorized(String modelId, Player player);

    /** 解析模型默认纹理（请求纹理缺失时回退默认）；不可用时返回 null。 */
    String resolveTextureOrDefault(String modelId, String requestedTexture);

    /** 未注册服务时的 no-op 默认（避免空指针）。 */
    MaidModelService NONE = new MaidModelService() {
        @Override
        public boolean containsModel(String modelId) {
            return false;
        }

        @Override
        public boolean isAuthorized(String modelId, Player player) {
            return false;
        }

        @Override
        public String resolveTextureOrDefault(String modelId, String requestedTexture) {
            return null;
        }
    };
}
