package com.micaftic.morpher.client.gui;
import com.micaftic.morpher.fakeplayer.*;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.*;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Full server-backed fake-player list with local search, filtering, sorting and virtual scrolling. */
public final class FakePlayerManagerScreen extends Screen {
    private static final int ROW = 25;
    private UUID selectedUuid;
    private String selectedName = "";
    private String query = "";
    private int offset;
    private int filter;
    private boolean modelSort;
    private boolean searchFocused;

    private FakePlayerManagerScreen() { super(Component.literal("SPM Fake Player Manager")); }

    public static void open() {
        FakePlayerListCache.replace(List.of());
        InputUtil.setScreen(new FakePlayerManagerScreen());
        NetworkHandler.sendToServer(new C2SRequestFakePlayerListPacket());
    }

    @Override protected void init() {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0101418);
        int pw = Math.min(720, width - 28), ph = Math.min(430, height - 20);
        int x = (width - pw) / 2, y = (height - ph) / 2;
        g.fill(x, y, x + pw, y + ph, 0xF02B3038);
        draw(g, "SPM 假人管理器", x + 18, y + 14, 0xFFFFFFFF);
        draw(g, "仅显示服务端确认的可管理假人；女仆和普通玩家不会进入此列表。",
                x + 18, y + 31, 0xFFB8C6D0);
        int sy = y + 48;
        g.fill(x + 18, sy, x + pw - 18, sy + 22, searchFocused ? 0xFF56636D : 0xFF343B42);
        draw(g, query.isBlank() ? "搜索名称、显示名称、UUID 后缀、模型或提供者…" : "搜索: " + query,
                x + 26, sy + 6, query.isBlank() ? 0xFF808890 : 0xFFFFFFFF);
        int fy = sy + 28;
        String[] fs = {"全部", "在线", "有模型", "有维度"};
        for (int i = 0; i < fs.length; i++) {
            int bx = x + 18 + i * 72;
            g.fill(bx, fy, bx + 66, fy + 18, filter == i ? 0xFF58636E : 0xFF343B42);
            draw(g, fs[i], bx + 15, fy + 5, 0xFFEDE1CC);
        }
        int sx = x + pw - 150;
        g.fill(sx, fy, x + pw - 18, fy + 18, 0xFF343B42);
        draw(g, modelSort ? "排序：模型" : "排序：名称", sx + 12, fy + 5, 0xFFEDE1CC);

        List<FakePlayerListEntry> rows = rows();
        int ly = fy + 28, by = y + ph - 42;
        int count = Math.max(1, (by - 10 - ly) / ROW);
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - count)));
        if (rows.isEmpty()) draw(g, FakePlayerListCache.entries().isEmpty()
                ? "正在获取服务端快照，或当前没有在线假人。" : "没有匹配的假人。清空搜索或调整筛选。",
                x + 26, ly + 12, 0xFFB8C6D0);
        for (int i = 0; i < count && offset + i < rows.size(); i++) {
            FakePlayerListEntry e = rows.get(offset + i);
            int ry = ly + i * ROW;
            boolean selected = e.uuid().equals(selectedUuid);
            boolean hover = mouseX >= x + 18 && mouseX < x + pw - 18 && mouseY >= ry && mouseY < ry + ROW - 2;
            g.fill(x + 18, ry, x + pw - 18, ry + ROW - 2,
                    selected ? 0xFF58636E : hover ? 0xFF434A52 : 0xFF343B42);
            draw(g, trim(e.displayName().isBlank() ? e.name() : e.displayName(), 42),
                    x + 28, ry + 4, 0xFFFFFFFF);
            draw(g, trim(e.name() + " · " + e.providerId() + " · "
                    + (e.modelId().isBlank() ? "未设置模型" : e.modelId()), 82),
                    x + 28, ry + 14, 0xFFB8C6D0);
            draw(g, trim(e.dimensionId(), 22), x + pw - 145, ry + 8, 0xFF9A9A9A);
        }

        boolean canApply = rows.stream().anyMatch(e -> e.uuid().equals(selectedUuid));
        boolean hover = mouseX >= x + 18 && mouseX < x + pw - 18 && mouseY >= by && mouseY < by + 24;
        g.fill(x + 18, by, x + pw - 18, by + 24,
                canApply ? (hover ? 0xFF687988 : 0xFF4B5A67) : 0xFF3A3E42);
        String button = canApply ? "选择模型 → " + selectedName : "请选择一个假人";
        draw(g, button, x + (pw - font.width(button)) / 2, by + 7,
                canApply ? 0xFFFFFFFF : 0xFF808890);
        draw(g, "滚轮浏览 · 点击搜索框输入 · ESC 返回", x + 18, y + ph - 13, 0xFF808890);
    }

    private List<FakePlayerListEntry> rows() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        return FakePlayerListCache.entries().stream()
                .filter(e -> filter != 1 || e.online())
                .filter(e -> filter != 2 || !e.modelId().isBlank())
                .filter(e -> filter != 3 || !e.dimensionId().isBlank())
                .filter(e -> q.isBlank() || match(e.name(), q) || match(e.displayName(), q)
                        || match(e.uuid().toString().replace("-", "").substring(20), q)
                        || match(e.modelId(), q) || match(e.providerId(), q))
                .sorted(modelSort ? Comparator.comparing(FakePlayerListEntry::modelId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FakePlayerListEntry::name, String.CASE_INSENSITIVE_ORDER)
                        : Comparator.comparing(FakePlayerListEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean match(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
    private void draw(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, Component.literal(value), x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int pw = Math.min(720, width - 28), ph = Math.min(430, height - 20);
        int x = (width - pw) / 2, y = (height - ph) / 2, sy = y + 48, fy = sy + 28;
        if (mx >= x + 18 && mx < x + pw - 18 && my >= sy && my < sy + 22) {
            searchFocused = true; return true;
        }
        for (int i = 0; i < 4; i++) {
            int bx = x + 18 + i * 72;
            if (mx >= bx && mx < bx + 66 && my >= fy && my < fy + 18) {
                filter = i; offset = 0; return true;
            }
        }
        int sx = x + pw - 150;
        if (mx >= sx && mx < x + pw - 18 && my >= fy && my < fy + 18) {
            modelSort = !modelSort; offset = 0; return true;
        }
        List<FakePlayerListEntry> rows = rows();
        int ly = fy + 28, by = y + ph - 42, count = Math.max(1, (by - 10 - ly) / ROW);
        for (int i = 0; i < count && offset + i < rows.size(); i++) {
            int ry = ly + i * ROW;
            if (mx >= x + 18 && mx < x + pw - 18 && my >= ry && my < ry + ROW - 2) {
                FakePlayerListEntry e = rows.get(offset + i);
                selectedUuid = e.uuid();
                selectedName = e.displayName().isBlank() ? e.name() : e.displayName();
                return true;
            }
        }
        if (rows.stream().anyMatch(e -> e.uuid().equals(selectedUuid))
                && mx >= x + 18 && mx < x + pw - 18 && my >= by && my < by + 24) {
            UUID target = selectedUuid;
            InputUtil.setScreen(new ModernPlayerModelScreen(
                    (modelId, textureId) -> NetworkHandler.sendToServer(
                            new C2SRequestFakePlayerModelPacket(target, modelId, textureId)),
                    "fake:" + target));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) { offset -= (int) Math.signum(sy); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && !Character.isISOControl(codePoint) && query.length() < 64) {
            query += codePoint; offset = 0; return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused && keyCode == 259) {
            if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            offset = 0; return true;
        }
        if (keyCode == 257 || keyCode == 335) { searchFocused = false; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
