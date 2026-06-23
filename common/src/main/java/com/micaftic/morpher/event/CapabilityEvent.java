package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CSetModelAndTexturePacket;
import com.micaftic.morpher.network.message.S2CSyncAuthModelsPacket;
import com.micaftic.morpher.network.message.S2CSyncProjectileModelPacket;
import com.micaftic.morpher.network.message.S2CSyncStarModelsPacket;
import com.micaftic.morpher.network.message.S2CSyncVehicleModelPacket;
import com.micaftic.morpher.network.message.S2CVersionCheckPacket;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.core.api.capability.CapabilityLifecycle;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public final class CapabilityEvent {
    private static final ConcurrentMap<UUID, ConcurrentMap<UUID, String>> SYNCED_PLAYER_MODEL_STATES = new ConcurrentHashMap<>();

    private CapabilityEvent() {
    }

    public static void register() {
        PlayerEvent.PLAYER_CLONE.register(CapabilityEvent::onPlayerCloned);
        PlayerEvent.PLAYER_QUIT.register(CapabilityEvent::onPlayerQuit);
        EntityEvent.ADD.register(CapabilityEvent::onEntityAdd);
        TickEvent.SERVER_POST.register(CapabilityEvent::onServerTick);
    }

    private static void onPlayerCloned(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        CapabilityLifecycle.revive(oldPlayer);
        Optional<ModelInfoCapability> oldModelInfoCap = getModelInfoCap(oldPlayer);
        Optional<AuthModelsCapability> oldAuthModelsCap = getAuthModelsCap(oldPlayer);
        Optional<StarModelsCapability> oldStarModelsCap = getStarModelsCap(oldPlayer);
        CapabilityLifecycle.invalidate(oldPlayer);
        Optional<ModelInfoCapability> modelInfoCap = getModelInfoCap(newPlayer);
        Optional<AuthModelsCapability> authModelsCap = getAuthModelsCap(newPlayer);
        Optional<StarModelsCapability> starModelsCap = getStarModelsCap(newPlayer);
        modelInfoCap.ifPresent(newModelInfo -> {
            Objects.requireNonNull(newModelInfo);
            oldModelInfoCap.ifPresent(newModelInfo::copyFrom);
        });
        authModelsCap.ifPresent(newAuthModels -> {
            Objects.requireNonNull(newAuthModels);
            oldAuthModelsCap.ifPresent(newAuthModels::copyFrom);
        });
        starModelsCap.ifPresent(newStarModels -> {
            Objects.requireNonNull(newStarModels);
            oldStarModelsCap.ifPresent(newStarModels::copyFrom);
        });
    }

    private static void onPlayerQuit(ServerPlayer player) {
        UUID playerId = player.getUUID();
        SYNCED_PLAYER_MODEL_STATES.remove(playerId);
        SYNCED_PLAYER_MODEL_STATES.values().forEach(states -> states.remove(playerId));
    }

    public static void forgetSyncedModelStates(ServerPlayer receiver) {
        SYNCED_PLAYER_MODEL_STATES.remove(receiver.getUUID());
    }

    private static EventResult onEntityAdd(Entity entity, Level level) {
        if (!YesSteveModel.isAvailable()) {
            return EventResult.pass();
        }
        if (!level.isClientSide() && entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            syncProjectileModel(projectile, owner);
        }
        if (entity instanceof ServerPlayer player) {
            getAuthModelsCap(player).ifPresent(authModelsCap -> {
                for (String modelId : ServerModelManager.getAuthModels()) {
                    authModelsCap.addModel(modelId);
                }
                NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(authModelsCap.getAuthModels()), player);
            });
            PlayerModelSelectionStore.restore(player);
            ServerModelManager.validatePlayerModel(player);
            syncPlayerModelToSelf(player);
            syncPlayerModelToTracking(player, false);
            getStarModelsCap(player).ifPresent(starModelsCap -> NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(starModelsCap.getStarModels()), player));
        }
        return EventResult.pass();
    }

    public static void syncPlayerModelToSelf(ServerPlayer player) {
        getModelInfoCap(player).ifPresent(modelInfoCap -> {
            if (!canSyncModel(player, modelInfoCap)) {
                modelInfoCap.markDirty();
                return;
            }
            modelInfoCap.stopAnimation(player);
            Optional<S2CSetModelAndTexturePacket> optional = modelInfoCap.createSyncMessage(player, false);
            Consumer<? super S2CSetModelAndTexturePacket> consumer = message -> NetworkHandler.sendToClientPlayer(message, player);
            Objects.requireNonNull(modelInfoCap);
            optional.ifPresentOrElse(consumer, modelInfoCap::markDirty);
        });
    }

    public static void syncPlayerModelToTracking(ServerPlayer player, boolean resetMessage) {
        getModelInfoCap(player).ifPresent(modelInfoCap -> {
                if (!canSyncModel(player, modelInfoCap)) {
                    NetworkOnlineDebugLog.info("syncToTracking: SKIP not_connected player={}", player.getName().getString());
                    modelInfoCap.markDirty();
                    return;
                }
            NetworkOnlineDebugLog.info("syncToTracking: {} modelId={} reset={}",
                    player.getName().getString(), modelInfoCap.getModelId(), resetMessage);
            modelInfoCap.createSyncMessage(player, resetMessage).ifPresentOrElse(
                    message -> {
                        NetworkOnlineDebugLog.info("syncToTracking: SENDING packet to {}", player.getName().getString());
                        NetworkHandler.sendToTrackingEntityAndSelf(message, player);
                        rememberTrackedPlayerModelState(player, modelInfoCap);
                    },
                    () -> {
                        NetworkOnlineDebugLog.info("syncToTracking: EMPTY -> markDirty");
                        modelInfoCap.markDirty();
                    });
        });
    }

    public static void syncVisiblePlayerModelsTo(ServerPlayer receiver) {
        MinecraftServer server = receiver.serverLevel().getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            getModelInfoCap(player).ifPresent(modelInfoCap -> {
                if (!canSyncModel(player, modelInfoCap)) {
                    return;
                }
                modelInfoCap.createSyncMessage(player, false).ifPresent(message -> {
                    NetworkHandler.sendToClientPlayer(message, receiver);
                    rememberModelState(player, receiver, buildModelStateKey(modelInfoCap));
                });
            });
        }
    }

    private static boolean canSyncModel(ServerPlayer player, ModelInfoCapability modelInfoCap) {
        return NetworkHandler.isPlayerConnected(player) || modelInfoCap.isMandatory();
    }

    private static void onServerTick(MinecraftServer server) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Boolean bool = ServerConfig.LOW_BANDWIDTH_USAGE.get();
        for (ServerPlayer serverPlayer : players) {
            getModelInfoCap(serverPlayer).ifPresent(cap -> {
                if (!NetworkHandler.isPlayerConnected(serverPlayer) && !cap.isMandatory()) {
                    if (serverPlayer.tickCount == 200 || serverPlayer.tickCount == 600 || serverPlayer.tickCount == 1800) {
                        NetworkHandler.sendToClientPlayer(new S2CVersionCheckPacket(), serverPlayer);
                    }
                    return;
                }
                if (cap.isDirty()) {
                    cap.getAnimSync().updateAndSync(serverPlayer, false, bool);
                    cap.createSyncMessage(serverPlayer, true).ifPresent(message -> {
                        cap.clearDirty();
                        NetworkHandler.sendToTrackingEntityAndSelf(message, serverPlayer);
                        rememberTrackedPlayerModelState(serverPlayer, cap);
                        if (serverPlayer.getVehicle() != null && serverPlayer.getVehicle().getFirstPassenger() == serverPlayer) {
                            syncVehicleModel(serverPlayer.getVehicle(), serverPlayer);
                        }
                    });
                } else {
                    cap.getAnimSync().updateAndSync(serverPlayer, true, bool);
                }
                syncTrackedPlayerModelState(serverPlayer, cap);
            });
        }
    }

    private static void syncTrackedPlayerModelState(ServerPlayer source, ModelInfoCapability cap) {
        MinecraftServer server = source.serverLevel().getServer();
        if (server == null) {
            return;
        }
        String stateKey = buildModelStateKey(cap);
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (receiver == source) {
                continue;
            }
            sendModelStateIfNeeded(source, cap, receiver, stateKey);
        }
        sendModelStateIfNeeded(source, cap, source, stateKey);
    }

    private static void sendModelStateIfNeeded(ServerPlayer source, ModelInfoCapability cap, ServerPlayer receiver, String stateKey) {
        if (!NetworkHandler.isPlayerConnected(receiver)) {
            return;
        }
        UUID sourceId = source.getUUID();
        ConcurrentMap<UUID, String> states = SYNCED_PLAYER_MODEL_STATES.computeIfAbsent(receiver.getUUID(), uuid -> new ConcurrentHashMap<>());
        if (stateKey.equals(states.get(sourceId))) {
            return;
        }
        cap.createSyncMessage(source, true).ifPresent(message -> {
            NetworkHandler.sendToClientPlayer(message, receiver);
            states.put(sourceId, stateKey);
        });
    }

    private static void rememberTrackedPlayerModelState(ServerPlayer source, ModelInfoCapability cap) {
        MinecraftServer server = source.serverLevel().getServer();
        if (server == null) {
            return;
        }
        String stateKey = buildModelStateKey(cap);
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            if (receiver == source) {
                continue;
            }
            rememberModelState(source, receiver, stateKey);
        }
        rememberModelState(source, source, stateKey);
    }

    private static void rememberModelState(ServerPlayer source, ServerPlayer receiver, String stateKey) {
        if (NetworkHandler.isPlayerConnected(receiver)) {
            SYNCED_PLAYER_MODEL_STATES.computeIfAbsent(receiver.getUUID(), uuid -> new ConcurrentHashMap<>()).put(source.getUUID(), stateKey);
        }
    }

    private static String buildModelStateKey(ModelInfoCapability cap) {
        return cap.getModelId() + "\0" + cap.getSelectTexture() + "\0" + cap.isDisabled();
    }

    public static void syncProjectileModel(Projectile projectile, ServerPlayer serverPlayer) {
        ModelInfoCapability.get(serverPlayer).ifPresent(modelInfoCap -> {
            if (!NetworkHandler.isPlayerConnected(serverPlayer) && !modelInfoCap.isMandatory()) {
                return;
            }
            ProjectileModelCapability.get(projectile).ifPresent(projectileModelCap -> modelInfoCap.withMolangVars(object2FloatOpenHashMap -> {
                projectileModelCap.setModel(modelInfoCap.getModelId(), object2FloatOpenHashMap);
                S2CSyncProjectileModelPacket packet = new S2CSyncProjectileModelPacket(projectile.getId(), projectileModelCap);
                NetworkHandler.sendToClientPlayer(packet, serverPlayer);
                NetworkHandler.sendToTrackingEntity(packet, projectile);
            }));
        });
    }

    public static void syncVehicleModel(Entity entity, ServerPlayer serverPlayer) {
        ModelInfoCapability.get(serverPlayer).ifPresent(modelInfoCap -> {
            if (!NetworkHandler.isPlayerConnected(serverPlayer) && !modelInfoCap.isMandatory()) {
                return;
            }
            VehicleModelCapability.get(entity).ifPresent(vehicleModelCap -> modelInfoCap.getMolangVars().ifPresent(object2FloatOpenHashMap -> {
                vehicleModelCap.setModel(modelInfoCap.getModelId(), object2FloatOpenHashMap);
                NetworkHandler.sendToTrackingEntity(new S2CSyncVehicleModelPacket(entity.getId(), vehicleModelCap), entity);
            }));
        });
    }

    public static Optional<ModelInfoCapability> getModelInfoCap(Player player) {
        return ModelInfoCapability.get(player);
    }

    public static Optional<AuthModelsCapability> getAuthModelsCap(Player player) {
        return AuthModelsCapability.get(player);
    }

    public static Optional<StarModelsCapability> getStarModelsCap(Player player) {
        return StarModelsCapability.get(player);
    }
}
