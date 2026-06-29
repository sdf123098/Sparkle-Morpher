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
import com.micaftic.morpher.core.gui.components.groups.IdentifiedGroup;
import com.micaftic.morpher.core.gui.molang.MolangOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModelSettingsScreen extends OptionScreen {

    private final ModelAssembly modelAssembly;

    private final AnimatableEntity<?> animatable;

    @Nullable
    private final String initialGroupId;

    private final boolean renderPreview;

    private int previewLeft, previewTop, previewRight, previewBottom;
    private int closeX, closeY;
    private int contentLeft, contentTop, contentRight, contentBottom;
    private int groupLeft, groupTop, groupRight, groupBottom;
    private int rowsLeft, rowsTop, rowsRight, rowsBottom;
    private int openDropdown = -1;
    private final Map<OptionGroup, List<SettingItem>> settingItems = new HashMap<>();

    private float yaw = ModelPreviewRenderer.FRONT_FACING_YAW;

    private float pitch = 0.0f;

    private float zoom = 90.0f;

    private float offsetX = 0.0f;

    private float offsetY = 0.0f;

    private boolean draggingPreview;

    private int draggingButton = -1;

    public ModelSettingsScreen(ModelAssembly modelAssembly, AnimatableEntity<?> animatable, @Nullable Screen parent, @Nullable String initialGroupId) {
        this(modelAssembly, animatable, parent, initialGroupId, true);
    }

    public ModelSettingsScreen(ModelAssembly modelAssembly, AnimatableEntity<?> animatable, @Nullable Screen parent, @Nullable String initialGroupId, boolean renderPreview) {
        super(Component.translatable("gui.sparkle_morpher.model_settings.title"), parent);
        this.modelAssembly = modelAssembly;
        this.animatable = animatable;
        this.initialGroupId = initialGroupId;
        this.renderPreview = renderPreview;
    }

    @Override
    protected int computePanelWidth() {
        return Math.min(this.width - 32, 760);
    }

    @Override
    protected int computePanelHeight() {
        return Math.min(this.height - 32, 430);
    }

    @Override
    protected boolean shouldUseCompactTabs() {
        return true;
    }

    @Override
    protected boolean showFooterButtons() {
        return false;
    }

    @Override
    protected int computeRowAreaRight() {
        return renderPreview ? panelRight - previewWidth() - 4 : panelRight;
    }

    private int previewWidth() {
        if (compactTabs) {
            int panelW = panelRight - panelLeft;
            return Mth.clamp(panelW / 3, 150, 220);
        }
        return 200;
    }

    @Override
    protected void init() {
        super.init();
        activeRows.clear();
        openDropdown = -1;
        closeX = panelRight - RoulettePanelStyle.ICON - 6;
        closeY = panelTop + 2;
        contentLeft = panelLeft + 8;
        contentTop = panelTop + 28;
        contentRight = panelRight - 8;
        contentBottom = panelBottom - 8;
        groupLeft = contentLeft + 8;
        groupTop = contentTop + 10;
        groupRight = contentRight - 8;
        groupBottom = groupTop + 17;
        rowsLeft = contentLeft + 8;
        rowsTop = groupBottom + 11;
        rowsRight = renderPreview ? panelRight - previewWidth() - 12 : contentRight - 8;
        rowsBottom = contentBottom - 8;
        if (renderPreview) {
            previewLeft = rowsRight + 8;
            previewTop = rowsTop;
            previewRight = contentRight - 8;
            previewBottom = rowsBottom;
        } else {
            previewLeft = previewTop = previewRight = previewBottom = 0;
        }
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
        out.add(new int[]{panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop});
        out.add(new int[]{contentLeft, contentTop, contentRight - contentLeft, contentBottom - contentTop});
        if (renderPreview) {
            out.add(new int[]{previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop});
        }
    }

    @Override
    protected void registerGroups() {
        settingItems.clear();
        List<ExtraAnimationButtons> ordered = new ArrayList<>(modelAssembly.getModelData().getModelProperties().getExtraAnimationButtons().values());
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (ExtraAnimationButtons cfgGroup : ordered) {
            IdentifiedGroup g = new IdentifiedGroup(cfgGroup.getId(), groupLabel(cfgGroup));
            List<SettingItem> items = new ArrayList<>();
            int formIndex = 0;
            AbstractConfig[] forms = cfgGroup.getConfigForms();
            if (forms == null || forms.length == 0) {
                continue;
            }
            for (AbstractConfig form : forms) {
                SettingItem item = buildItem(cfgGroup.getId(), formIndex, form);
                if (item != null) items.add(item);
                formIndex++;
            }
            if (!items.isEmpty()) {
                settingItems.put(g, items);
                groups.add(g);
            }
        }
    }

    private String groupLabel(ExtraAnimationButtons group) {
        String fallback = group.getName() == null || group.getName().isEmpty() ? group.getId() : group.getName();
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.name".formatted(group.getId()), fallback);
    }

    @Nullable
    private SettingItem buildItem(String groupId, int formIndex, AbstractConfig form) {
        if (form == null) {
            return null;
        }
        String title = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.title".formatted(groupId, formIndex), form.getTitle());
        String desc = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.description".formatted(groupId, formIndex), form.getDescription());
        if (form instanceof CheckboxConfig cfg) {
            return SettingItem.bool(MolangOption.ofBoolean(title, desc, animatable, cfg.getValue()));
        }
        if (form instanceof RangeConfig cfg) {
            return SettingItem.range(MolangOption.ofDouble(title, desc, animatable, cfg.getValue()), cfg.getMin(), cfg.getMax(), cfg.getStep());
        }
        if (form instanceof RadioConfig cfg) {
            OrderedStringMap<String, String> labels = cfg.getLabels();
            List<String> texts = new ArrayList<>(labels.size());
            String[] writeExprs = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) {
                texts.add(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.labels.%d".formatted(groupId, formIndex, i), labels.getKeyAt(i)));
                writeExprs[i] = labels.getValueAt(i);
            }
            return SettingItem.radio(MolangOption.ofIndex(title, desc, animatable, cfg.getValue(), writeExprs), texts);
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, RoulettePanelStyle.BG);
        RoulettePanelStyle.glassPanel(g, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop);
        RoulettePanelStyle.fill(g, panelLeft, panelTop, panelRight - panelLeft, 22, RoulettePanelStyle.PANEL_ACTIVE);
        g.drawString(this.font, this.title, panelLeft + 8, panelTop + 7, RoulettePanelStyle.TEXT, false);
        RoulettePanelStyle.iconButton(g, mouseX, mouseY, closeX, closeY, RoulettePanelStyle.Glyph.CLOSE, true);

        RoulettePanelStyle.glassPanel(g, contentLeft, contentTop, contentRight - contentLeft, contentBottom - contentTop);
        renderGroupChips(g, mouseX, mouseY);
        renderRows(g, mouseX, mouseY);
        renderExtras(g, mouseX, mouseY, partialTick);
        renderDropdown(g, mouseX, mouseY);
    }

    private void renderGroupChips(GuiGraphics g, int mouseX, int mouseY) {
        if (groups.isEmpty()) return;
        int gap = 5;
        int chipW = Math.max(54, (groupRight - groupLeft - gap * (groups.size() - 1)) / groups.size());
        int x = groupLeft;
        for (int i = 0; i < groups.size(); i++) {
            OptionGroup group = groups.get(i);
            int w = i == groups.size() - 1 ? groupRight - x : chipW;
            boolean selected = group == activeGroup;
            boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, groupTop, w, 15);
            RoulettePanelStyle.fill(g, x, groupTop, w, 15, selected ? RoulettePanelStyle.RED_SOFT : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030);
            RoulettePanelStyle.drawCentered(g, this.font, Component.literal(RoulettePanelStyle.trim(this.font, group.getTitle().getString(), w - 6)), x + w / 2, groupTop + 4, selected ? 0xFFFFFFFF : RoulettePanelStyle.MUTED);
            x += w + gap;
        }
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        List<SettingItem> items = activeItems();
        int rowH = 22;
        int visible = Math.max(1, (rowsBottom - rowsTop) / rowH);
        maxRowScroll = Math.max(0, items.size() - visible);
        rowScrollOffset = Mth.clamp(rowScrollOffset, 0, maxRowScroll);
        int y = rowsTop;
        g.enableScissor(rowsLeft, rowsTop, rowsRight, rowsBottom);
        for (int i = rowScrollOffset; i < items.size() && y + 19 <= rowsBottom; i++) {
            renderSettingItem(g, mouseX, mouseY, rowsLeft, y, rowsRight - rowsLeft, items.get(i), i);
            y += rowH;
        }
        g.disableScissor();
        if (maxRowScroll > 0) {
            int trackX = rowsRight - 2;
            int trackH = rowsBottom - rowsTop - 2;
            int thumbH = Math.max(16, trackH * visible / Math.max(1, items.size()));
            int thumbY = rowsTop + 1 + (int) ((trackH - thumbH) * rowScrollOffset / Math.max(1, maxRowScroll));
            g.fill(trackX, rowsTop + 1, trackX + 1, rowsBottom - 1, 0x60444444);
            g.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, 0xFFEDE1CC);
        }
    }

    @SuppressWarnings("unchecked")
    private void renderSettingItem(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, SettingItem item, int index) {
        RoulettePanelStyle.fill(g, x, y, w, 19, 0x44202020);
        Component label = item.option.getLabel();
        if (item.kind == SettingKind.BOOL) {
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, label.getString(), w - 54)), x + 6, y + 6, RoulettePanelStyle.TEXT, false);
            boolean value = Boolean.TRUE.equals(((Option<Boolean>) item.option).get());
            int bx = x + w - 34;
            RoulettePanelStyle.fill(g, bx, y + 4, 26, 11, value ? RoulettePanelStyle.RED_SOFT : 0x55303030);
            RoulettePanelStyle.fill(g, bx + (value ? 15 : 2), y + 5, 9, 9, RoulettePanelStyle.TEXT);
            return;
        }
        if (item.kind == SettingKind.RANGE) {
            int valueW = 48;
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, label.getString(), w - 116)), x + 6, y + 6, RoulettePanelStyle.TEXT, false);
            RoulettePanelStyle.iconButton(g, mouseX, mouseY, x + w - 50, y + 1, RoulettePanelStyle.Glyph.MINUS, true);
            RoulettePanelStyle.iconButton(g, mouseX, mouseY, x + w - 24, y + 1, RoulettePanelStyle.Glyph.PLUS, true);
            RoulettePanelStyle.drawCentered(g, this.font, Component.literal(formatValue(((Option<Double>) item.option).get(), item.step)), x + w - 56 - valueW / 2, y + 6, RoulettePanelStyle.MUTED);
            return;
        }
        int controlW = Math.min(190, Math.max(90, w / 3));
        int cx = x + w - controlW - 8;
        g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, label.getString(), cx - x - 12)), x + 6, y + 6, RoulettePanelStyle.TEXT, false);
        boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, cx, y + 2, controlW, 15);
        RoulettePanelStyle.fill(g, cx, y + 2, controlW, 15, openDropdown == index ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030);
        RoulettePanelStyle.border(g, cx, y + 2, controlW, 15, openDropdown == index ? RoulettePanelStyle.RED : 0x33FFFFFF);
        g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, item.labelAt(currentIndex(item)), controlW - 18)), cx + 5, y + 6, 0xFFFFFFFF, false);
        g.fill(cx + controlW - 10, y + 8, cx + controlW - 4, y + 9, RoulettePanelStyle.MUTED);
        g.fill(cx + controlW - 9, y + 9, cx + controlW - 5, y + 10, RoulettePanelStyle.MUTED);
        g.fill(cx + controlW - 8, y + 10, cx + controlW - 6, y + 11, RoulettePanelStyle.MUTED);
    }

    private void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        List<SettingItem> items = activeItems();
        if (openDropdown < 0 || openDropdown >= items.size()) return;
        SettingItem item = items.get(openDropdown);
        if (item.kind != SettingKind.RADIO || item.labels.isEmpty()) return;
        DropdownBounds bounds = dropdownBounds(item);
        if (bounds == null) return;
        RoulettePanelStyle.secondaryGlassPanel(g, bounds.x, bounds.y, bounds.w, bounds.h);
        int current = currentIndex(item);
        int visible = Math.min(8, item.labels.size());
        for (int i = 0; i < visible; i++) {
            boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, bounds.x + 1, bounds.y + 1 + i * 16, bounds.w - 2, 16);
            boolean selected = i == current;
            RoulettePanelStyle.fill(g, bounds.x + 1, bounds.y + 1 + i * 16, bounds.w - 2, 16, selected ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x00000000);
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, item.labelAt(i), bounds.w - 10)), bounds.x + 5, bounds.y + 5 + i * 16, selected ? 0xFFFFFFFF : RoulettePanelStyle.TEXT, false);
        }
    }

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!renderPreview) {
            return;
        }
        RoulettePanelStyle.secondaryGlassPanel(g, previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop);
        renderPreview(g, partialTick);
    }

    @Override
    protected void renderHeaderActions(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RoulettePanelStyle.iconButton(g, mouseX, mouseY, closeX, closeY, RoulettePanelStyle.Glyph.CLOSE, true);
    }

    @Override
    protected boolean headerMouseClicked(double mouseX, double mouseY, int button) {
        if (RoulettePanelStyle.inside(mouseX, mouseY, closeX, closeY, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON)) {
            onClose();
            return true;
        }
        return false;
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
        try {
            float cx = (previewLeft + previewRight) / 2.0f + offsetX;
            float cy = previewTop + (previewBottom - previewTop) * 0.65f + offsetY;
            renderPlayerForSettings(cx, cy, zoom, pitch, yaw, partialTick, la, RendererManager.getPlayerRenderer());
        } finally {
            RenderSystem.disableScissor();
        }
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
        if (RoulettePanelStyle.inside(mouseX, mouseY, closeX, closeY, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON)) {
            onClose();
            return true;
        }
        if (handleDropdownClick(mouseX, mouseY)) {
            return true;
        }
        openDropdown = -1;
        if (handleGroupClick(mouseX, mouseY)) {
            return true;
        }
        if (handleRowClick(mouseX, mouseY)) {
            return true;
        }
        if (renderPreview && isInPreview(mouseX, mouseY)) {
            draggingPreview = true;
            draggingButton = button;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPreview && button == draggingButton) {
            draggingPreview = false;
            draggingButton = -1;
            return true;
        }
        return false;
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
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (renderPreview && isInPreview(mouseX, mouseY)) {
            zoom = Mth.clamp((float) (zoom * (1.0 + scrollY * 0.1)), 30.0f, 400.0f);
            return true;
        }
        if (RoulettePanelStyle.inside(mouseX, mouseY, rowsLeft, rowsTop, rowsRight - rowsLeft, rowsBottom - rowsTop) && maxRowScroll > 0) {
            rowScrollOffset = Mth.clamp((int) (rowScrollOffset - scrollY), 0, maxRowScroll);
            openDropdown = -1;
            return true;
        }
        return false;
    }

    private boolean isInPreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX < previewRight && mouseY >= previewTop && mouseY < previewBottom;
    }

    private boolean handleGroupClick(double mouseX, double mouseY) {
        if (groups.isEmpty() || !RoulettePanelStyle.inside(mouseX, mouseY, groupLeft, groupTop, groupRight - groupLeft, 15)) return false;
        int gap = 5;
        int chipW = Math.max(54, (groupRight - groupLeft - gap * (groups.size() - 1)) / groups.size());
        int x = groupLeft;
        for (int i = 0; i < groups.size(); i++) {
            int w = i == groups.size() - 1 ? groupRight - x : chipW;
            if (RoulettePanelStyle.inside(mouseX, mouseY, x, groupTop, w, 15)) {
                activeGroup = groups.get(i);
                rowScrollOffset = 0;
                openDropdown = -1;
                return true;
            }
            x += w + gap;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleRowClick(double mouseX, double mouseY) {
        if (!RoulettePanelStyle.inside(mouseX, mouseY, rowsLeft, rowsTop, rowsRight - rowsLeft, rowsBottom - rowsTop)) return false;
        List<SettingItem> items = activeItems();
        int index = rowScrollOffset + (int) ((mouseY - rowsTop) / 22);
        if (index < 0 || index >= items.size()) return true;
        int localY = rowsTop + (index - rowScrollOffset) * 22;
        SettingItem item = items.get(index);
        int rowW = rowsRight - rowsLeft;
        if (item.kind == SettingKind.BOOL) {
            int bx = rowsLeft + rowW - 38;
            if (RoulettePanelStyle.inside(mouseX, mouseY, bx, localY, 34, 19)) {
                Option<Boolean> option = (Option<Boolean>) item.option;
                option.setPending(!Boolean.TRUE.equals(option.get()));
            }
            return true;
        }
        if (item.kind == SettingKind.RANGE) {
            Option<Double> option = (Option<Double>) item.option;
            double current = option.get() == null ? item.min : option.get();
            if (RoulettePanelStyle.inside(mouseX, mouseY, rowsLeft + rowW - 50, localY + 1, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON)) {
                option.setPending(clampToStep(current - item.stepValue(), item.min, item.max, item.stepValue()));
                return true;
            }
            if (RoulettePanelStyle.inside(mouseX, mouseY, rowsLeft + rowW - 24, localY + 1, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON)) {
                option.setPending(clampToStep(current + item.stepValue(), item.min, item.max, item.stepValue()));
            }
            return true;
        }
        int controlW = Math.min(190, Math.max(90, rowW / 3));
        int cx = rowsLeft + rowW - controlW - 8;
        if (RoulettePanelStyle.inside(mouseX, mouseY, cx, localY + 2, controlW, 15)) {
            openDropdown = openDropdown == index ? -1 : index;
            return true;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleDropdownClick(double mouseX, double mouseY) {
        List<SettingItem> items = activeItems();
        if (openDropdown < 0 || openDropdown >= items.size()) return false;
        SettingItem item = items.get(openDropdown);
        if (item.kind != SettingKind.RADIO) return false;
        DropdownBounds bounds = dropdownBounds(item);
        if (bounds == null || !RoulettePanelStyle.inside(mouseX, mouseY, bounds.x, bounds.y, bounds.w, bounds.h)) return false;
        int slot = (int) ((mouseY - bounds.y - 1) / 16);
        if (slot >= 0 && slot < Math.min(8, item.labels.size())) {
            ((Option<Integer>) item.option).setPending(slot);
            openDropdown = -1;
        }
        return true;
    }

    private DropdownBounds dropdownBounds(SettingItem item) {
        int slot = openDropdown - rowScrollOffset;
        int rowY = rowsTop + slot * 22;
        if (rowY < rowsTop || rowY + 19 > rowsBottom) return null;
        int rowW = rowsRight - rowsLeft;
        int controlW = Math.min(190, Math.max(90, rowW / 3));
        int x = rowsLeft + rowW - controlW - 8;
        int y = rowY + 19;
        int h = Math.min(8, item.labels.size()) * 16 + 2;
        if (y + h > rowsBottom) y = rowY - h;
        return new DropdownBounds(x, y, controlW, h);
    }

    private List<SettingItem> activeItems() {
        if (activeGroup == null) return List.of();
        return settingItems.getOrDefault(activeGroup, List.of());
    }

    private int currentIndex(SettingItem item) {
        if (!(item.option.get() instanceof Integer value)) return 0;
        return Mth.clamp(value, 0, Math.max(0, item.labels.size() - 1));
    }

    private static String formatValue(Double value, double step) {
        double v = value == null ? 0.0d : value;
        if (step >= 1.0d) return String.format(Locale.ROOT, "%.0f", v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static double clampToStep(double value, double min, double max, double step) {
        double clamped = Mth.clamp(value, min, max);
        if (step > 0.0d) clamped = step * Math.round(clamped / step);
        return Mth.clamp(clamped, min, max);
    }

    private enum SettingKind {
        BOOL,
        RANGE,
        RADIO
    }

    private record SettingItem(SettingKind kind, Option<?> option, double min, double max, double step, List<String> labels) {
        static SettingItem bool(Option<Boolean> option) {
            return new SettingItem(SettingKind.BOOL, option, 0.0d, 1.0d, 1.0d, List.of());
        }

        static SettingItem range(Option<Double> option, double min, double max, double step) {
            return new SettingItem(SettingKind.RANGE, option, Math.min(min, max), Math.max(min, max), Math.max(step, 0.0d), List.of());
        }

        static SettingItem radio(Option<Integer> option, List<String> labels) {
            return new SettingItem(SettingKind.RADIO, option, 0.0d, 0.0d, 0.0d, List.copyOf(labels));
        }

        String labelAt(int index) {
            if (index < 0 || index >= labels.size()) return "";
            return labels.get(index);
        }

        double stepValue() {
            return step > 0.0d ? step : 1.0d;
        }
    }

    private record DropdownBounds(int x, int y, int w, int h) {
    }

}
