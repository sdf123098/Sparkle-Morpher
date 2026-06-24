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
 * Injects four Sparkle Morpher buttons into the vanilla pause screen
 * when the server has the Android online-model bridge enabled. The set
 * is identical across all four Sparkle Morpher subprojects: skin,
 * extra-player render, animation roulette, and a YSM settings shortcut.
 */
public class PauseScreenButtonBuilder {
    public static boolean isServerConnected() {
        return YesSteveModel.isOnAndroid();
    }

    @Nullable
    public static List<Button> createButtons(PauseScreen pauseScreen) {
        if (!isServerConnected()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        int baseY = pauseScreen.height - 35;
        int cx = pauseScreen.width / 2;

        Button skinBtn = Button.builder(Component.translatable("gui.sparkle_morpher.skin"), button -> {
            minecraft.setScreen(new ModernPlayerModelScreen());
        }).bounds(cx - 94, baseY, 138, 30).build();
        skinBtn.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.player_model.desc")));

        Button renderBtn = Button.builder(Component.literal("棣冩暋"), button -> {
            minecraft.setScreen(new ExtraPlayerRenderScreen());
        }).bounds(cx - 145, baseY, 50, 30).build();
        renderBtn.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.open_extra_player_render.desc")));

        Button rouletteBtn = Button.builder(Component.literal("棣冩"), button -> {
            if (minecraft.player == null) return;
            PlayerCapability.get(minecraft.player).ifPresent(cap -> {
                String modelId = cap.getModelId();
                ModelAssembly modelAssembly = cap.getModelAssembly();
                if (modelAssembly != null && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                    minecraft.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
                }
            });
        }).bounds(cx + 45, baseY, 50, 30).build();
        rouletteBtn.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.animation_roulette.desc")));

        Button configBtn = Button.builder(Component.literal("sparkle"), button -> {
            minecraft.setScreen(ModernPlayerModelScreen.settings());
        }).bounds(cx + 96, baseY, 50, 30).build();
        configBtn.setTooltip(Tooltip.create(Component.translatable("gui.sparkle_morpher.config")));

        return List.of(skinBtn, renderBtn, rouletteBtn, configBtn);
    }
}
