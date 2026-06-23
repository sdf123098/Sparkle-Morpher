package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.gui.custom.AbstractConfig;
import com.micaftic.morpher.client.gui.custom.ExtraAnimationButtons;
import com.micaftic.morpher.client.gui.custom.configs.CheckboxConfig;
import com.micaftic.morpher.client.gui.custom.configs.RadioConfig;
import com.micaftic.morpher.client.gui.custom.configs.RangeConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import com.micaftic.morpher.core.gui.components.BooleanOptionRow;
import com.micaftic.morpher.core.gui.components.RadioOptionRow;
import com.micaftic.morpher.core.gui.components.SliderOptionRow;
import com.micaftic.morpher.core.gui.components.groups.IdentifiedGroup;
import com.micaftic.morpher.core.gui.molang.MolangOption;

import java.util.ArrayList;
import java.util.List;

public class ModelSettingsScreen extends OptionScreen {

    private final ModelAssembly modelAssembly;

    private final AnimatableEntity<?> animatable;

    @Nullable
    private final String initialGroupId;

    private int previewLeft, previewTop, previewRight, previewBottom;

    private float yaw = ModelPreviewRenderer.FRONT_FACING_YAW;

    private float pitch = 0.0f;

    private float zoom = 90.0f;

    private float offsetX = 0.0f;

    private float offsetY = 0.0f;

    private boolean draggingPreview;

    private int draggingButton = -1;

    public ModelSettingsScreen(ModelAssembly modelAssembly, AnimatableEntity<?> animatable, @Nullable Screen parent, @Nullable String initialGroupId) {
        super(Component.translatable("gui.sparkle_morpher.model_settings.title"), parent);
        this.modelAssembly = modelAssembly;
        this.animatable = animatable;
        this.initialGroupId = initialGroupId;
    }

    @Override
    protected int computePanelWidth() {
        return Math.min(this.width - 40, 640);
    }

    @Override
    protected int computePanelHeight() {
        return Math.min(this.height - 40, 360);
    }

    @Override
    protected boolean shouldUseCompactTabs() {
        return this.width < 620;
    }

    @Override
    protected int computeRowAreaRight() {
        return panelRight - previewWidth() - 4;
    }

