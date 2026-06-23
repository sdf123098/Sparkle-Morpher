package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

public final class NeoForgeCapabilityTypes {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YesSteveModel.MOD_ID);

    public static final Supplier<AttachmentType<ModelInfoCapability>> MODEL_INFO =
            ATTACHMENTS.register("model_info", () -> AttachmentType.serializable(ModelInfoCapability::new).copyOnDeath().build());
    public static final Supplier<AttachmentType<AuthModelsCapability>> AUTH_MODELS =
            ATTACHMENTS.register("auth_models", () -> AttachmentType.serializable(AuthModelsCapability::new).copyOnDeath().build());
    public static final Supplier<AttachmentType<StarModelsCapability>> STAR_MODELS =
            ATTACHMENTS.register("star_models", () -> AttachmentType.serializable(StarModelsCapability::new).copyOnDeath().build());
    public static final Supplier<AttachmentType<ProjectileModelCapability>> PROJECTILE_MODEL =
            ATTACHMENTS.register("projectile_model", () -> AttachmentType.builder(ProjectileModelCapability::new).build());
    public static final Supplier<AttachmentType<VehicleModelCapability>> VEHICLE_MODEL =
            ATTACHMENTS.register("vehicle_model", () -> AttachmentType.builder(VehicleModelCapability::new).build());
    public static final Supplier<AttachmentType<ProjectileCapability>> PROJECTILE_RENDER =
            ATTACHMENTS.register("projectile_render", () -> AttachmentType.builder(holder -> new ProjectileCapability((Projectile) holder)).build());
    public static final Supplier<AttachmentType<VehicleCapability>> VEHICLE_RENDER =
            ATTACHMENTS.register("vehicle_render", () -> AttachmentType.builder(holder -> new VehicleCapability((Entity) holder)).build());

    private NeoForgeCapabilityTypes() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static Optional<ProjectileCapability> projectileRender(Entity entity) {
        return entity instanceof Projectile ? Optional.of(entity.getData(PROJECTILE_RENDER)) : Optional.empty();
    }

    public static Optional<VehicleCapability> vehicleRender(Entity entity) {
        return Optional.of(entity.getData(VEHICLE_RENDER));
    }
}
