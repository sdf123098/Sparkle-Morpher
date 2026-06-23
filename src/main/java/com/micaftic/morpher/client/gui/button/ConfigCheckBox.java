package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ISpecialWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class ConfigCheckBox extends AbstractButton implements ISpecialWidget {

    private static final Identifier location = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");

    private final Consumer<Boolean> consumer2;

    private final Component component2;
    private boolean isStateTriggered;

    public ConfigCheckBox(int x, int y, int width, Component component, Consumer<Boolean> consumer) {
        super(x, y, width, 12, Component.empty());
        this.component2 = component;
        this.consumer2 = consumer;
    }

    public ConfigCheckBox(int x, int y, Component component, Consumer<Boolean> consumer) {
        this(x, y, 115, component, consumer);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        int uOffset = isStateTriggered ? 128 : 0;
        int vOffset = isHovered() ? 12 : 0;
        int boxSize = 12;
        guiGraphics.blit(location, getX(), getY(), getX() + boxSize, getY() + boxSize, uOffset / 256.0f, (uOffset + boxSize) / 256.0f, vOffset / 24.0f, (vOffset + boxSize) / 24.0f);
        guiGraphics.text(Minecraft.getInstance().font, this.component2, getX() + boxSize + 2, getY() + 2, -1, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        setStateTriggered(!this.isStateTriggered);
        this.consumer2.accept(this.isStateTriggered);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        setStateTriggered(!this.isStateTriggered);
        this.consumer2.accept(this.isStateTriggered);
    }

    public void setStateTriggered(boolean stateTriggered) {
        this.isStateTriggered = stateTriggered;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
