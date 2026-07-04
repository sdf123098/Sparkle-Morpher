package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.model.ModelActionProfile;

import java.util.EnumMap;
import java.util.Map;

public class ModelActionProviderRegistry {

    private static final Map<ModelActionProfile, PlayerActionProvider> PROVIDERS = new EnumMap<>(ModelActionProfile.class);

    static {
        register(ModelActionProfile.YSM_AUTHORED, new YsmActionProvider());
        register(ModelActionProfile.VANILLA_HUMANOID, new VanillaHumanoidActionProvider());
        register(ModelActionProfile.HYBRID_AUTHORED_WITH_VANILLA_FALLBACK, new HybridActionProvider());
    }

    private ModelActionProviderRegistry() {
    }

    public static void register(ModelActionProfile profile, PlayerActionProvider provider) {
        PROVIDERS.put(profile, provider);
    }

    public static PlayerActionProvider get(ModelActionProfile profile) {
        return PROVIDERS.getOrDefault(profile, PROVIDERS.get(ModelActionProfile.YSM_AUTHORED));
    }
}
