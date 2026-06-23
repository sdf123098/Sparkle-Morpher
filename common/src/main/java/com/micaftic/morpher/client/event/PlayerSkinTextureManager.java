package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.core.architectury.event.EventResult;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.api.PlatformAPI;

public class PlayerSkinTextureManager {

    private PlayerSkinTextureManager() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        SpecialPlayerRenderEvent.EVENT.register(PlayerSkinTextureManager::onRenderTexture);
    }

    private static EventResult onRenderTexture(SpecialPlayerRenderEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return EventResult.pass();
        }
        // Skin models (misc/1_alex, misc/2_steve) have been removed.
        // The remaining built-in models have their own textures and should not be overridden.
        return EventResult.pass();
    }
}
