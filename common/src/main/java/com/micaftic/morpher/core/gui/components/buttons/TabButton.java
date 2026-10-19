package com.micaftic.morpher.core.gui.components.buttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import com.micaftic.morpher.core.gui.OptionGroup;

import java.util.function.Consumer;

public class TabButton extends AbstractWidget {

    private final OptionGroup group;
    private final Consumer<OptionGroup> onSelect;
    private boolean selected;
    private boolean horizontal;

    public TabButton(int x, int y, int width, int height, OptionGroup group, Consumer<OptionGroup> onSelect) {
        super(x, y, width, height, group.getTitle());
        this.group = group;
        this.onSelect = onSelect;
    }

    public OptionGroup getGroup() {
        return group;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        var font=Minecraft.getInstance().font;var text=net.minecraft.network.chat.Component.literal(com.micaftic.morpher.core.gui.RoulettePanelStyle.trim(font,getMessage().getString(),Math.max(1,width-12)));
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedSurface(g,getX(),getY(),width,height,4,selected?0x443F3F3F:isHovered()?0x88505050:0x44303030,selected?0xFFE05252:0x33FFFFFF);
        g.text(font,text,getX()+(width-font.width(text))/2,com.micaftic.morpher.core.gui.UiElementLayout.centeredY(getY(),height,font.lineHeight),0xFFF0F0F0,false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        onSelect.accept(group);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
