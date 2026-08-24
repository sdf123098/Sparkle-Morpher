package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.*;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerModelToggleKey {
    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameAlt("key.sparkle_morpher.player_model.desc", InputConstants.Type.KEYSYM, 89, "key.category.sparkle_morpher");
    private PlayerModelToggleKey() {}
    @SubscribeEvent public static void onKey(InputEvent.Key event) {
        if (PlatformAPI.isServer()) return;
        if (event.getAction() == 1 && InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers(), KEY_MAPPING)) {
            openModelScreen();
        }
    }
    @SubscribeEvent public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (PlatformAPI.isServer()) return;
        if (event.getAction() == 1 && InputUtil.isMousePressed(event.getButton(), KEY_MAPPING) && openModelScreen()) {
            event.setCanceled(true);
        }
    }
    private static boolean openModelScreen() {
        if (!InputUtil.isPlayerReady()) return false;
        if (!YesSteveModel.isAvailable()) { YesSteveModel.sendUnavailableMessage(); return true; }
        if (NetworkHandler.isClientConnected() && !ConfigPolicies.network().canSwitchModel()) Minecraft.getInstance().setScreen(ModernPlayerModelScreen.settings());
        else Minecraft.getInstance().setScreen(new ModernPlayerModelScreen());
        return true;
    }
}
