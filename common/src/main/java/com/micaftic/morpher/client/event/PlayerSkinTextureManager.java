package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.core.api.PlatformAPI;

import java.util.Map;

public class PlayerSkinTextureManager {

    private static final ResourceLocation STEVE_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final ResourceLocation ALEX_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    private static final String STEVE_TEXTURE_ID = "misc/2_steve";

    private static final String ALEX_TEXTURE_ID = "misc/1_alex";

    private PlayerSkinTextureManager() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        SpecialPlayerRenderEvent.EVENT.register(PlayerSkinTextureManager::onRenderTexture);
    }

    private static EventResult onRenderTexture(SpecialPlayerRenderEvent event) {
        ResourceLocation location;
        if (!YesSteveModel.isAvailable()) {
            return EventResult.pass();
        }
        Player player = event.getPlayer();
        if (isDefaultSkin(event.getModelId()) && (player instanceof AbstractClientPlayer abstractClientPlayer)) {
            location = abstractClientPlayer.getSkin().texture();
            if (location == null) {
                location = getSkinTexture(event.getModelId());
            }
            event.setTextureLocation(location);
        }
        return EventResult.pass();
    }

    private static boolean isDefaultSkin(String str) {
        return str.equals(STEVE_TEXTURE_ID) || str.equals(ALEX_TEXTURE_ID);
    }

    private static ResourceLocation getSkinTexture(String str) {
        return str.equals(STEVE_TEXTURE_ID) ? STEVE_SKIN : ALEX_SKIN;
    }
}
