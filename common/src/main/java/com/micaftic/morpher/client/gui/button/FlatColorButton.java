package com.micaftic.morpher.client.gui.button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public class FlatColorButton extends Button {

    private boolean selected;

    private List<Component> tooltip;

    public FlatColorButton(int x, int y, int width, int height, Component component, OnPress onPress) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
        this.selected = false;
    }

    public FlatColorButton setTooltipText(String str) {
        this.tooltip = Collections.singletonList(Component.translatable(str));
        return this;
    }

    public FlatColorButton setTooltipLines(List<Component> list) {
        this.tooltip = list;
        return this;
    }

    public void renderTooltip(GuiGraphicsExtractor guiGraphics, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered && this.tooltip != null) {
            guiGraphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
/*             GuiGraphicsExtractor.renderComponentTooltip(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
 */
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        var font=Minecraft.getInstance().font;boolean hover=isHoveredOrFocused();
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedSurface(g,getX(),getY(),width,height,4,!active?0x44282828:selected?0x443F3F3F:hover?0x88505050:0x66303030,selected?0xFFE05252:hover?0x66FFFFFF:0x33FFFFFF);
        Component text=Component.literal(com.micaftic.morpher.core.gui.RoulettePanelStyle.trim(font,getMessage().getString(),Math.max(1,width-10)));
        g.text(font,text,getX()+(width-font.width(text))/2,com.micaftic.morpher.core.gui.UiElementLayout.centeredY(getY(),height,font.lineHeight),active?0xFFF0F0F0:0xFF9A9A9A,false);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
