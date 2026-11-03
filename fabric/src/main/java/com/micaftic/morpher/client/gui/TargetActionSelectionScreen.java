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
import net.minecraft.client.gui.GuiGraphics;
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
    private int pageSize() { return Math.max(1, (IndependentPanelChrome.of(width, height).footerY() - rowTop() - 36) / rowHeight()); }
    private int rowHeight() { return height < 240 ? 26 : height < 360 ? 34 : 48; }
    private int rowTop() { return IndependentPanelChrome.of(width, height).bodyTop() + 48; }
    private List<TargetRow> visibleTargets() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        return targets.stream().filter(row -> query.isEmpty() || (row.label() + " " + row.type() + " " + row.model()).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
    }
    private final List<TargetRow> targets = new ArrayList<>();
    private int page;
    private int refreshTicks;
    private int lastFingerprint = Integer.MIN_VALUE;

    private TargetActionSelectionScreen() {
        super(Component.translatable("gui.sparkle_morpher.target_action.title"));
    }

    public static void open() {
        InputUtil.setScreen(new TargetActionSelectionScreen());
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        clearWidgets();
        IndependentPanelChrome chrome = IndependentPanelChrome.of(width, height);
        search = new EditBox(font, chrome.contentLeft() + 7, chrome.bodyTop() + 7, Math.min(286, chrome.contentRight() - chrome.contentLeft() - 50), 14, Component.translatable("gui.sparkle_morpher.target_action.search"));
        search.setBordered(false);
        search.setHint(Component.translatable("gui.sparkle_morpher.target_action.search_hint"));
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
                                Component.translatable(fakeIds.contains(player.getUUID()) ? "gui.sparkle_morpher.target_action.fake_player" : "gui.sparkle_morpher.target_action.player").getString(), assembly.getDisplayName(cap.getModelId())));
                });
            }
            for (LivingEntity maid : level.getEntitiesOfClass(LivingEntity.class, local.getBoundingBox().inflate(64.0), entity -> TouhouMaidCompat.isMaidEntity(entity))) {
                MaidCapability.get(maid).ifPresent(cap -> {
                    var assembly = cap.getModelAssembly();
                    if (assembly != null && assembly.getPresentationCapabilities().playerActionControlsAvailable() && seen.add(maid.getUUID()))
                        targets.add(new TargetRow(maid.getUUID(), maid.getDisplayName().getString(), false, Component.translatable("gui.sparkle_morpher.target_action.maid").getString(), assembly.getDisplayName(cap.getModelId())));
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
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        IndependentPanelChrome c = IndependentPanelChrome.of(width, height);
        c.frame(g, font, title, Component.translatable("gui.sparkle_morpher.target_action.description"), mx, my);
        int x = c.contentLeft(), right = c.contentRight(), w = right - x;
        RoulettePanelStyle.roundedSurface(g, x, c.bodyTop(), Math.min(300, w - 30), 22, 3, 0x0BFFFFFF, 0x24FFFFFF);
        IndependentPanelChrome.icon(g, mx, my, right - 22, c.bodyTop(), RoulettePanelStyle.Glyph.RELOAD);
        RoulettePanelStyle.roundedSurface(g, x, rowTop() - 24, w, 23, 3, 0x0BFFFFFF, 0);
        int typeX = x + Math.round(w * .33f), modelX = x + Math.round(w * .55f);
        g.drawString(font, Component.translatable("gui.sparkle_morpher.target_action.column.target").getString(), x + 10, rowTop() - 16, 0xFFB1B1B1, false);
        g.drawString(font, Component.translatable("gui.sparkle_morpher.target_action.column.type").getString(), typeX, rowTop() - 16, 0xFFB1B1B1, false);
        g.drawString(font, Component.translatable("gui.sparkle_morpher.target_action.column.model").getString(), modelX, rowTop() - 16, 0xFFB1B1B1, false);
        List<TargetRow> rows = visibleTargets();
        for (int i = page * pageSize(), slot = 0; i < rows.size() && slot < pageSize(); i++, slot++) {
            TargetRow row = rows.get(i);
            int y = rowTop() + slot * rowHeight();
            boolean active = row.uuid().equals(selected);
            if (active || IndependentPanelChrome.inside(mx, my, x, y, w, rowHeight() - 2))
                RoulettePanelStyle.roundedSurface(g, x, y, w, rowHeight() - 2, 3, active ? 0x14FFFFFF : 0x0BFFFFFF, 0);
            if (active) RoulettePanelStyle.fill(g, x, y + 4, 2, rowHeight() - 10, 0xFFDC5159);
            g.drawString(font, RoulettePanelStyle.trim(font, row.label(), typeX - x - 20), x + 10, y + rowHeight() / 2 - 4, 0xFFF4F4F4, false);
            g.drawString(font, row.type(), typeX, y + rowHeight() / 2 - 4, 0xFFB1B1B1, false);
            g.drawString(font, RoulettePanelStyle.trim(font, row.model(), right - modelX - 10), modelX, y + rowHeight() / 2 - 4, 0xFFB1B1B1, false);
            RoulettePanelStyle.fill(g, x + 8, y + rowHeight() - 1, w - 16, 1, 0x0FFFFFFF);
        }
        if (rows.isEmpty()) g.drawString(font, Component.translatable("gui.sparkle_morpher.target_action.empty").getString(), x + 10, rowTop() + 20, 0xFFB1B1B1, false);
        if (!failure.isBlank()) g.drawString(font, RoulettePanelStyle.trim(font, failure, w - 130), x, c.footerY() + 7, 0xFFDC5159, false);
        if (rows.size() > pageSize()) {
            IndependentPanelChrome.icon(g, mx, my, x, c.footerY() - 30, RoulettePanelStyle.Glyph.CANCEL);
            IndependentPanelChrome.icon(g, mx, my, x + 80, c.footerY() - 30, RoulettePanelStyle.Glyph.APPLY);
            g.drawString(font, (page + 1) + " / " + Math.max(1, (rows.size() + pageSize() - 1) / pageSize()), x + 30, c.footerY() - 23, 0xFFB1B1B1, false);
        }
        IndependentPanelChrome.button(g, font, mx, my, right - 100, c.footerY(), 100, Component.translatable("gui.sparkle_morpher.target_action.open_wheel"), true);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button) {
        IndependentPanelChrome c = IndependentPanelChrome.of(width, height);
        if (c.isBackOrClose(mouseX, mouseY)) { onClose(); return true; }
        if (IndependentPanelChrome.inside(mouseX, mouseY, c.contentRight() - 22, c.bodyTop(), 22, 22)) { rebuildTargets(); return true; }
        if (IndependentPanelChrome.inside(mouseX, mouseY, c.contentRight() - 100, c.footerY(), 100, 22)) { openSelected(); return true; }
        List<TargetRow> rows = visibleTargets();
        if (IndependentPanelChrome.inside(mouseX, mouseY, c.contentLeft(), rowTop(), c.contentRight() - c.contentLeft(), pageSize() * rowHeight())) {
            int index = page * pageSize() + (int) ((mouseY - rowTop()) / rowHeight());
            if (index < rows.size()) selected = rows.get(index).uuid();
            return true;
        }
        if (rows.size() > pageSize() && IndependentPanelChrome.inside(mouseX, mouseY, c.contentLeft(), c.footerY() - 30, 102, 22)) {
            page = Math.max(0, Math.min((rows.size() - 1) / pageSize(), page + (mouseX < c.contentLeft() + 50 ? -1 : 1))); return true;
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        var c=IndependentPanelChrome.of(width,height);
        if(sy!=0&&IndependentPanelChrome.inside(mx,my,c.contentLeft(),rowTop(),c.contentRight()-c.contentLeft(),Math.max(1,c.footerY()-rowTop()))){page=CloudScreenPagination.range(visibleTargets().size(),pageSize(),page+(sy>0?-1:1)).page();return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }

    private void openSelected() { visibleTargets().stream().filter(row -> row.uuid().equals(selected)).findFirst().ifPresent(this::openActionWheel); }

    @Override
    public boolean keyPressed(int key,int scanCode,int modifiers) {
        if (key == 257 && (search == null || !search.isFocused())) { openSelected(); return true; }
        return super.keyPressed(key,scanCode,modifiers);
    }

    private void openActionWheel(TargetRow row) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity entity = resolve(level, row);
        if (entity == null) {
            fail(Component.translatable("gui.sparkle_morpher.target_action.error.not_loaded").getString());
            return;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            PlayerCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail(Component.translatable("gui.sparkle_morpher.target_action.error.fake_player_sync").getString()));
        } else {
            MaidCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail(Component.translatable("gui.sparkle_morpher.target_action.error.maid_not_ready").getString()));
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
            fail(Component.translatable("gui.sparkle_morpher.target_action.error.no_animations").getString());
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
