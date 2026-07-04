package com.micaftic.morpher.client.network;

import com.google.common.collect.Sets;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.event.EntityJoinCallbackEvent;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.parser.ParseException;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import com.micaftic.morpher.network.message.C2SVersionCheckPacket;
import com.micaftic.morpher.network.message.S2CExecuteMolangPacket;
import com.micaftic.morpher.network.message.S2CModelSyncPayload;
import com.micaftic.morpher.network.message.S2CModelUploadResultPacket;
import com.micaftic.morpher.network.message.S2CModelUploadStartPacket;
import com.micaftic.morpher.network.message.S2CSetModelAndTexturePacket;
import com.micaftic.morpher.network.message.S2CSyncAnimationExpressionPacket;
import com.micaftic.morpher.network.message.S2CSyncAuthModelsPacket;
import com.micaftic.morpher.network.message.S2CSyncPlayerStatePacket;
import com.micaftic.morpher.network.message.S2CSyncProjectileModelPacket;
import com.micaftic.morpher.network.message.S2CSyncStarModelsPacket;
import com.micaftic.morpher.network.message.S2CSyncVehicleModelPacket;
import com.micaftic.morpher.network.message.S2CVersionCheckPacket;
import com.micaftic.morpher.util.LocalStarModelsStore;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.util.Set;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static boolean isClientConnected() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getConnection() != null;
    }

    public static boolean isLocalPlayer(Object player) {
        return player instanceof LocalPlayer;
    }

    public static void handleModelSync(Object obj, Connection connection) {
        S2CModelSyncPayload message = (S2CModelSyncPayload) obj;
        ByteBuffer data = message.getData();
        ClientModelManager.startSync(connection, data);
    }

    public static void handleModelUploadStart(Object obj) {
        S2CModelUploadStartPacket message = (S2CModelUploadStartPacket) obj;
        ModelUploadSession.onStartAck(message.uploadId(), message.status(), message.chunkSize(), message.maxTotalBytes(), message.chunksPerTick(), message.message());
    }

    public static void handleModelUploadResult(Object obj) {
        S2CModelUploadResultPacket message = (S2CModelUploadResultPacket) obj;
        ModelUploadSession.onResult(message.uploadId(), message.status(), message.modelId(), message.h1(), message.h2(), message.message());
    }

    public static void handleExecuteMolang(Object obj) {
        S2CExecuteMolangPacket message = (S2CExecuteMolangPacket) obj;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int entityId : message.getEntityIds()) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity instanceof Player) {
                PlayerCapability.get(entity).ifPresent(cap -> {
                    try {
                        cap.executeExpression(GeckoLibCache.parseSimpleExpression(message.getExpression()), true, false, null);
                    } catch (ParseException e) {
                        YesSteveModel.LOGGER.error("Failed to execute molang " + message.getExpression(), e);
                    }
                });
            } else if (TouhouMaidCompat.isMaidEntity(entity)) {
                TouhouMaidCompat.playMaidAnimation(entity, message.getExpression());
            }
        }
    }

    public static void handleSetModelAndTexture(Object obj) {
        S2CSetModelAndTexturePacket message = (S2CSetModelAndTexturePacket) obj;
        EntityJoinCallbackEvent.addCallback(message.getEntityId(), entity -> {
            PlayerCapability.get(entity).ifPresent(cap -> {
                LocalPlayer localPlayer = Minecraft.getInstance().player;
                boolean keepLocalOnlyModel = entity == localPlayer && ClientModelManager.isSelectedLocalOnlyModel(cap.getModelId());
                if (!keepLocalOnlyModel) {
                    cap.initModelWithTexture(message.getModelId(), message.getTextureId());
                }
                cap.setForceDisabled(message.isDisabled());
                applyPlayerState(entity, message.getEntityModelSync());
            });
        });
    }

    public static void handleSyncAuthModels(Object obj) {
        S2CSyncAuthModelsPacket message = (S2CSyncAuthModelsPacket) obj;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            AuthModelsCapability.get(minecraft.player).ifPresent(cap -> cap.setAuthModels(message.getAuthModels()));
        }
    }

    public static void handleSyncStarModels(Object obj) {
        S2CSyncStarModelsPacket message = (S2CSyncStarModelsPacket) obj;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Set<String> merged = Sets.newHashSet(message.getStarModels());
            Set<String> local = LocalStarModelsStore.load();
            merged.addAll(local);
            LocalStarModelsStore.save(merged);
            StarModelsCapability.get(minecraft.player).ifPresent(cap -> cap.setStarModels(merged));
            for (String modelId : local) {
                if (!message.getStarModels().contains(modelId) && NetworkHandler.isClientConnected()) {
                    NetworkHandler.sendToServer(C2SSetStarModelPacket.add(modelId));
                }
            }
        }
    }

    public static void handleVersionCheck(Object obj, Connection connection) {
        S2CVersionCheckPacket message = (S2CVersionCheckPacket) obj;
        ClientModelManager.setOysmServer(message.isOysmServer());
        ClientModelManager.setAllowUpload(message.isAllowUpload());
        if (NetworkHandler.setChannelVersion(connection, message.getVersion())) {
            ClientModelManager.onSyncConnected();
        }
        if (NetworkHandler.VERSION.equals(message.getVersion())) {
            NetworkHandler.markClientHandshakeComplete();
        }
        NetworkHandler.sendToServer(new C2SVersionCheckPacket());
    }

    public static void handleSyncAnimationExpression(Object obj) {
        S2CSyncAnimationExpressionPacket message = (S2CSyncAnimationExpressionPacket) obj;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            PlayerCapability.get(minecraft.level.getEntity(message.getEntityId()))
                    .ifPresent(cap -> cap.executeAnimationExpression(message.getFloatData()));
        }
    }

    public static void handleSyncPlayerState(Object obj) {
        S2CSyncPlayerStatePacket message = (S2CSyncPlayerStatePacket) obj;
        EntityJoinCallbackEvent.addCallback(message.entityId, entity -> applyPlayerState(entity, message));
    }

    public static void handleSyncProjectileModel(Object obj) {
        S2CSyncProjectileModelPacket message = (S2CSyncProjectileModelPacket) obj;
        EntityJoinCallbackEvent.addCallback(message.getEntityId(), entity -> {
            ProjectileCapability.get(entity).ifPresent(projectileCapability -> {
                projectileCapability.updateModelId(message.getCapability().getOwnerModelId());
                projectileCapability.setFloatProperties(message.getFloatMap());
            });
        });
    }

    public static void handleSyncVehicleModel(Object obj) {
        S2CSyncVehicleModelPacket message = (S2CSyncVehicleModelPacket) obj;
        EntityJoinCallbackEvent.addCallback(message.getEntityId(), entity -> {
            VehicleCapability.get(entity).ifPresent(vehicleCapability -> {
                vehicleCapability.setOwnerModelId(message.getCapability().getOwnerModelId());
                vehicleCapability.setFloatMap((Int2FloatOpenHashMap) message.getFloatMap());
            });
        });
    }

    private static void applyPlayerState(Entity entity, S2CSyncPlayerStatePacket message) {
        if (entity instanceof Player) {
            PlayerCapability.get(entity).ifPresent(cap -> {
                if ((message.flags & 2048) != 0) {
                    if (!StringUtils.isEmpty(message.modelSwitchId)) {
                        cap.requestModelSwitch(message.modelSwitchId);
                    } else {
                        cap.clearModelSwitch();
                    }
                }
                if ((message.flags & 4096) != 0) {
                    if (message.isFullSync()) {
                        cap.updateMolangVars(message.getMolangHashId(), (Int2FloatOpenHashMap) message.molangVarData);
                    } else {
                        cap.enqueueMolangDelta(message.getMolangHashId(), message.molangVarData);
                    }
                }
                cap.getPositionTracker().applySyncMessage(message);
            });
        }
    }
}
