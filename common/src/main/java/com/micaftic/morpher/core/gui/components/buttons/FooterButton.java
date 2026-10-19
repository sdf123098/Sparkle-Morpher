package com.micaftic.morpher.core.gui.components.buttons;

import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FooterButton extends AbstractWidget {
    private final Runnable onPress;
    private final RoulettePanelStyle.Glyph glyph;

    public FooterButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        this(x, y, width, height, label, onPress, RoulettePanelStyle.Glyph.CHECK);
    }

    public FooterButton(int x, int y, int width, int height, Component label, Runnable onPress, RoulettePanelStyle.Glyph glyph) {
        super(x, y, width, height, label);
        this.onPress = onPress;
        this.glyph = glyph;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        boolean hover=active&&isHovered();RoulettePanelStyle.roundedSurface(g,getX(),getY(),width,height,4,!active?0x44282828:hover?0x88505050:0x66303030,hover?RoulettePanelStyle.RED:0x33FFFFFF);
        var font=net.minecraft.client.Minecraft.getInstance().font;
        if(width>32){var text=Component.literal(RoulettePanelStyle.trim(font,getMessage().getString(),width-12));g.text(font,text,getX()+(width-font.width(text))/2,com.micaftic.morpher.core.gui.UiElementLayout.centeredY(getY(),height,font.lineHeight),active?RoulettePanelStyle.TEXT:RoulettePanelStyle.MUTED,false);}
        else{int size=Math.min(14,Math.min(width,height)-6);RoulettePanelStyle.drawIconScaled(g,glyph,getX()+(width-size)/2,getY()+(height-size)/2,size);}
        if(hover)g.setComponentTooltipForNextFrame(font,java.util.List.of(getMessage()),mouseX,mouseY);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        if (active) onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
