package com.micaftic.morpher.core.gui.components;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.ModernPlayerTextureScreen;
import com.micaftic.morpher.core.gui.OptionRow;

import java.util.ArrayList;
import java.util.List;

public final class TextureGrid extends OptionRow<Object> {
    private static final int TEX_BTN_W = 54;
    private static final int TEX_BTN_H = 102;
    private static final int TEX_GAP = 4;

    private final ModernPlayerTextureScreen owner;
    private final List<String> textureNames;
    private final PlayerPreviewEntity[] holders;

    public TextureGrid(ModernPlayerTextureScreen owner) {
        super(0, 0, 0, 0, null);
        this.owner = owner;
        this.textureNames = new ArrayList<>(owner.textureMap.size());
        for (int i = 0; i < owner.textureMap.size(); i++) {
            this.textureNames.add(owner.textureMap.getKeyAt(i));
        }
        this.holders = new PlayerPreviewEntity[textureNames.size()];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = new PlayerPreviewEntity();
            holders[i].resetModel();
            holders[i].getAnimationStateMachine().setCurrentAnimation("idle");
            holders[i].initModelWithTexture(owner.modelId, textureNames.get(i));
        }
    }

    private int cols() {
        return Math.max(1, (width + TEX_GAP) / (TEX_BTN_W + TEX_GAP));
    }

    private int rows() {
        return (textureNames.size() + cols() - 1) / cols();
    }

    @Override
    public void setWidth(int w) {
        super.setWidth(w);
        this.height = rows() * (TEX_BTN_H + TEX_GAP) - TEX_GAP;
    }

    public void collectBlurRegions(List<int[]> out, int rowScroll, int areaTop, int areaBottom) {
        int c = cols();
        int slotW = TEX_BTN_W + TEX_GAP;
        int slotH = TEX_BTN_H + TEX_GAP;
        for (int i = 0; i < textureNames.size(); i++) {
            int col = i % c;
            int row = i / c;
            int x = getX() + col * slotW;
            int y = getY() + row * slotH - rowScroll;
            int yBot = y + TEX_BTN_H;
            if (yBot <= areaTop || y >= areaBottom) continue;
            int top = Math.max(y, areaTop);
            int bot = Math.min(yBot, areaBottom);
            out.add(new int[]{x, top, TEX_BTN_W, bot - top});
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int c = cols();
        int slotW = TEX_BTN_W + TEX_GAP;
        int slotH = TEX_BTN_H + TEX_GAP;
        for (int i = 0; i < textureNames.size(); i++) {
            int col = i % c;
            int row = i / c;
            int x = getX() + col * slotW;
            int y = getY() + row * slotH;
            renderSlot(g, x, y, i, mouseX, mouseY, partialTick);
        }
    }

    private void renderSlot(GuiGraphics g, int x, int y, int idx, int mx, int my, float pt) {
        String name = textureNames.get(idx);
        PlayerPreviewEntity holder = holders[idx];
        String currentTex = currentTextureName();
        boolean selected = name.equals(currentTex);
        boolean hover = mx >= x && mx < x + TEX_BTN_W && my >= y && my < y + TEX_BTN_H;
        int bg = selected ? 0x90333333 : (hover ? 0x90171717 : 0x90000000);
        g.fill(x, y, x + TEX_BTN_W, y + TEX_BTN_H, bg);
        renderHolderPreview(x, y, holder, pt);
        Component label = Component.literal(ModelMetadataPresenter.getLocalizedModelString(owner.renderContext, "files.player.texture.%s".formatted(name), name));
        int textY = y + TEX_BTN_H - 12;
        int tw = Minecraft.getInstance().font.width(label);
        g.drawString(Minecraft.getInstance().font, label, x + (TEX_BTN_W - tw) / 2, textY, 0xFFFFFFFF, true);
        if (selected || hover) {
            int border = selected ? 0xFFFFFFFF : 0xFFAAAAAA;
            g.fill(x, y, x + TEX_BTN_W, y + 1, border);
            g.fill(x, y + TEX_BTN_H - 1, x + TEX_BTN_W, y + TEX_BTN_H, border);
            g.fill(x, y, x + 1, y + TEX_BTN_H, border);
            g.fill(x + TEX_BTN_W - 1, y, x + TEX_BTN_W, y + TEX_BTN_H, border);
        }
    }

    private String currentTextureName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return StringPool.EMPTY;
        return PlayerCapability.get(mc.player).map(PlayerCapability::getCurrentTextureName).orElse(StringPool.EMPTY);
    }

    private void renderHolderPreview(int x, int y, PlayerPreviewEntity holder, float pt) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int previewH = TEX_BTN_H - 20;
        int sx = (int) (x * scale);
        int sy = (int) (mc.getWindow().getHeight() - (y + previewH) * scale);
        int sw = (int) (TEX_BTN_W * scale);
        int sh = (int) (previewH * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
        ModelPreviewRenderer.renderLivingEntityPreview(x + TEX_BTN_W / 2.0f, y + TEX_BTN_H / 2.0f + 24.0f, 35.0f, pt, holder, RendererManager.getPlayerRenderer(), false, true);
        RenderSystem.disableScissor();
    }

    @Override
    protected void renderControl(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int c = cols();
        int slotW = TEX_BTN_W + TEX_GAP;
        int slotH = TEX_BTN_H + TEX_GAP;
        int col = (int) ((mouseX - getX()) / slotW);
        int row = (int) ((mouseY - getY()) / slotH);
        if (col < 0 || col >= c) return;
        int idx = row * c + col;
        if (idx < 0 || idx >= textureNames.size()) return;
        double localX = mouseX - getX() - col * slotW;
        double localY = mouseY - getY() - row * slotH;
        if (localX >= TEX_BTN_W || localY >= TEX_BTN_H) return;
        String name = textureNames.get(idx);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerCapability.get(mc.player).ifPresent(cap -> {
            cap.setCurrentTexture(name);
            ClientModelManager.rememberSelectedModel(owner.modelId, name);
            NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(owner.modelId, name));
        });
    }
}
