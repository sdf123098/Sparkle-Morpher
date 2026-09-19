package com.micaftic.morpher.fakeplayer;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.util.PlayerDataSaveBridge;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.util.YSMMessageFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-authoritative application service for the fake-player manager GUI. */
public final class FakePlayerAppearanceService {

    private FakePlayerAppearanceService() {
    }

    public static boolean apply(ServerPlayer actor, UUID targetUuid, String modelId, String requestedTextureId) {
        if (actor == null || targetUuid == null || !ConfigPolicies.network().canSwitchModel()
                || !YSMMessageFormatter.hasPermission(actor, 2)) {
            send(actor, "SPM: 你没有权限修改假人外观。");
            return false;
        }
        MinecraftServer server = actor.serverLevel().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(targetUuid);
        if (target == null || !FakePlayerTargetRules.isFakePlayer(target)) {
            send(actor, "SPM: 目标不是可管理的假人，或假人已经离线。");
            return false;
        }
        var info = ServerModelManager.getServerModelInfo().get(modelId);
        if (info == null || info.getModelInfo().getTextures().isEmpty()) {
            send(actor, "SPM: 服务端没有这个模型：" + modelId);
            return false;
        }
        String textureId = "-".equals(requestedTextureId)
                ? info.getLoadedModelData().getModelProperties().getDefaultTexture()
                : requestedTextureId;
        textureId = ServerModelManager.resolveTextureOrDefault(modelId, textureId);
        if (textureId == null) {
            send(actor, "SPM: 模型没有可用纹理：" + modelId);
            return false;
        }
        String finalTextureId = textureId;
        ModelInfoCapability.get(target).ifPresentOrElse(cap -> {
            cap.setModelAndTexture(modelId, finalTextureId);
            cap.setMandatory(true);
            cap.stopAnimation(target);
            cap.markDirty();
            PlayerModelSelectionStore.saveCurrentSelection(target, cap);
            PlayerDataSaveBridge.save(target);
            FakePlayerAppearanceBroadcast.publish(target, modelId, finalTextureId, cap.isDisabled());
            send(actor, "SPM: 已将假人 " + target.getScoreboardName() + " 设置为 " + modelId + "。 ");
        }, () -> send(actor, "SPM: 目标假人没有可用的模型能力。"));
        return true;
    }

    private static void send(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
