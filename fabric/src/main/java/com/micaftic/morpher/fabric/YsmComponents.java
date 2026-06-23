package com.micaftic.morpher.fabric;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.fabric.AuthModelsComponent;
import com.micaftic.morpher.capability.fabric.ModelInfoComponent;
import com.micaftic.morpher.capability.fabric.ProjectileModelComponent;
import com.micaftic.morpher.capability.fabric.StarModelsComponent;
import com.micaftic.morpher.capability.fabric.VehicleModelComponent;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class YsmComponents implements EntityComponentInitializer {

    public static final ComponentKey<StarModelsComponent> STAR_MODELS = ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,"star_models"),
            StarModelsComponent.class
    );

    public static final ComponentKey<AuthModelsComponent> AUTH_MODELS = ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,"auth_models"),
            AuthModelsComponent.class
    );

    public static final ComponentKey<ModelInfoComponent> MODEL_INFO = ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,"model_info"),
            ModelInfoComponent.class
    );

    public static final ComponentKey<ProjectileModelComponent> PROJECTILE_MODEL = ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,"projectile_model"),
            ProjectileModelComponent.class
    );

    public static final ComponentKey<VehicleModelComponent> VEHICLE_MODEL = ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,"vehicle_model"),
            VehicleModelComponent.class
    );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(STAR_MODELS, p -> new StarModelsComponent(), RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(AUTH_MODELS, p -> new AuthModelsComponent(), RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(MODEL_INFO, p -> new ModelInfoComponent(), RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerFor(Projectile.class, PROJECTILE_MODEL, p -> new ProjectileModelComponent());
        registry.registerFor(Entity.class, VEHICLE_MODEL, e -> new VehicleModelComponent());
    }
}
