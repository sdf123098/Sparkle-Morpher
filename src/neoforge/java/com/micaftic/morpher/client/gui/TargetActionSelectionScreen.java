package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.fakeplayer.FakePlayerListCache;
import com.micaftic.morpher.fakeplayer.FakePlayerListEntry;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import com.micaftic.morpher.core.gui.IndependentPanelChrome;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Unified target picker for fake-player and maid action wheels.
 *
 * <p>The picker deliberately does not inspect Minecraft.hitResult. It uses the
 * Cloud-discovered fake-player snapshot plus loaded nearby maid entities,
 * then hands the selected entity to the existing unified action roulette.</p>
 */
public final class TargetActionSelectionScreen extends Screen {
    private EditBox search;
    private UUID selected;
    private String failure = "";
    private int pageSize() { return Math.max(1, (IndependentPanelChrome.of(width, height).footerY() - rowTop() - 18) / rowHeight()); }
    private int rowHeight() { return height < 360 ? 34 : 48; }
    private int rowTop() { return IndependentPanelChrome.of(width, height).bodyTop() + 60; }
    private List<TargetRow> visibleTargets() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        return targets.stream().filter(row -> query.isEmpty() || (row.label() + " " + row.type() + " " + row.model()).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
    }
    private final List<TargetRow> targets = new ArrayList<>();
    private int page;
    private int refreshTicks;
    private int lastFingerprint = Integer.MIN_VALUE;

    private TargetActionSelectionScreen() {
        super(Component.literal("选择动作目标"));
    }

