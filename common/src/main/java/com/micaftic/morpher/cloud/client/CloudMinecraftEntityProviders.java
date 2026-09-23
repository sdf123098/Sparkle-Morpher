package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers the built-in client adapters for the three explicitly bindable
 * Cloud entity kinds. The adapter never infers a kind or creates a binding;
 * it only resolves UUIDs already present in the Cloud binding snapshot.
 */
public final class CloudMinecraftEntityProviders {
    private static CloudClientRuntime.RuntimeState registeredRuntime;

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
            if (assetId != null && !assetId.isBlank()
                    && ClientModelManager.getAvailableModelIds().contains(assetId)) {
                String textureId = appearance.textureId() == null ? "default" : appearance.textureId();
                capability.initModelWithTexture(assetId, textureId);
            }
            capability.setForceDisabled(false);
        }

        private static Entity entity(UUID entityUuid) {
            Minecraft client = Minecraft.getInstance();
            return client.level == null ? null : client.level.getEntity(entityUuid);
        }
    }
}