    private int previewWidth() {
        if (compactTabs) {
            int panelW = panelRight - panelLeft;
            return Mth.clamp(panelW / 3, 110, 180);
        }
        return 200;
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(applyBtn);
        removeWidget(undoBtn);
        removeWidget(cancelBtn);
        applyBtn.visible = false;
        undoBtn.visible = false;
        cancelBtn.visible = false;
        applyBtn.active = false;
        undoBtn.active = false;
        saveBtn.setMessage(Component.translatable("gui.sparkle_morpher.config.done"));
        saveBtn.setX(panelRight - saveBtn.getWidth());
        previewLeft = panelRight - previewWidth();
        previewTop = rowAreaTop;
        previewRight = panelRight;
        previewBottom = panelBottom - 60;
        if (initialGroupId != null) {
            for (OptionGroup g : groups) {
                if (g instanceof IdentifiedGroup ig && initialGroupId.equals(ig.id)) {
                    selectGroup(g);
                    break;
                }
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
    }

    @Override
    protected void collectBlurRegions(List<int[]> out) {
        super.collectBlurRegions(out);
        out.add(new int[]{previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop});
    }

    @Override
    protected void registerGroups() {
        List<ExtraAnimationButtons> ordered = new ArrayList<>(modelAssembly.getModelData().getModelProperties().getExtraAnimationButtons().values());
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (ExtraAnimationButtons cfgGroup : ordered) {
            IdentifiedGroup g = new IdentifiedGroup(cfgGroup.getId(), groupLabel(cfgGroup));
            int formIndex = 0;
            for (AbstractConfig form : cfgGroup.getConfigForms()) {
                OptionRow<?> row = buildRow(cfgGroup.getId(), formIndex, form);
                if (row != null) g.add(row);
                formIndex++;
            }
            groups.add(g);
        }
    }

    private String groupLabel(ExtraAnimationButtons group) {
        String fallback = group.getName() == null || group.getName().isEmpty() ? group.getId() : group.getName();
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.name".formatted(group.getId()), fallback);
    }

    @Nullable
    private OptionRow<?> buildRow(String groupId, int formIndex, AbstractConfig form) {
        String title = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.title".formatted(groupId, formIndex), form.getTitle());
        String desc = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.description".formatted(groupId, formIndex), form.getDescription());
        if (form instanceof CheckboxConfig cfg) {
            return new BooleanOptionRow(0, 0, 0, 22, MolangOption.ofBoolean(title, desc, animatable, cfg.getValue()));
        }
        if (form instanceof RangeConfig cfg) {
            return new SliderOptionRow(0, 0, 0, 22, MolangOption.ofDouble(title, desc, animatable, cfg.getValue()), cfg.getMin(), cfg.getMax(), cfg.getStep(), "");
        }
        if (form instanceof RadioConfig cfg) {
            OrderedStringMap<String, String> labels = cfg.getLabels();
            List<String> texts = new ArrayList<>(labels.size());
            String[] writeExprs = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) {
                texts.add(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.labels.%d".formatted(groupId, formIndex, i), labels.getKeyAt(i)));
                writeExprs[i] = labels.getValueAt(i);
            }
            return new RadioOptionRow(0, 0, 0, 22, MolangOption.ofIndex(title, desc, animatable, cfg.getValue(), writeExprs), texts);
        }
        return null;
    }

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(previewLeft, previewTop, previewRight, previewBottom, 0x66000000);
        renderPreview(g, partialTick);
    }

    private void renderPreview(GuiGraphics g, float partialTick) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!(animatable instanceof LivingAnimatable<?> la)) return;
        double scale = this.minecraft.getWindow().getGuiScale();
        int sx = (int) (previewLeft * scale);
        int sy = (int) (this.minecraft.getWindow().getHeight() - previewBottom * scale);
        int sw = (int) ((previewRight - previewLeft) * scale);
        int sh = (int) ((previewBottom - previewTop) * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
        float cx = (previewLeft + previewRight) / 2.0f + offsetX;
        float cy = previewTop + (previewBottom - previewTop) * 0.65f + offsetY;
        renderPlayerForSettings(cx, cy, zoom, pitch, yaw, partialTick, la, RendererManager.getPlayerRenderer());
        RenderSystem.disableScissor();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderPlayerForSettings(float x, float y, float scale, float pitch, float yaw, float partialTick, LivingAnimatable animatable, GeoReplacedEntityRenderer renderer) {
        ModelPreviewRenderer.setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatable.getEntity();
        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1250.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(-10.0f + pitch);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        float oldHeadRotO = livingEntity.yHeadRotO;

        float previewYaw = -yaw;
        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = previewYaw;
        livingEntity.yHeadRotO = previewYaw;

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        dispatcher.overrideCameraOrientation(rotationX);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        try {
            RenderSystem.runAsFancy(() -> renderer.renderEntity(animatable, 0.0f, partialTick, poseStack, bufferSource, 15728880));
            bufferSource.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            livingEntity.yBodyRot = oldBodyRot;
            livingEntity.yBodyRotO = oldBodyRotO;
            livingEntity.setYRot(oldYRot);
            livingEntity.yRotO = oldYRotO;
            livingEntity.setXRot(oldXRot);
            livingEntity.xRotO = oldXRotO;
            livingEntity.yHeadRot = oldHeadRot;
            livingEntity.yHeadRotO = oldHeadRotO;
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            ModelPreviewRenderer.setPreviewMode(false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInPreview(mouseX, mouseY)) {
            draggingPreview = true;
            draggingButton = button;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPreview && button == draggingButton) {
            draggingPreview = false;
            draggingButton = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview && button == draggingButton) {
            if (button == 0) {
                yaw = (float) (yaw + dragX * 1.2);
                pitch = Mth.clamp((float) (pitch - dragY * 0.8), -85.0f, 85.0f);
            } else if (button == 1) {
                offsetX = (float) (offsetX + dragX);
                offsetY = (float) (offsetY + dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInPreview(mouseX, mouseY)) {
            zoom = Mth.clamp((float) (zoom * (1.0 + scrollY * 0.1)), 30.0f, 400.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInPreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX < previewRight && mouseY >= previewTop && mouseY < previewBottom;
    }

}
