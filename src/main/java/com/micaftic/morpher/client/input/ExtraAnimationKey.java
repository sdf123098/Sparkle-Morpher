package com.micaftic.morpher.client.input;

import com.google.common.collect.Lists;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.List;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ExtraAnimationKey {
    public static final List<KeyMapping> KEY_MAPPINGS = Lists.newArrayList();
    private static boolean initialized = false;

    private ExtraAnimationKey() {}

    public static List<KeyMapping> getKeyMappings() {
        if (!initialized) {
            initialized = true;
            for (int i = 0; i <= 7; i++) {
                KeyMapping eventMapping = KeyMappingFactory.createInGameNone(String.format("key.sparkle_morpher.extra_animation.%d.desc", i), InputConstants.Type.KEYSYM, -1, "key.category.sparkle_morpher");
                KEY_MAPPINGS.add(eventMapping);
            }
        }
        return KEY_MAPPINGS;
    }

    public static void register() {
        if (PlatformAPI.isServer()) return;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (PlatformAPI.isServer()) return;
        onKeyInput(event.getAction(), event.getKey(), event.getScanCode());
    }

    private static void onKeyInput(int action, int keyCode, int scanCode) {
        if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady()) return;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        for (KeyMapping eventMapping : KEY_MAPPINGS) {
            if (action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, eventMapping) && localPlayer != null && !AnimationLockEvent.isMoving(localPlayer)) {
                PlayerCapability.get(localPlayer).ifPresent(cap -> {
                    ModelAssembly modelAssembly = cap.getModelAssembly();
                    int index = KEY_MAPPINGS.indexOf(eventMapping);
                    ModelProperties modelProperties = modelAssembly.getModelData().getModelProperties();
                    OrderedStringMap<String, String> map = modelProperties.getExtraAnimation();
                    if (map.size() > index) {
                        String rouletteKey = map.getKeyAt(index);
                        if ("#return".equals(rouletteKey)) {
                            NetworkHandler.sendToServer(C2SPlayAnimationPacket.createDefault());
                            return;
                        }
                        if (rouletteKey.startsWith("#") && modelProperties.getExtraAnimationClassify().containsKey(rouletteKey.substring(1))) {
                            UnifiedRouletteScreen.setInitialSubmenu(rouletteKey.substring(1));
                            Minecraft.getInstance().setScreen(new UnifiedRouletteScreen(cap.getModelId(), modelAssembly, cap));
                            return;
                        }
                        NetworkHandler.sendToServer(new C2SPlayAnimationPacket(index, StringPool.EMPTY));
                    }
                });
                return;
            }
        }
    }
}
