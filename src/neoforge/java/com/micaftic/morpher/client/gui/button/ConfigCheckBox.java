package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ISpecialWidget;
import net.neoforged.api.distmarker.Dist;import net.neoforged.api.distmarker.OnlyIn;import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ConfigCheckBox extends StateSwitchingButton implements ISpecialWidget {


    private final Consumer<Boolean> consumer2;

    private final Component component2;

    public ConfigCheckBox(int x, int y, int width, Component component, Consumer<Boolean> consumer) {
        super(x, y, width, 12, false);
        this.component2 = component;
        this.consumer2 = consumer;
    }

    public ConfigCheckBox(int x, int y, Component component, Consumer<Boolean> consumer) {
        this(x, y, 115, component, consumer);
    }

    public void renderWidget(GuiGraphics guiGraphics,int mouseX,int mouseY,float partialTick){
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedSurface(guiGraphics,getX(),getY(),12,12,3,
                isStateTriggered?0x55E05252:0x0CFFFFFF,isStateTriggered?0xFFE05252:0x33FFFFFF);
        if(isStateTriggered)guiGraphics.blit(com.micaftic.morpher.core.gui.RoulettePanelStyle.MODEL_PANEL_ICONS,getX()+1,getY()+1,10,10,0,64,64,64,512,512);
        guiGraphics.drawString(Minecraft.getInstance().font,this.component2,getX()+16,getY()+2,0xFFF0F0F0,false);
    }

    public void onClick(double mouseX, double mouseY) {
        this.isStateTriggered = !this.isStateTriggered;
        this.consumer2.accept(Boolean.valueOf(this.isStateTriggered));
    }
}