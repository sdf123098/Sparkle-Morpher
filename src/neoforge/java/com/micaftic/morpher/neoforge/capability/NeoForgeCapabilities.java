package com.micaftic.morpher.neoforge.capability;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Optional;
import java.util.function.Supplier;

public final class NeoForgeCapabilities {
    private static final DeferredRegister<AttachmentType<?>> ATT = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YesSteveModel.MOD_ID);

    public static final Supplier<AttachmentType<AuthModelsCapability>> AUTH_MODELS = ATT.register("auth_models", () ->
            AttachmentType.builder(AuthModelsCapability::new).serialize(new AuthModelsSerializer()).copyOnDeath().build());
    public static final Supplier<AttachmentType<StarModelsCapability>> STAR_MODELS = ATT.register("star_models", () ->
            AttachmentType.builder(StarModelsCapability::new).serialize(new StarModelsSerializer()).copyOnDeath().build());
    public static final Supplier<AttachmentType<ModelInfoCapability>> MODEL_INFO = ATT.register("model_info", () ->
            AttachmentType.builder(ModelInfoCapability::new).serialize(new ModelInfoSerializer()).copyOnDeath().build());
    public static final Supplier<AttachmentType<ProjectileModelCapability>> PROJECTILE_MODEL = ATT.register("projectile_model", () ->
            AttachmentType.builder(ProjectileModelCapability::new).serialize(new ProjectileModelSerializer()).build());
    public static final Supplier<AttachmentType<VehicleModelCapability>> VEHICLE_MODEL = ATT.register("vehicle_model", () ->
            AttachmentType.builder(VehicleModelCapability::new).serialize(new VehicleModelSerializer()).build());

    public static void register(IEventBus bus) { ATT.register(bus); }
    public static Optional<AuthModelsCapability> getAuthModels(Player p) { return Optional.of(p.getData(AUTH_MODELS)); }
    public static Optional<StarModelsCapability> getStarModels(Player p) { return Optional.of(p.getData(STAR_MODELS)); }
    public static Optional<ModelInfoCapability> getModelInfo(Player p) { return Optional.of(p.getData(MODEL_INFO)); }
    public static Optional<ProjectileModelCapability> getProjectileModel(Entity e) { return Optional.of(e.getData(PROJECTILE_MODEL)); }
    public static Optional<VehicleModelCapability> getVehicleModel(Entity e) { return Optional.of(e.getData(VEHICLE_MODEL)); }

    private NeoForgeCapabilities() {}

    private static class AuthModelsSerializer implements IAttachmentSerializer<ListTag, AuthModelsCapability> {
        @Override public AuthModelsCapability read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) { AuthModelsCapability c = new AuthModelsCapability(); c.deserializeNBT(tag); return c; }
        @Override public ListTag write(AuthModelsCapability c, HolderLookup.Provider provider) { return c.serializeNBT(); }
    }
    private static class StarModelsSerializer implements IAttachmentSerializer<ListTag, StarModelsCapability> {
        @Override public StarModelsCapability read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) { StarModelsCapability c = new StarModelsCapability(); c.deserializeNBT(tag); return c; }
        @Override public ListTag write(StarModelsCapability c, HolderLookup.Provider provider) { return c.serializeNBT(); }
    }
    private static class ModelInfoSerializer implements IAttachmentSerializer<CompoundTag, ModelInfoCapability> {
        @Override public ModelInfoCapability read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) { ModelInfoCapability c = new ModelInfoCapability(); c.deserializeNBT(tag); return c; }
        @Override public CompoundTag write(ModelInfoCapability c, HolderLookup.Provider provider) { return c.serializeNBT(); }
    }
    private static class ProjectileModelSerializer implements IAttachmentSerializer<CompoundTag, ProjectileModelCapability> {
        @Override public ProjectileModelCapability read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) { ProjectileModelCapability c = new ProjectileModelCapability(); c.deserializeNBT(tag); return c; }
        @Override public CompoundTag write(ProjectileModelCapability c, HolderLookup.Provider provider) { return c.serializeNBT(); }
    }
    private static class VehicleModelSerializer implements IAttachmentSerializer<CompoundTag, VehicleModelCapability> {
        @Override public VehicleModelCapability read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) { VehicleModelCapability c = new VehicleModelCapability(); c.deserializeNBT(tag); return c; }
        @Override public CompoundTag write(VehicleModelCapability c, HolderLookup.Provider provider) { return c.serializeNBT(); }
    }
}