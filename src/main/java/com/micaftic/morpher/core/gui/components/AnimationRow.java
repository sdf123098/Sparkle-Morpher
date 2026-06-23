package com.micaftic.morpher.core.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.ModernPlayerTextureScreen;
import com.micaftic.morpher.core.gui.OptionRow;

public final class AnimationRow extends OptionRow<Object> {
    public final String animKey;
    private final ModernPlayerTextureScreen owner;

    public AnimationRow(int x, int y, int width, int height, String animKey, ModernPlayerTextureScreen owner) {
        super(x, y, width, height, null);
        this.animKey = animKey;
        this.owner = owner;
        String i18nKey = "gui.sparkle_morpher.texture.button." + animKey.replace(':', '.');
        Component label = I18n.exists(i18nKey) ? Component.translatable(i18nKey) : Component.literal(animKey);
        setMessage(label);
    }

    public boolean matches(String lowerSearch) {
        return animKey.toLowerCase().contains(lowerSearch) || getMessage().getString().toLowerCase().contains(lowerSearch);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean selected = animKey.equals(owner.currentAnimation());
        int bg = selected ? 0x90333333 : (isHovered() ? 0x90171717 : 0x90000000);
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        if (selected) g.fill(getX(), getY(), getX() + 2, getY() + height, -1);
        int textY = getY() + (height - 8) / 2;
        g.drawString(Minecraft.getInstance().font, getMessage(), getX() + 8, textY, -1, false);
    }

    @Override
    protected void renderControl(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        owner.selectAnimation(animKey);
    }
}
