package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.micaftic.morpher.core.api.PlatformAPI;

import java.util.List;

public final class ExtraAnimationKey {

    public static final List<KeyMapping> KEY_MAPPINGS = Lists.newArrayList();

    private static boolean initialized = false;

    private ExtraAnimationKey() {
    }

    public static List<KeyMapping> getKeyMappings() {
        if (!initialized) {
            initialized = true;
            if (YesSteveModel.isAvailable()) {
                for (int i = 0; i <= 7; i++) {
                    KeyMapping eventMapping = KeyMappingFactory.createInGameNone(String.format("key.sparkle_morpher.extra_animation.%d.desc", Integer.valueOf(i)), InputConstants.Type.KEYSYM, -1, "key.category.sparkle_morpher");
                    KEY_MAPPINGS.add(eventMapping);
                }
            }
        }
        return KEY_MAPPINGS;
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            onKeyInput(action, keyCode, scanCode);
            return EventResult.pass();
        });
    }

    private static void onKeyInput(int action, int keyCode, int scanCode) {
        if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady()) {
            return;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        for (KeyMapping eventMapping : KEY_MAPPINGS) {
            if (action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, eventMapping) && localPlayer != null && !AnimationLockEvent.isPlayerMoving(localPlayer)) {
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