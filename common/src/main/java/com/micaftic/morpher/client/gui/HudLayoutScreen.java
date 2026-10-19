package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.modernhud.ModernHudRenderer;
import com.micaftic.morpher.config.HudLayoutConfig;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import com.micaftic.morpher.core.gui.IndependentPanelChrome;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 通用 HUD 布局画布编辑器（26.x，GuiGraphicsExtractor 版）：拖拽移动、右下角蓝色把手/滚轮
 * 无级缩放、右键拖动旋转、Alt+R 重置。经典 HUD 与现代 HUD 各持有一份 {@link HudLayoutConfig}，
 * 本屏通过 {@link HudPreview} 回调预览对应渲染器，并把编辑结果写回自己的配置，互不干扰。
 *
 * <p>画布只有 2×scale 的方形 guide（经典 HUD framing），实际绘制以预览渲染器输出为准
 * （现代 HUD 按模型真实 AABB 自定尺寸）。
 */
public final class HudLayoutScreen extends Screen {
    private static final float MIN_SCALE = 8.0f;
    private static final float MAX_SCALE = 360.0f;
    private static final int HANDLE_RADIUS = 7;

    private final Screen parent;
    private final HudLayoutConfig config;
    private final HudPreview preview;
    private int playerX;
    private int playerY;
    private float playerScale;
    private float playerYaw;
    private DragMode dragMode = DragMode.NONE;
    private double grabOffsetX;
    private double grabOffsetY;
    private EditBox xField, yField, scaleField;
    private int scaleCorner;
    private float fixedX, fixedY;
    private IndependentPanelChrome chrome() { return IndependentPanelChrome.of(width, height); }
    private int canvasX() { return chrome().contentLeft(); }
    private int canvasY() { return chrome().bodyTop(); }
    private int canvasW() { return Math.round((chrome().contentRight() - canvasX()) * .73864f); }
    private int canvasH() { return Math.max(80, chrome().footerY() - canvasY() - 73); }
    private float canvasScale() { return Math.min(canvasW() / (float) width, canvasH() / (float) height); }
    private double logicalX(double x) { return (x - canvasX()) / canvasScale(); }
    private double logicalY(double y) { return (y - canvasY()) / canvasScale(); }

    /** 预览回调：把编辑中的（未保存）布局值渲染到画布；返回是否成功绘制。 */
    @FunctionalInterface
    public interface HudPreview {
        boolean render(GuiGraphicsExtractor graphics, LocalPlayer player, float partialTick,
                       int screenWidth, int screenHeight, float x, float y, float scale, float yaw);
    }

    public HudLayoutScreen(Screen parent, Component title, HudLayoutConfig config, HudPreview preview) {
        super(title);
        this.parent = parent;
        this.config = config;
        this.preview = preview;
        this.playerX = config.getX();
        this.playerY = config.getY();
        this.playerScale = config.getScale();
        this.playerYaw = config.getYaw();
    }

    /** 经典 HUD 预览：经经典路径绘制，直接落回画布坐标。 */
    public static HudPreview classicPreview() {
        return (graphics, player, partialTick, w, h, x, y, scale, yaw) -> {
            ModelPreviewRenderer.renderPlayerOverlay(graphics, player, x, y, scale, yaw, -500, partialTick, false);
            return true;
        };
    }

    /** 现代 HUD 预览：经现代 HUD 独立管线（FBO + 合成）绘制。 */
    public static HudPreview modernPreview() {
        return ModernHudRenderer::renderAt;
    }

