package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.fakeplayer.FakePlayerListCache;
import com.micaftic.morpher.fakeplayer.FakePlayerListEntry;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestFakePlayerListPacket;
import com.micaftic.morpher.network.message.C2SRequestFakePlayerModelPacket;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Standalone target picker and model manager for server-confirmed fake players. */
public final class FakePlayerModelScreen extends Screen {

    private UUID selectedUuid;
    private String selectedName = "";

    private FakePlayerModelScreen() {
        super(Component.literal("SPM Fake Player Manager"));
    }

    public static void open() {
        FakePlayerListCache.replace(List.of());
        InputUtil.setScreen(new FakePlayerModelScreen());
        NetworkHandler.sendToServer(new C2SRequestFakePlayerListPacket());
    }

    public static void openFromCrosshair() {
        open();
    }

    @Override
    protected void init() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0101418);
        int panelW = Math.min(540, this.width - 32);
        int panelH = Math.min(300, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        g.fill(x, y, x + panelW, y + panelH, 0xF02B3038);
        text(g, Component.literal("SPM 假人外观管理"), x + 18, y + 16, 0xFFFFFFFF);
        text(g, Component.literal("准星目标会自动预选，也可以直接从服务端列表选择。"),
                x + 18, y + 38, 0xFFB8C6D0);

        List<FakePlayerListEntry> entries = FakePlayerListCache.entries();
        int listY = y + 58;
        if (entries.isEmpty()) {
            text(g, Component.literal("正在获取可管理假人，或当前没有在线假人。"),
                    x + 18, listY + 12, 0xFFB8C6D0);
        } else {
            int maxRows = Math.min(entries.size(), Math.max(1, (panelH - 118) / 25));
            for (int i = 0; i < maxRows; i++) {
                FakePlayerListEntry entry = entries.get(i);
                int rowY = listY + i * 25;
                boolean selected = entry.uuid().equals(selectedUuid);
                boolean hover = mouseX >= x + 18 && mouseX < x + panelW - 18
                        && mouseY >= rowY && mouseY < rowY + 22;
                g.fill(x + 18, rowY, x + panelW - 18, rowY + 22,
                        selected ? 0xFF58636E : hover ? 0xFF434A52 : 0xFF343B42);
                text(g, Component.literal(entry.name()), x + 28, rowY + 6, 0xFFFFFFFF);
                text(g, Component.literal(entry.providerId()), x + panelW - 170, rowY + 6, 0xFFB8C6D0);
                if (selected) {
                    selectedName = entry.name();
                }
            }
        }

        int buttonY = y + panelH - 46;
        boolean canApply = entries.stream().anyMatch(entry -> entry.uuid().equals(selectedUuid));
        boolean hover = mouseX >= x + 18 && mouseX < x + panelW - 18
                && mouseY >= buttonY && mouseY < buttonY + 24;
        g.fill(x + 18, buttonY, x + panelW - 18, buttonY + 24,
                canApply ? (hover ? 0xFF687988 : 0xFF4B5A67) : 0xFF3A3E42);
        String label = canApply
                ? "选择模型 → " + (selectedName.isBlank() ? "已选目标" : selectedName)
                : "请选择一个假人";
        text(g, Component.literal(label), x + (panelW - this.font.width(label)) / 2,
                buttonY + 7, canApply ? 0xFFFFFFFF : 0xFF808890);
        text(g, Component.literal("ESC 返回"), x + 18, y + panelH - 16, 0xFF808890);
    }

    private void text(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        if (event.button() != 0) {
            return super.mouseClicked(event, flag);
        }
        int panelW = Math.min(540, this.width - 32);
        int panelH = Math.min(300, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int listY = y + 58;
        List<FakePlayerListEntry> entries = FakePlayerListCache.entries();
        int maxRows = Math.min(entries.size(), Math.max(1, (panelH - 118) / 25));
        for (int i = 0; i < maxRows; i++) {
            int rowY = listY + i * 25;
            if (event.x() >= x + 18 && event.x() < x + panelW - 18
                    && event.y() >= rowY && event.y() < rowY + 22) {
                FakePlayerListEntry entry = entries.get(i);
                selectedUuid = entry.uuid();
                selectedName = entry.name();
                return true;
            }
        }
        int buttonY = y + panelH - 46;
        if (entries.stream().anyMatch(entry -> entry.uuid().equals(selectedUuid))
                && event.x() >= x + 18 && event.x() < x + panelW - 18
                && event.y() >= buttonY && event.y() < buttonY + 24) {
            UUID target = selectedUuid;
            InputUtil.setScreen(new ModernPlayerModelScreen(this,
                    (modelId, textureId) -> NetworkHandler.sendToServer(
                            new C2SRequestFakePlayerModelPacket(target, modelId, textureId)),
                    "fake:" + target));
            return true;
        }
        return super.mouseClicked(event, flag);
    }
}
