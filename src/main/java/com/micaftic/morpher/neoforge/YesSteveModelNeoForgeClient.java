package com.micaftic.morpher.neoforge;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.compat.touhoulittlemaid.TouhouLittleMaidClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@OnlyIn(Dist.CLIENT)
public final class YesSteveModelNeoForgeClient {

    private YesSteveModelNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        TouhouLittleMaidClientCompat.initOfficialCompat();
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> ModernPlayerModelScreen.settings(parent));
        NeoForgeClientEventBridge.register(modBus);
    }

    public static void sendUnavailableMessage() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.sendSystemMessage(YesSteveModel.getUnavailableComponent());
        }
    }
}