    public static void open() {
        InputUtil.setScreen(new TargetActionSelectionScreen());
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        IndependentPanelChrome chrome = IndependentPanelChrome.of(width, height);
        search = new EditBox(font, chrome.contentLeft() + 7, chrome.bodyTop() + 7, Math.min(286, chrome.contentRight() - chrome.contentLeft() - 50), 14, Component.literal("搜索目标"));
        search.setBordered(false);
        search.setHint(Component.literal("搜索目标…"));
        search.setValue(query);
        search.setResponder(value -> { page = 0; rebuildTargetWidgets(); });
        addRenderableWidget(search);
        rebuildTargets();
        rebuildTargetWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            int fingerprint = fingerprint();
            if (fingerprint != lastFingerprint) {
                rebuildTargets();
                rebuildTargetWidgets();
            }
        }
    }

    private int fingerprint() {
        int result = FakePlayerListCache.entries().hashCode();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            for (Player player : minecraft.level.players()) {
                result = 31 * result + player.getUUID().hashCode();
                result = 31 * result + player.getDisplayName().getString().hashCode();
                result = 31 * result + PlayerCapability.get(player)
                        .map(cap -> java.util.Objects.hash(cap.getModelId(), cap.getModelAssembly())).orElse(0);
            }
            result = 31 * result + minecraft.level.getEntitiesOfClass(
                    LivingEntity.class,
                    minecraft.player.getBoundingBox().inflate(64.0),
                    entity -> TouhouMaidCompat.isMaidEntity(entity)).size();
        }
        return result;
    }

    private void rebuildTargets() {
        targets.clear();
        Set<UUID> seen = new HashSet<>();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player local = minecraft.player;
        if (level != null && local != null) {
            Set<UUID> fakeIds = new HashSet<>();
            for (FakePlayerListEntry entry : FakePlayerListCache.entries()) fakeIds.add(entry.uuid());
            for (Player player : level.players()) {
                PlayerCapability.get(player).ifPresent(cap -> {
                    var assembly = cap.getModelAssembly();
                    if (assembly != null && assembly.getPresentationCapabilities().playerActionControlsAvailable() && seen.add(player.getUUID()))
                        targets.add(new TargetRow(player.getUUID(), player.getDisplayName().getString(), true,
                                fakeIds.contains(player.getUUID()) ? "假人" : "玩家", assembly.getDisplayName(cap.getModelId())));
                });
            }
            for (LivingEntity maid : level.getEntitiesOfClass(LivingEntity.class, local.getBoundingBox().inflate(64.0), entity -> TouhouMaidCompat.isMaidEntity(entity))) {
                MaidCapability.get(maid).ifPresent(cap -> {
                    var assembly = cap.getModelAssembly();
                    if (assembly != null && assembly.getPresentationCapabilities().playerActionControlsAvailable() && seen.add(maid.getUUID()))
                        targets.add(new TargetRow(maid.getUUID(), maid.getDisplayName().getString(), false, "女仆", assembly.getDisplayName(cap.getModelId())));
                });
            }
        }
        targets.sort(Comparator.comparing(TargetRow::label, String.CASE_INSENSITIVE_ORDER));
        rebuildTargetWidgets();
        lastFingerprint = fingerprint();
    }

    private void rebuildTargetWidgets() {
        List<TargetRow> rows = visibleTargets();
        page = Math.min(page, Math.max(0, (rows.size() - 1) / pageSize()));
        if (selected == null || rows.stream().noneMatch(row -> row.uuid().equals(selected)))
            selected = rows.isEmpty() ? null : rows.get(0).uuid();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        IndependentPanelChrome c = IndependentPanelChrome.of(width, height);
        c.frame(g, font, title, Component.literal("选择已加载且有模型的玩家、假人或其他目标"), mx, my);
        int x = c.contentLeft(), right = c.contentRight(), w = right - x;
        RoulettePanelStyle.roundedSurface(g, x, c.bodyTop(), Math.min(300, w - 30), 22, 3, 0x0BFFFFFF, 0x24FFFFFF);
        IndependentPanelChrome.icon(g, mx, my, right - 22, c.bodyTop(), RoulettePanelStyle.Glyph.RELOAD);
        RoulettePanelStyle.roundedSurface(g, x, rowTop() - 24, w, 23, 3, 0x0BFFFFFF, 0);
        int typeX = x + Math.round(w * .33f), modelX = x + Math.round(w * .55f);
        g.text(font, "目标", x + 10, rowTop() - 16, 0xFFB1B1B1, false);
        g.text(font, "类型", typeX, rowTop() - 16, 0xFFB1B1B1, false);
        g.text(font, "当前模型", modelX, rowTop() - 16, 0xFFB1B1B1, false);
        List<TargetRow> rows = visibleTargets();
        for (int i = page * pageSize(), slot = 0; i < rows.size() && slot < pageSize(); i++, slot++) {
            TargetRow row = rows.get(i);
            int y = rowTop() + slot * rowHeight();
            boolean active = row.uuid().equals(selected);
            if (active || IndependentPanelChrome.inside(mx, my, x, y, w, rowHeight() - 2))
                RoulettePanelStyle.roundedSurface(g, x, y, w, rowHeight() - 2, 3, active ? 0x14FFFFFF : 0x0BFFFFFF, 0);
            if (active) RoulettePanelStyle.fill(g, x, y + 4, 2, rowHeight() - 10, 0xFFDC5159);
            g.text(font, RoulettePanelStyle.trim(font, row.label(), typeX - x - 20), x + 10, y + rowHeight() / 2 - 4, 0xFFF4F4F4, false);
            g.text(font, row.type(), typeX, y + rowHeight() / 2 - 4, 0xFFB1B1B1, false);
            g.text(font, RoulettePanelStyle.trim(font, row.model(), right - modelX - 10), modelX, y + rowHeight() / 2 - 4, 0xFFB1B1B1, false);
            RoulettePanelStyle.fill(g, x + 8, y + rowHeight() - 1, w - 16, 1, 0x0FFFFFFF);
        }
        if (rows.isEmpty()) g.text(font, "没有匹配的已加载目标", x + 10, rowTop() + 20, 0xFFB1B1B1, false);
        if (!failure.isBlank()) g.text(font, RoulettePanelStyle.trim(font, failure, w - 130), x, c.footerY() + 7, 0xFFDC5159, false);
        if (rows.size() > pageSize()) {
            IndependentPanelChrome.icon(g, mx, my, x, c.footerY() - 30, RoulettePanelStyle.Glyph.CANCEL);
            IndependentPanelChrome.icon(g, mx, my, x + 80, c.footerY() - 30, RoulettePanelStyle.Glyph.APPLY);
            g.text(font, (page + 1) + " / " + Math.max(1, (rows.size() + pageSize() - 1) / pageSize()), x + 30, c.footerY() - 23, 0xFFB1B1B1, false);
        }
        IndependentPanelChrome.button(g, font, mx, my, right - 100, c.footerY(), 100, Component.literal("打开动作轮盘"), true);
        super.extractRenderState(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        IndependentPanelChrome c = IndependentPanelChrome.of(width, height);
        if (c.isBackOrClose(event.x(), event.y())) { onClose(); return true; }
        if (IndependentPanelChrome.inside(event.x(), event.y(), c.contentRight() - 22, c.bodyTop(), 22, 22)) { rebuildTargets(); return true; }
        if (IndependentPanelChrome.inside(event.x(), event.y(), c.contentRight() - 100, c.footerY(), 100, 22)) { openSelected(); return true; }
        List<TargetRow> rows = visibleTargets();
        if (IndependentPanelChrome.inside(event.x(), event.y(), c.contentLeft(), rowTop(), c.contentRight() - c.contentLeft(), pageSize() * rowHeight())) {
            int index = page * pageSize() + (int) ((event.y() - rowTop()) / rowHeight());
            if (index < rows.size()) selected = rows.get(index).uuid();
            return true;
        }
        if (rows.size() > pageSize() && IndependentPanelChrome.inside(event.x(), event.y(), c.contentLeft(), c.footerY() - 30, 102, 22)) {
            page = Math.max(0, Math.min((rows.size() - 1) / pageSize(), page + (event.x() < c.contentLeft() + 50 ? -1 : 1))); return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        var c=IndependentPanelChrome.of(width,height);
        if(sy!=0&&IndependentPanelChrome.inside(mx,my,c.contentLeft(),rowTop(),c.contentRight()-c.contentLeft(),Math.max(1,c.footerY()-rowTop()))){page=CloudScreenPagination.range(visibleTargets().size(),pageSize(),page+(sy>0?-1:1)).page();return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }

    private void openSelected() { visibleTargets().stream().filter(row -> row.uuid().equals(selected)).findFirst().ifPresent(this::openActionWheel); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 && (search == null || !search.isFocused())) { openSelected(); return true; }
        return super.keyPressed(event);
    }

    private void openActionWheel(TargetRow row) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity entity = resolve(level, row);
        if (entity == null) {
            fail("目标尚未加载到客户端，无法打开动作轮盘。");
            return;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            PlayerCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail("假人的 YSM 模型能力尚未同步。"));
        } else {
            MaidCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail("女仆的 YSM 模型能力尚未就绪。"));
        }
    }

    private static Entity resolve(ClientLevel level, TargetRow row) {
        if (level == null) {
            return null;
        }
        var playerTarget=level.getPlayerByUUID(row.uuid());
        if(playerTarget!=null)return playerTarget;
        Player local = Minecraft.getInstance().player;
        if (local == null) {
            return null;
        }
        List<LivingEntity> maids = level.getEntitiesOfClass(
                LivingEntity.class,
                local.getBoundingBox().inflate(96.0),
                entity -> row.uuid().equals(entity.getUUID()) && TouhouMaidCompat.isMaidEntity(entity));
        return maids.isEmpty() ? null : maids.get(0);
    }

    private void openRoulette(String modelId, com.micaftic.morpher.client.model.ModelAssembly assembly,
                              com.micaftic.morpher.geckolib3.core.AnimatableEntity<?> target) {
        if (assembly == null || !assembly.getPresentationCapabilities().playerActionControlsAvailable()
                || assembly.getModelData() == null
                || assembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
            fail("目标模型没有可用的额外动作。");
            return;
        }
        InputUtil.setScreen(new UnifiedRouletteScreen(modelId, assembly, target));
    }

    private void fail(String message) {
        failure = message;
    }

    private record TargetRow(UUID uuid, String label, boolean fakePlayer, String type, String model) {
    }
}
