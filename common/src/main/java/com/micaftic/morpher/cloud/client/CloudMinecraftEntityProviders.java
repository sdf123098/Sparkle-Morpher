package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

/**
 * Registers the built-in client adapters for the three explicitly bindable
 * Cloud entity kinds. The adapter never infers a kind or creates a binding;
 * it only resolves UUIDs already present in the Cloud binding snapshot.
 */
public final class CloudMinecraftEntityProviders {
    private static CloudClientRuntime.RuntimeState registeredRuntime;
    private static final Set<CloudAssetIdentity> MATERIALIZING = ConcurrentHashMap.newKeySet();

    private CloudMinecraftEntityProviders() {
    }

    public static void tick() {
        CloudClientRuntime.RuntimeState runtime = CloudClientRuntime.state();
        if (runtime == null) return;
        if (registeredRuntime != runtime) {
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.PLAYER));
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.FAKE_PLAYER));
            CloudClientRuntime.registerEntityProvider(new CapabilityProvider(CloudEntityProvider.Kind.MAID));
            registeredRuntime = runtime;
        }
        CloudClientRuntime.tickEntityObservations();
    }

    private static final class CapabilityProvider implements CloudEntityProvider {
        private final Kind kind;

        private CapabilityProvider(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        @Override
        public Kind kind() {
            return kind;
        }

        @Override
        public boolean isAvailable(UUID entityUuid) {
            Entity entity = entity(entityUuid);
            if (entity == null) return false;
            return switch (kind) {
                case PLAYER, FAKE_PLAYER -> PlayerCapability.get(entity).isPresent();
                case MAID -> MaidCapability.get(entity).isPresent();
            };
        }

        @Override
        public void applyAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision) {
            Entity entity = entity(entityUuid);
            if (entity == null) return;
            if (kind == Kind.MAID) {
                MaidCapability.get(entity).ifPresent(capability -> applyMaid(capability, appearance));
            } else {
                PlayerCapability.get(entity).ifPresent(capability -> applyPlayer(capability, appearance));
            }
        }

        private static void applyPlayer(PlayerCapability capability, CloudScopeClient.CloudAppearance appearance) {
            capability.isDisabled = appearance.disabled();
            applyModel(capability, appearance);
        }

        private static void applyMaid(MaidCapability capability, CloudScopeClient.CloudAppearance appearance) {
            applyModel(capability, appearance);
        }

        private static void applyModel(LivingAnimatable<?> capability,
                                       CloudScopeClient.CloudAppearance appearance) {
            if (appearance.disabled()) {
                capability.setForceDisabled(true);
                return;
            }
            String assetId = appearance.assetId();
            CloudClientRuntime.RuntimeState runtime = CloudClientRuntime.state();
            CloudAssetIdentity identity = assetId == null || assetId.isBlank() || runtime == null
                    ? null : cloudIdentity(runtime, appearance);
            String runtimeModelId = identity == null ? null : identity.runtimeModelId();
            if (runtimeModelId != null && ClientModelManager.getAvailableModelIds().contains(runtimeModelId)) {
                String textureId = appearance.textureId() == null ? "default" : appearance.textureId();
                capability.initModelWithTexture(runtimeModelId, textureId);
            } else if (assetId != null && !assetId.isBlank()) {
                requestMaterialization(capability, appearance, runtime, identity);
            }
            capability.setForceDisabled(false);
        }

        private static void requestMaterialization(LivingAnimatable<?> capability,
                                                    CloudScopeClient.CloudAppearance appearance,
                                                    CloudClientRuntime.RuntimeState runtime,
                                                    CloudAssetIdentity identity) {
            Long revision = appearance.assetRevision();
            String rawSha256 = appearance.rawSha256();
            if (runtime == null || identity == null || revision == null || rawSha256 == null || rawSha256.isBlank()) return;
            CloudAssetSummary summary = runtime.assetCatalog().get(appearance.assetId());
            if (summary == null) return;
            CloudAssetRef ref = new CloudAssetRef(appearance.assetId(), revision, rawSha256);
            if (!ref.equals(summary.ref()) || !MATERIALIZING.add(identity)) return;

            runtime.assetMaterialization().ensure(ref).thenAccept(path -> {
                final byte[] bytes;
                try {
                    bytes = Files.readAllBytes(path);
                } catch (IOException failure) {
                    MATERIALIZING.remove(identity);
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    if (CloudClientRuntime.state() != runtime) {
                        MATERIALIZING.remove(identity);
                        return;
                    }
                    String runtimeModelId = identity.runtimeModelId();
                    ClientModelManager.importLocalModel(runtimeModelId, importFileName(summary), bytes, error -> {
                        MATERIALIZING.remove(identity);
                        if (error == null && ClientModelManager.getAvailableModelIds().contains(runtimeModelId)) {
                            String textureId = appearance.textureId() == null ? "default" : appearance.textureId();
                            capability.initModelWithTexture(runtimeModelId, textureId);
                        }
                    });
                });
            }).exceptionally(failure -> {
                MATERIALIZING.remove(identity);
                return null;
            });
        }

        private static CloudAssetIdentity cloudIdentity(CloudClientRuntime.RuntimeState runtime,
                                                        CloudScopeClient.CloudAppearance appearance) {
            if (appearance.assetRevision() == null || appearance.rawSha256() == null || appearance.rawSha256().isBlank()) return null;
            return new CloudAssetIdentity(runtime.instance().instanceId(), "catalog", appearance.assetId(),
                    Long.toString(appearance.assetRevision()), appearance.rawSha256());
        }

        private static String importFileName(CloudAssetSummary summary) {
            String name = summary.name() == null || summary.name().isBlank() ? summary.ref().assetId() : summary.name();
            String format = summary.format() == null ? "" : summary.format().trim().toLowerCase(Locale.ROOT);
            if (!format.isBlank() && format.matches("[a-z0-9]{1,12}")
                    && !name.toLowerCase(Locale.ROOT).endsWith("." + format)) {
                name += "." + format;
            }
            return name;
        }

        private static Entity entity(UUID entityUuid) {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return null;
            try {
                Method byUuid = client.level.getClass().getMethod("getEntity", UUID.class);
                Object resolved = byUuid.invoke(client.level, entityUuid);
                if (resolved instanceof Entity entity) return entity;
            } catch (ReflectiveOperationException ignored) {
                // 1.21.1 exposes the integer-id overload instead; use the
                // reflective iterable fallbacks below so this common source
                // remains binary-safe across the supported client versions.
            }
            for (String methodName : new String[]{"entitiesForRendering", "getAllEntities"}) {
                try {
                    Method all = client.level.getClass().getMethod(methodName);
                    Object value = all.invoke(client.level);
                    if (value instanceof Iterable<?> iterable) {
                        for (Object candidate : iterable) {
                            if (candidate instanceof Entity entity && entityUuid.equals(entity.getUUID())) return entity;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Optional loader/version-specific lookup surface.
                }
            }
            for (var player : client.level.players()) {
                if (entityUuid.equals(player.getUUID())) return player;
            }
            return null;
        }
    }
}
