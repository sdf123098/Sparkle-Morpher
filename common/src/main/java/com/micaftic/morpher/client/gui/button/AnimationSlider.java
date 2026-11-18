package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.client.motion.ClientMotionSources;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostMolangRegistry;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.client.gui.ISpecialWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;

public class AnimationSlider extends RangedSliderWidget implements ISpecialWidget {


    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    private final HostAnimatable<?> model;

    private final String controllerName;

    public AnimationSlider(int x, int y, Component component, double currentValue, HostAnimatable<?> animatableEntity, String controllerName, double stepSize, double minValue, double maxValue) {
        super(x, y, 115, 15, component, Component.empty(), minValue, maxValue, currentValue, stepSize, 0, true);
        this.model = animatableEntity;
        this.controllerName = controllerName;
    }

    @Override
    protected void applyValue() {
        String str = this.controllerName + "=" + getValue();
        HostMolangScript script = HostMolangRegistry.parseExpression(str);
        if (!script.parsedSuccessfully()) {
            YesSteveModel.LOGGER.error("Failed to parse slider expression '{}': {}", str, script.failure());
            return;
        }
        ClientMotionSources.beginEdit(this.model);
        com.micaftic.morpher.cloud.client.CloudEntityMotionSync.rememberSetting(this.model, this.controllerName, str);
        this.model.executeExpression(script, true, false, null);
        if (!HostMolangRegistry.isRoamingVariableAssignment(str)) {
            ClientMotionSources.expression(this.model, str, java.util.List.of());
        }
    }

    @Override
    public String getValueString() {
        return DECIMAL_FORMAT.format(getValue());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics,int mouseX,int mouseY,float partialTick){
        int cy=getY()+height/2,handleX=getX()+(int)(this.value*(this.width-8));
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedFill(guiGraphics,getX(),cy-2,width,4,2,0x55444444);
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedFill(guiGraphics,getX(),cy-2,Math.max(1,handleX-getX()+4),4,2,0xFFE05252);
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedFill(guiGraphics,handleX,cy-5,8,10,4,0xFFF0F0F0);
        var font=Minecraft.getInstance().font;
        guiGraphics.text(font,getMessage(),getX()+(width-font.width(getMessage()))/2,getY()+(height-8)/2,0xFFF0F0F0,false);
    }
}