    @Override
    protected void init() {
        IndependentPanelChrome c = chrome();
        int rightX = canvasX() + canvasW() + 26;
        int fieldW = Math.max(46, Math.round((c.contentRight() - rightX) * .29327f));
        xField = valueField(c.contentRight() - fieldW, canvasY() + 7, fieldW, "位置 X", Integer.toString(playerX), value -> {
            try { playerX = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        });
        yField = valueField(c.contentRight() - fieldW, canvasY() + 42, fieldW, "位置 Y", Integer.toString(playerY), value -> {
            try { playerY = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        });
        scaleField = valueField(c.contentRight() - fieldW, canvasY() + 77, fieldW, "缩放", String.format(java.util.Locale.ROOT, "%.2f", playerScale / 40f), value -> {
            try { playerScale = Mth.clamp(Float.parseFloat(value) * 40f, MIN_SCALE, MAX_SCALE); } catch (NumberFormatException ignored) { }
        });
    }

    private EditBox valueField(int x, int y, int w, String name, String value, java.util.function.Consumer<String> change) {
        EditBox field = new EditBox(font, x + 5, y + 7, w - 10, 14, Component.literal(name));
        field.setBordered(false);
        field.setValue(value);
        field.setResponder(change);
        addRenderableWidget(field);
        return field;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        IndependentPanelChrome c = chrome();
        c.frame(graphics, font, Component.literal("HUD 布局"), Component.literal("拖动移动，拖动边角缩放；预览使用中性辅助线。"), mouseX, mouseY);
        int cx = canvasX(), cy = canvasY(), cw = canvasW(), ch = canvasH();
        RoulettePanelStyle.roundedSurface(graphics, cx, cy, cw, ch, 4, 0x18101010, 0x1AFFFFFF);
        for (int i = 1; i < 4; i++) {
            RoulettePanelStyle.fill(graphics, cx + i * cw / 4, cy, 1, ch, 0x10FFFFFF);
            RoulettePanelStyle.fill(graphics, cx, cy + i * ch / 4, cw, 1, 0x10FFFFFF);
        }
        graphics.enableScissor(cx, cy, cx + cw, cy + ch);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(canvasScale(), canvasScale());
        int right = Math.round(playerX + playerScale), bottom = Math.round(playerY + playerScale * 2f);
        RoulettePanelStyle.border(graphics, playerX, playerY, right - playerX, bottom - playerY, 0xAADC5159);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) preview.render(graphics, minecraft.player, partialTick, width, height, playerX, playerY, playerScale, playerYaw);
        for (int hx : new int[] { playerX, right }) for (int hy : new int[] { playerY, bottom })
            RoulettePanelStyle.fill(graphics, hx - 3, hy - 3, 6, 6, 0xFFF4F4F4);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        int sx = cx + cw + 26, sw = c.contentRight() - sx;
        EditBox[] fields = { xField, yField, scaleField };
        String[] labels = { "位置 X", "位置 Y", "缩放" };
        String[] values = { Integer.toString(playerX), Integer.toString(playerY), String.format(java.util.Locale.ROOT, "%.2f", playerScale / 40f) };
        for (int i = 0; i < fields.length; i++) {
            EditBox field = fields[i];
            int y = cy + 7 + i * 35;
            graphics.text(font, labels[i], sx + 10, y + 6, 0xFFF4F4F4, false);
            RoulettePanelStyle.roundedSurface(graphics, field.getX() - 5, y, field.getWidth() + 10, 22, 3, 0x0EFFFFFF, 0x24FFFFFF);
            RoulettePanelStyle.fill(graphics, sx + 10, y + 27, Math.max(1, sw - 10), 1, 0x12FFFFFF);
            if (!field.isFocused()) field.setValue(values[i]);
        }
        IndependentPanelChrome.button(graphics, font, mouseX, mouseY, sx, cy + 144, sw, Component.translatable("controls.reset"), false);
        RoulettePanelStyle.fill(graphics, c.contentLeft(), c.footerY() - 10, c.contentRight() - c.contentLeft(), 1, 0x1AFFFFFF);
        IndependentPanelChrome.button(graphics, font, mouseX, mouseY, c.contentRight() - 100, c.footerY(), 100, Component.translatable("gui.done"), true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        IndependentPanelChrome c = chrome();
        if (c.isBackOrClose(event.x(), event.y()) || IndependentPanelChrome.inside(event.x(), event.y(), c.contentRight() - 100, c.footerY(), 100, 22)) { onClose(); return true; }
        int sx = canvasX() + canvasW() + 26;
        if (IndependentPanelChrome.inside(event.x(), event.y(), sx, canvasY() + 144, c.contentRight() - sx, 22)) { resetLayout(); return true; }
        if (!IndependentPanelChrome.inside(event.x(), event.y(), canvasX(), canvasY(), canvasW(), canvasH())) return super.mouseClicked(event, flag);
        double mouseX = logicalX(event.x()), mouseY = logicalY(event.y());
        int right = Math.round(playerX + playerScale), bottom = Math.round(playerY + playerScale * 2f);
        double handle = HANDLE_RADIUS / canvasScale();
        if (com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button()) == 0) {
            int corner = 0;
            for (int hx : new int[] { playerX, right }) for (int hy : new int[] { playerY, bottom }) {
                if (Math.abs(mouseX - hx) <= handle && Math.abs(mouseY - hy) <= handle) {
                    scaleCorner = corner;
                    fixedX = hx == playerX ? right : playerX;
                    fixedY = hy == playerY ? bottom : playerY;
                    dragMode = DragMode.SCALE;
                    return true;
                }
                corner++;
            }
        }
        boolean inside = mouseX >= playerX && mouseX <= right && mouseY >= playerY && mouseY <= bottom;
        if (com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button()) == 0 && inside) {
            dragMode = DragMode.MOVE;
            grabOffsetX = mouseX - playerX;
            grabOffsetY = mouseY - playerY;
            return true;
        }
        if (com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button()) == 1 && inside) { dragMode = DragMode.YAW; return true; }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        switch (this.dragMode) {
            case MOVE -> {
                this.playerX = Mth.clamp((int) Math.round(logicalX(event.x()) - this.grabOffsetX), 0, Math.max(0, this.width - 1));
                this.playerY = Mth.clamp((int) Math.round(logicalY(event.y()) - this.grabOffsetY), 0, Math.max(0, this.height - 1));
                return true;
            }
            case SCALE -> {
                float widthScale = Math.abs((float) logicalX(event.x()) - fixedX);
                float heightScale = Math.abs((float) logicalY(event.y()) - fixedY) * .5f;
                playerScale = Mth.clamp(Math.min(widthScale, heightScale), MIN_SCALE, MAX_SCALE);
                playerX = Math.round(scaleCorner < 2 ? fixedX - playerScale : fixedX);
                playerY = Math.round(scaleCorner % 2 == 0 ? fixedY - playerScale * 2 : fixedY);
                return true;
            }
            case YAW -> {
                this.playerYaw += (float) dragX * 2.0f;
                return true;
            }
            default -> {
                return super.mouseDragged(event, dragX, dragY);
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragMode = DragMode.NONE;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = logicalX(mouseX);
        mouseY = logicalY(mouseY);
        int right = Math.round(this.playerX + this.playerScale);
        int bottom = Math.round(this.playerY + this.playerScale * 2.0f);
        if (mouseX >= this.playerX && mouseX <= right && mouseY >= this.playerY && mouseY <= bottom) {
            float step = Math.max(0.5f, this.playerScale * 0.04f);
            this.playerScale = Mth.clamp(this.playerScale + (float) scrollY * step, MIN_SCALE, MAX_SCALE);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Character.toLowerCase(event.codepoint()) == 'r' && Minecraft.getInstance().hasAltDown()) {
            resetLayout();
            return true;
        }
        return super.charTyped(event);
    }

    private void resetLayout() {
        this.playerX = 10;
        this.playerY = 10;
        this.playerScale = 40.0f;
        this.playerYaw = 0.0f;
    }

    @Override
    public void onClose() {
        this.config.set(this.playerX, this.playerY, this.playerScale, this.playerYaw);
        this.config.save();
        InputUtil.setScreen(this.parent);
    }

    private enum DragMode {
        NONE,
        MOVE,
        SCALE,
        YAW
    }
}
