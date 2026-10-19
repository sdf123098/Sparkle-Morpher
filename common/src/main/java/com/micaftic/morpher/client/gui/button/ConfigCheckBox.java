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
    public void extractContents(GuiGraphicsExtractor guiGraphics,int mouseX,int mouseY,float partialTick){
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedSurface(guiGraphics,getX(),getY(),12,12,3,
                isStateTriggered?0x55E05252:0x0CFFFFFF,isStateTriggered?0xFFE05252:0x33FFFFFF);
        if(isStateTriggered)guiGraphics.blit(com.micaftic.morpher.core.gui.RoulettePanelStyle.MODEL_PANEL_ICONS,getX()+1,getY()+1,getX()+11,getY()+11,0.0f,0.125f,0.125f,0.25f);
        guiGraphics.text(Minecraft.getInstance().font,this.component2,getX()+16,getY()+2,0xFFF0F0F0,false);
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
