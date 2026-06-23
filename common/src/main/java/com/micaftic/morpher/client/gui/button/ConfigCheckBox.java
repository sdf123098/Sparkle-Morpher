package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ISpecialWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.navigation.WidgetSprites;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ConfigCheckBox extends StateSwitchingButton implements ISpecialWidget {

    private static final Identifier location = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");

    private final Consumer<Boolean> consumer2;

    private final Component component2;

    public ConfigCheckBox(int x, int y, int width, Component component, Consumer<Boolean> consumer) {
        super(x, y, width, 12, false);
        this.component2 = component;
        this.consumer2 = consumer;
        initTextureValues(new WidgetSprites(location, location, location, location));
    }

    public ConfigCheckBox(int x, int y, Component component, Consumer<Boolean> consumer) {
        this(x, y, 115, component, consumer);
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        int uOffset = isStateTriggered ? 128 : 0;
        int vOffset = isHovered() ? 12 : 0;
        int boxSize = 12;
        guiGraphics.blit(location, getX(), getY(), getX() + boxSize, getY() + boxSize, uOffset / 256.0f, (uOffset + boxSize) / 256.0f, vOffset / 24.0f, (vOffset + boxSize) / 24.0f);
        guiGraphics.text(Minecraft.getInstance().font, this.component2, getX() + boxSize + 2, getY() + 2, -1, false);
    }

    public void onClick(MouseButtonEvent event, boolean flag) {
        this.isStateTriggered = !this.isStateTriggered;
        this.consumer2.accept(Boolean.valueOf(this.isStateTriggered));
    }
}
