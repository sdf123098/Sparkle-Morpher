package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SelectableModelButton extends ModelButton {
    private final String modelId;
    private final Supplier<Boolean> selected;
    private final Consumer<String> toggle;

    public SelectableModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity playerPreviewEntity,
                                 ModelAssembly textureRegistry, String modelId, Supplier<Boolean> selected,
                                 Consumer<String> toggle) {
        super(x, y, isAuthLocked, playerPreviewEntity, textureRegistry);
        this.modelId = modelId;
        this.selected = selected;
        this.toggle = toggle;
    }

    @Override
    public void onPress() {
        this.toggle.accept(this.modelId);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        if (this.selected.get()) {
            int x = getX();
            int y = getY();
            guiGraphics.fill(x, y, x + getWidth(), y + 3, 0xFF55FF88);
            guiGraphics.fill(x, y, x + 3, y + getHeight(), 0xFF55FF88);
            guiGraphics.fill(x + getWidth() - 3, y, x + getWidth(), y + getHeight(), 0xFF55FF88);
            guiGraphics.fill(x, y + getHeight() - 3, x + getWidth(), y + getHeight(), 0xFF55FF88);
        }
    }
}
