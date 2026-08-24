package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Injects three Sparkle Morpher buttons into the vanilla pause screen
 * when the server has the Android online-model bridge enabled. The set
 * is identical across all Sparkle Morpher subprojects: skin,
 * animation roulette, and a YSM settings shortcut.
 */
public class PauseScreenButtonBuilder {
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_GAP = 4;
    private static final int SCREEN_MARGIN = 10;

    public static boolean isServerConnected() {
        return YesSteveModel.isOnAndroid();
    }

    @Nullable
    public static List<Button> createButtons(PauseScreen pauseScreen) {
        if (!isServerConnected()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        Component skinLabel = Component.translatable("gui.sparkle_morpher.skin");
        Component rouletteLabel = Component.translatable("gui.sparkle_morpher.config.roulette_mode");
        Component configLabel = Component.translatable("gui.sparkle_morpher.config");

        int skinWidth = buttonWidth(minecraft, skinLabel, 110, 138);
        int rouletteWidth = buttonWidth(minecraft, rouletteLabel, 50, 96);
        int configWidth = buttonWidth(minecraft, configLabel, 50, 100);
        int availableWidth = Math.max(0, pauseScreen.width - SCREEN_MARGIN * 2);
        int rowWidth = skinWidth + rouletteWidth + configWidth + BUTTON_GAP * 2;
        boolean twoRows = rowWidth > availableWidth;

        int baseY = pauseScreen.height - BUTTON_HEIGHT - 5;
        int rowX = (pauseScreen.width - rowWidth) / 2;
        int skinX = rowX;
        int rouletteX = skinX + skinWidth + BUTTON_GAP;
        int configX = rouletteX + rouletteWidth + BUTTON_GAP;
        int skinY = baseY;
        int controlY = baseY;

        if (twoRows) {
            int controlsWidth = rouletteWidth + configWidth + BUTTON_GAP;
            skinWidth = Math.min(skinWidth, availableWidth);
            skinX = (pauseScreen.width - skinWidth) / 2;
            skinY = pauseScreen.height - BUTTON_HEIGHT * 2 - BUTTON_GAP - 5;
            rouletteX = (pauseScreen.width - controlsWidth) / 2;
            configX = rouletteX + rouletteWidth + BUTTON_GAP;
            controlY = skinY + BUTTON_HEIGHT + BUTTON_GAP;
        }

        Button skinBtn = Button.builder(skinLabel, button -> {
            minecraft.setScreen(new ModernPlayerModelScreen());
        }).bounds(skinX, skinY, skinWidth, BUTTON_HEIGHT).build();
        skinBtn.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.player_model.desc")));

        Button rouletteBtn = Button.builder(rouletteLabel, button -> {
            if (minecraft.player == null) return;
            PlayerCapability.get(minecraft.player).ifPresent(cap -> {
                String modelId = cap.getModelId();
                ModelAssembly modelAssembly = cap.getModelAssembly();
                if (modelAssembly != null && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                    minecraft.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
                }
            });
        }).bounds(rouletteX, controlY, rouletteWidth, BUTTON_HEIGHT).build();
        rouletteBtn.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.animation_roulette.desc")));

        Button configBtn = Button.builder(configLabel, button -> {
            minecraft.setScreen(ModernPlayerModelScreen.settings());
        }).bounds(configX, controlY, configWidth, BUTTON_HEIGHT).build();
        configBtn.setTooltip(Tooltip.create(Component.translatable("gui.sparkle_morpher.config")));

        return List.of(skinBtn, rouletteBtn, configBtn);
    }

    private static int buttonWidth(Minecraft minecraft, Component label, int minWidth, int maxWidth) {
        return Math.min(maxWidth, Math.max(minWidth, minecraft.font.width(label) + 20));
    }
}
