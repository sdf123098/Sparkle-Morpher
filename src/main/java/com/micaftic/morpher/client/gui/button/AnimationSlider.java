package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ISpecialWidget;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.parser.ParseException;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestExecuteMolangPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;

public class AnimationSlider extends RangedSliderWidget implements ISpecialWidget {

    private static final Identifier ROULETTE_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    private final AnimatableEntity<?> model;

    private final String controllerName;

    public AnimationSlider(int x, int y, Component component, double currentValue, AnimatableEntity<?> animatableEntity, String controllerName, double stepSize, double minValue, double maxValue) {
        super(x, y, 115, 15, component, Component.empty(), minValue, maxValue, currentValue, stepSize, 0, true);
        this.model = animatableEntity;
        this.controllerName = controllerName;
    }

    @Override
    protected void applyValue() {
        try {
            String str = this.controllerName + "=" + getValue();
            this.model.executeExpression(GeckoLibCache.parseSimpleExpression(str), true, false, null);
            if (!GeckoLibCache.isRoamingVariableAssignment(str) && NetworkHandler.isClientConnected() && !ServerConfig.LOW_BANDWIDTH_USAGE.get().booleanValue()) {
                NetworkHandler.sendToServer(new C2SRequestExecuteMolangPacket(str, this.model.getEntity().getId()));
            }
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error(e);
        }
    }

    @Override
    public String getValueString() {
        return DECIMAL_FORMAT.format(getValue());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        Minecraft minecraft = Minecraft.getInstance();
        blitWithBorder(guiGraphics, ROULETTE_TEXTURE, getX(), getY(), 0, getTextureY() + 24, this.width, this.height, 200, 15, 2, 3, 2, 2);
        int handleX = getX() + (int) (this.value * (this.width - 8));
        blitWithBorder(guiGraphics, ROULETTE_TEXTURE, handleX, getY(), 0, getHandleTextureY() + 24, 8, this.height, 200, 15, 2, 3, 2, 2);

        int color = 16777215 | (Mth.ceil(this.alpha * 255.0f) << 24);
        guiGraphics.centeredText(minecraft.font, this.getMessage(), getX() + this.width / 2, getY() + (this.height - 8) / 2, color);
    }
}