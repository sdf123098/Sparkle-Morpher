package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.preview.PaperDollLayout;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 全屏 HUD 布局编辑器：直接在当前 GUI 缩放下展示真实位置的 HUD 小人，
 * 拖动小人本身调整位置，边角把手/滚轮缩放，右键旋转，Alt+R 重置。
 *
 * <p>位置以「锚点 + 偏移」保存，与 {@link PaperDollLayout} 和实际 HUD 渲染共用同一套
 * 解析逻辑，因此不同分辨率下不会出现编辑框与实际模型脱节。</p>
 */
public final class HudLayoutScreen extends Screen {
    private static final float MIN_SCALE = 8.0f;
    private static final float MAX_SCALE = 360.0f;
    private static final int HANDLE_RADIUS = 9;
    private static final int ANCHOR_CELL = 20;
    private static final int ANCHOR_GAP = 4;

    private final Screen parent;
    private final HudLayoutConfig config;
    private final HudPreview preview;
    private final Consumer<PaperDollLayout.Anchor> anchorSink;
    private PaperDollLayout.Anchor anchor;
    private int offsetX;
    private int offsetY;
    private float playerScale;
    private float playerYaw;
    private DragMode dragMode = DragMode.NONE;
    private double grabOffsetX;
    private double grabOffsetY;
    private float fixedX;
    private float fixedY;
    private boolean panelVisible = true;
    private EditBox xField, yField, scaleField;

    /** 预览回调：把编辑中的（未保存）布局值渲染到实际屏幕坐标；返回是否成功绘制。 */
    @FunctionalInterface
    public interface HudPreview {
        boolean render(GuiGraphics graphics, LocalPlayer player, float partialTick,
                       int screenWidth, int screenHeight, float x, float y, float scale, float yaw);
    }

    public HudLayoutScreen(Screen parent, Component title, HudLayoutConfig config, HudPreview preview) {
        this(parent, title, config, preview, PaperDollLayout.Anchor.TOP_LEFT, ignored -> { });
    }

    public HudLayoutScreen(Screen parent, Component title, HudLayoutConfig config, HudPreview preview,
                           PaperDollLayout.Anchor anchor, Consumer<PaperDollLayout.Anchor> anchorSink) {
        super(title);
        this.parent = parent;
        this.config = config;
        this.preview = preview;
        this.anchor = anchor == null ? PaperDollLayout.Anchor.TOP_LEFT : anchor;
        this.anchorSink = anchorSink == null ? ignored -> { } : anchorSink;
        this.offsetX = config.getX();
        this.offsetY = config.getY();
        this.playerScale = config.getScale();
        this.playerYaw = config.getYaw();
    }

    /** 经典 HUD 预览：与实际 HUD 走同一条渲染路径，直接使用屏幕坐标。 */
    public static HudPreview classicPreview() {
        return (graphics, player, partialTick, w, h, x, y, scale, yaw) -> {
            ModelPreviewRenderer.renderPlayerOverlay(graphics, player, x, y, scale, yaw, -500, partialTick);
            return true;
        };
    }

    @Override
    protected void init() {
        int fx = fieldX();
        int fw = fieldW();
        int top = fieldsTop();
        xField = valueField(fx, top, fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_x"),
                Integer.toString(offsetX), value -> {
                    try { offsetX = clampOffsetX(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
                });
        yField = valueField(fx, top + rowGap(), fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_y"),
                Integer.toString(offsetY), value -> {
                    try { offsetY = clampOffsetY(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
                });
        scaleField = valueField(fx, top + rowGap() * 2, fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_scale"),
                scaleText(), value -> {
                    try {
                        playerScale = Mth.clamp(Float.parseFloat(value) * 40f, MIN_SCALE, MAX_SCALE);
                        clampOffsets();
                    } catch (NumberFormatException ignored) { }
                });
        setPanelVisible(panelVisible);
    }

    private EditBox valueField(int x, int y, int w, Component label, String value, Consumer<String> change) {
        EditBox field = new EditBox(font, x + 6, y + 5, Math.max(12, w - 12), 12, label);
        field.setBordered(false);
        field.setValue(value);
        field.setResponder(change);
        addRenderableWidget(field);
        return field;
    }

    private int panelW() { return Math.min(304, Math.max(224, width - 24)); }
    private int panelH() { return Math.min(276, Math.max(220, height - 24)); }
    private int panelX() { return Math.max(12, width - panelW() - 16); }
    private int panelY() { return height < 380 ? Math.max(12, height - panelH() - 12) : 16; }
    private int contentTop() { return panelY() + (panelH() < 248 ? 92 : 100); }
    private int fieldsTop() { return contentTop() + 14; }
    private int rowGap() { return panelH() < 248 ? 30 : 34; }
    private int fieldX() { return panelX() + 112; }
    private int fieldW() { return Math.max(80, panelW() - 126); }
    private int closeX() { return panelX() + panelW() - 34; }
    private int closeY() { return panelY() + 9; }
    private int collapseX() { return panelX() + panelW() - 62; }
    private int collapseY() { return panelY() + 9; }
    private int pillSize() { return 24; }
    private int resetY() { return panelY() + panelH() - 32; }
    private int resetX() { return panelX() + 14; }
    private int resetW() { return Math.max(42, (panelW() - 36) / 2); }
    private int doneX() { return panelX() + panelW() - 14 - resetW(); }

    private int dollW() { return Math.max(1, Math.round(playerScale)); }
    private int dollH() { return Math.max(1, Math.round(playerScale * 2.0f)); }

    private PaperDollLayout.Position position() {
        return PaperDollLayout.resolve(anchor, width, height, offsetX, offsetY, dollW(), dollH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RoulettePanelStyle.fill(graphics, 0, 0, width, height, 0x4A000000);
        PaperDollLayout.Position position = position();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            preview.render(graphics, player, partialTick, width, height, position.x(), position.y(), playerScale, playerYaw);
        }
        drawSelection(graphics, position.x(), position.y(), mouseX, mouseY);
        drawPanel(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSelection(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = dollW(), h = dollH();
        boolean active = dragMode != DragMode.NONE || insideDoll(mouseX, mouseY);
        int color = active ? 0xDCE05252 : 0x88FFFFFF;
        // 只画四角括线：包围盒与实际渲染尺寸一致，但不会像整框那样在留白处显得脱离模型。
        int arm = Math.min(16, Math.max(6, Math.min(w, h) / 3));
        int left = x - 2, top = y - 2, right = x + w + 2, bottom = y + h + 2;
        for (int[] corner : new int[][] { { left, top, 1, 1 }, { right, top, -1, 1 },
                { left, bottom, 1, -1 }, { right, bottom, -1, -1 } }) {
            int cx = corner[0], cy = corner[1], dx = corner[2], dy = corner[3];
            RoulettePanelStyle.roundedFill(g, Math.min(cx, cx + dx * arm), cy, arm, 2, 1, color);
            RoulettePanelStyle.roundedFill(g, cx, Math.min(cy, cy + dy * arm), 2, arm, 1, color);
        }
        for (int hx : new int[] { x, x + w }) {
            for (int hy : new int[] { y, y + h }) {
                RoulettePanelStyle.roundedSurface(g, hx - 4, hy - 4, 8, 8, 3,
                        near(mouseX, mouseY, hx, hy) ? 0xFFFFD0D0 : 0xF2F4F4F4, 0x66000000);
            }
        }
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (!panelVisible) {
            int x = panelX(), y = panelY(), size = pillSize();
            boolean hover = inside(mouseX, mouseY, x, y, size);
            RoulettePanelStyle.roundedSurface(g, x, y, size, size, 5,
                    hover ? 0x18FFFFFF : 0x0CFFFFFF, 0x22FFFFFF);
            RoulettePanelStyle.drawIconScaled(g, RoulettePanelStyle.Glyph.SETTINGS, x + 4, y + 4, 16);
            return;
        }
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        RoulettePanelStyle.glassPanel(g, px, py, pw, ph);
        int closeX = closeX(), closeY = closeY();
        boolean closeHover = inside(mouseX, mouseY, closeX, closeY, 22);
        RoulettePanelStyle.roundedSurface(g, closeX, closeY, 22, 22, 4,
                closeHover ? 0x18FFFFFF : 0x0CFFFFFF, 0x22FFFFFF);
        RoulettePanelStyle.drawIconScaled(g, RoulettePanelStyle.Glyph.CLOSE, closeX + 5, closeY + 5, 12);
        int collapseX = collapseX(), collapseY = collapseY();
        boolean collapseHover = inside(mouseX, mouseY, collapseX, collapseY, 22);
        RoulettePanelStyle.roundedSurface(g, collapseX, collapseY, 22, 22, 4,
                collapseHover ? 0x18FFFFFF : 0x0CFFFFFF, 0x22FFFFFF);
        RoulettePanelStyle.drawIconScaled(g, RoulettePanelStyle.Glyph.MINUS, collapseX + 5, collapseY + 5, 12);
        g.drawString(font, Component.translatable("gui.sparkle_morpher.classic_hud_layout.title"),
                px + 16, py + 15, 0xFFF4F4F4, false);

        Component tips = Component.translatable("gui.sparkle_morpher.classic_hud_layout.tips");
        var tipLines = font.split(tips, Math.max(40, pw - 32));
        int tipY = py + 34;
        int lineH = Math.max(9, font.lineHeight);
        int maxTips = ph < 248 ? 2 : 3;
        for (int i = 0; i < Math.min(maxTips, tipLines.size()); i++) {
            g.drawString(font, tipLines.get(i), px + 16, tipY + i * lineH, 0xFFB1B1B1, false);
        }
        RoulettePanelStyle.fill(g, px + 14, contentTop() - 10, pw - 28, 1, 0x1AFFFFFF);

        g.drawString(font, Component.translatable("gui.sparkle_morpher.classic_hud_layout.anchor"),
                px + 16, contentTop(), 0xFFB1B1B1, false);
        drawAnchorGrid(g, mouseX, mouseY, px + 16, contentTop() + 14);

        int fx = fieldX(), fw = fieldW();
        drawField(g, mouseX, mouseY, fx, fieldsTop(), fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_x"), xField, Integer.toString(offsetX));
        drawField(g, mouseX, mouseY, fx, fieldsTop() + rowGap(), fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_y"), yField, Integer.toString(offsetY));
        drawField(g, mouseX, mouseY, fx, fieldsTop() + rowGap() * 2, fw, Component.translatable("gui.sparkle_morpher.model_panel.setting.classic_hud_scale"), scaleField, scaleText());

        drawButton(g, mouseX, mouseY, resetX(), resetY(), resetW(), 22, Component.translatable("controls.reset"), false);
        drawButton(g, mouseX, mouseY, doneX(), resetY(), resetW(), 22, Component.translatable("gui.done"), true);
    }

    private void drawAnchorGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        PaperDollLayout.Anchor[] values = PaperDollLayout.Anchor.values();
        for (int i = 0; i < values.length; i++) {
            int col = i % 3, row = i / 3;
            int cx = x + col * (ANCHOR_CELL + ANCHOR_GAP);
            int cy = y + row * (ANCHOR_CELL + ANCHOR_GAP);
            boolean active = anchor == values[i];
            boolean hover = inside(mouseX, mouseY, cx, cy, ANCHOR_CELL);
            RoulettePanelStyle.roundedSurface(g, cx, cy, ANCHOR_CELL, ANCHOR_CELL, 4,
                    active ? 0x22E05252 : hover ? 0x14FFFFFF : 0x08FFFFFF,
                    active ? 0xB0E05252 : 0x22FFFFFF);
            RoulettePanelStyle.roundedFill(g, cx + 4 + col * 6, cy + 4 + row * 6, 4, 4, 2,
                    active ? 0xFFF4F4F4 : 0x99FFFFFF);
        }
    }

    private void drawField(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                           Component label, EditBox field, String value) {
        g.drawString(font, Component.literal(trim(label.getString(), w)), x, y - 10, 0xFFB1B1B1, false);
        boolean hover = inside(mouseX, mouseY, x, y, w, 20);
        RoulettePanelStyle.roundedSurface(g, x, y, w, 20, 4,
                field.isFocused() ? 0x18FFFFFF : hover ? 0x10FFFFFF : 0x0EFFFFFF,
                field.isFocused() ? 0x66E05252 : 0x24FFFFFF);
        if (!field.isFocused()) field.setValue(value);
    }

    private void drawButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
                            Component label, boolean primary) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        RoulettePanelStyle.roundedSurface(g, x, y, w, h, 5,
                primary ? hover ? 0xFFEA6068 : 0xFFDC5159 : hover ? 0x18FFFFFF : 0x0EFFFFFF, 0x24FFFFFF);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2, 0xFFF4F4F4);
    }

    private String trim(String value, int maxWidth) {
        return RoulettePanelStyle.trim(font, value, maxWidth);
    }

    private String scaleText() {
        return String.format(Locale.ROOT, "%.2f", playerScale / 40f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!panelVisible) {
            if (inside(mouseX, mouseY, panelX(), panelY(), pillSize())) {
                setPanelVisible(true);
                return true;
            }
        } else {
            if (inside(mouseX, mouseY, closeX(), closeY(), 22)) { onClose(); return true; }
            if (inside(mouseX, mouseY, collapseX(), collapseY(), 22)) { setPanelVisible(false); return true; }
            if (inside(mouseX, mouseY, resetX(), resetY(), resetW(), 22)) { resetLayout(); return true; }
            if (inside(mouseX, mouseY, doneX(), resetY(), resetW(), 22)) { onClose(); return true; }
            PaperDollLayout.Anchor[] values = PaperDollLayout.Anchor.values();
            for (int i = 0; i < values.length; i++) {
                int cx = panelX() + 16 + (i % 3) * (ANCHOR_CELL + ANCHOR_GAP);
                int cy = contentTop() + 14 + (i / 3) * (ANCHOR_CELL + ANCHOR_GAP);
                if (inside(mouseX, mouseY, cx, cy, ANCHOR_CELL)) {
                    setAnchor(values[i]);
                    return true;
                }
            }
            if (inside(mouseX, mouseY, panelX(), panelY(), panelW(), panelH())) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        PaperDollLayout.Position position = position();
        int right = position.x() + dollW(), bottom = position.y() + dollH();
        if (button == 0) {
            int[][] corners = { { position.x(), position.y() }, { right, position.y() },
                    { position.x(), bottom }, { right, bottom } };
            for (int[] corner : corners) {
                if (near(mouseX, mouseY, corner[0], corner[1])) {
                    fixedX = corner[0] == position.x() ? right : position.x();
                    fixedY = corner[1] == position.y() ? bottom : position.y();
                    dragMode = DragMode.SCALE;
                    return true;
                }
            }
        }
        boolean insideDoll = mouseX >= position.x() && mouseX <= right && mouseY >= position.y() && mouseY <= bottom;
        if (button == 0 && insideDoll) {
            dragMode = DragMode.MOVE;
            grabOffsetX = mouseX - position.x();
            grabOffsetY = mouseY - position.y();
            return true;
        }
        if (button == 1 && insideDoll) {
            dragMode = DragMode.YAW;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        switch (dragMode) {
            case MOVE -> {
                setPositionFromScreen(mouseX - grabOffsetX, mouseY - grabOffsetY);
                return true;
            }
            case SCALE -> {
                updateScaleFromCorner(mouseX, mouseY);
                return true;
            }
            case YAW -> {
                playerYaw += (float) dragX * 2.0f;
                return true;
            }
            default -> {
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
    }

    private void updateScaleFromCorner(double mouseX, double mouseY) {
        float desired = Math.min(Math.abs((float) mouseX - fixedX), Math.abs((float) mouseY - fixedY) * 0.5f);
        playerScale = Mth.clamp(desired, MIN_SCALE, MAX_SCALE);
        int w = dollW(), h = dollH();
        int x = mouseX < fixedX ? Math.round(fixedX) - w : Math.round(fixedX);
        int y = mouseY < fixedY ? Math.round(fixedY) - h : Math.round(fixedY);
        setPositionFromScreen(x, y);
    }

    private void setPositionFromScreen(double x, double y) {
        int w = dollW(), h = dollH();
        int px = Mth.clamp((int) Math.round(x), 0, Math.max(0, width - w));
        int py = Mth.clamp((int) Math.round(y), 0, Math.max(0, height - h));
        PaperDollLayout.Position offset = PaperDollLayout.toOffset(anchor, width, height, px, py, w, h);
        offsetX = clampOffsetX(offset.x());
        offsetY = clampOffsetY(offset.y());
    }

    private int clampOffsetX(int value) {
        int span = Math.max(0, width - dollW());
        if (anchor.ordinal() % 3 == 1) {
            int half = span / 2;
            return Mth.clamp(value, -half, half);
        }
        return Mth.clamp(value, 0, span);
    }

    private int clampOffsetY(int value) {
        int span = Math.max(0, height - dollH());
        if (anchor.ordinal() / 3 == 1) {
            int half = span / 2;
            return Mth.clamp(value, -half, half);
        }
        return Mth.clamp(value, 0, span);
    }

    private void clampOffsets() {
        offsetX = clampOffsetX(offsetX);
        offsetY = clampOffsetY(offsetY);
    }

    private void setAnchor(PaperDollLayout.Anchor next) {
        if (next == anchor) return;
        PaperDollLayout.Position current = position();
        anchor = next;
        PaperDollLayout.Position offset = PaperDollLayout.toOffset(anchor, width, height,
                current.x(), current.y(), dollW(), dollH());
        offsetX = clampOffsetX(offset.x());
        offsetY = clampOffsetY(offset.y());
        anchorSink.accept(anchor);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragMode = DragMode.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PaperDollLayout.Position position = position();
        int right = position.x() + dollW(), bottom = position.y() + dollH();
        if (mouseX >= position.x() && mouseX <= right && mouseY >= position.y() && mouseY <= bottom) {
            float centerX = (position.x() + right) * 0.5f;
            float centerY = (position.y() + bottom) * 0.5f;
            float step = Math.max(0.5f, playerScale * 0.04f);
            playerScale = Mth.clamp(playerScale + (float) scrollY * step, MIN_SCALE, MAX_SCALE);
            int w = dollW(), h = dollH();
            setPositionFromScreen(centerX - w * 0.5f, centerY - h * 0.5f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.toLowerCase(codePoint) == 'r' && Screen.hasAltDown()) {
            resetLayout();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 72 && Screen.hasAltDown()) {
            setPanelVisible(!panelVisible);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 收起面板后整个屏幕都是拖拽区，避免面板挡住屏幕边缘的 HUD 位置。 */
    private void setPanelVisible(boolean visible) {
        panelVisible = visible;
        for (EditBox field : new EditBox[] { xField, yField, scaleField }) {
            if (field != null) {
                field.setVisible(visible);
                if (!visible) {
                    field.setFocused(false);
                }
            }
        }
    }

    private void resetLayout() {
        anchor = PaperDollLayout.Anchor.TOP_LEFT;
        offsetX = 10;
        offsetY = 10;
        playerScale = 40.0f;
        playerYaw = 0.0f;
        anchorSink.accept(anchor);
        syncFields();
    }

    private void syncFields() {
        if (xField != null) xField.setValue(Integer.toString(offsetX));
        if (yField != null) yField.setValue(Integer.toString(offsetY));
        if (scaleField != null) scaleField.setValue(scaleText());
    }

    @Override
    public void onClose() {
        config.set(offsetX, offsetY, playerScale, playerYaw);
        config.save();
        anchorSink.accept(anchor);
        InputUtil.setScreen(this.parent);
    }

    private boolean inside(double px, double py, int x, int y, int size) {
        return RoulettePanelStyle.inside(px, py, x, y, size, size);
    }

    private boolean inside(double px, double py, int x, int y, int w, int h) {
        return RoulettePanelStyle.inside(px, py, x, y, w, h);
    }

    private boolean near(double px, double py, int x, int y) {
        return Math.abs(px - x) <= HANDLE_RADIUS && Math.abs(py - y) <= HANDLE_RADIUS;
    }

    private boolean insideDoll(double mouseX, double mouseY) {
        PaperDollLayout.Position position = position();
        return mouseX >= position.x() && mouseX <= position.x() + dollW()
                && mouseY >= position.y() && mouseY <= position.y() + dollH();
    }

    private enum DragMode {
        NONE,
        MOVE,
        SCALE,
        YAW
    }
}
