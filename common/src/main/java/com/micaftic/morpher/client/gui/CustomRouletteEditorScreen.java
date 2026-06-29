package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.animation.custom.CustomRouletteEntry;
import com.micaftic.morpher.client.animation.custom.CustomRouletteGroup;
import com.micaftic.morpher.client.animation.custom.CustomRouletteLayout;
import com.micaftic.morpher.client.animation.custom.CustomRouletteStore;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.util.data.OrderedStringMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomRouletteEditorScreen extends Screen {
    private static final int ENTRY_H = 16;
    private static final int PANEL_GAP = 10;
    private static final int TOOL_GAP = 8;

    private final String modelId;
    private final ModelAssembly modelAssembly;
    private final List<CustomRouletteEntry> availableAnimations;
    private final List<CustomRouletteEntry> pendingRootEntries;
    private final List<MutableGroup> pendingGroups;
    private final List<Hit> hits = new ArrayList<>();

    private int selectedGroupIndex = -1;
    private int leftScroll;
    private int rightScroll;
    private int hoveredLeftIndex = -1;
    private int hoveredRightIndex = -1;
    private int hoveredRightKind;
    private int hoveredRightExtra = -1;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int leftX;
    private int rightX;
    private int listTop;
    private int listBottom;
    private int listW;

    private static class MutableGroup {
        String name;
        final List<CustomRouletteEntry> animations = new ArrayList<>();

        MutableGroup(String name) {
            this.name = name;
        }
    }

    private record Hit(int x, int y, int w, int h, Component tooltip, Runnable action) {
    }

    public CustomRouletteEditorScreen(String modelId, ModelAssembly modelAssembly) {
        super(Component.translatable("gui.sparkle_morpher.roulette.editor.title"));
        this.modelId = modelId;
        this.modelAssembly = modelAssembly;
        this.availableAnimations = buildAvailableAnimations();

        CustomRouletteLayout existing = CustomRouletteStore.load(modelId);
        if (existing != null) {
            this.pendingRootEntries = new ArrayList<>(existing.rootEntries());
            this.pendingGroups = new ArrayList<>();
            for (CustomRouletteGroup group : existing.groups()) {
                MutableGroup mutable = new MutableGroup(group.name());
                mutable.animations.addAll(group.animations());
                this.pendingGroups.add(mutable);
            }
        } else {
            this.pendingRootEntries = new ArrayList<>();
            this.pendingGroups = new ArrayList<>();
        }
    }

    private List<CustomRouletteEntry> buildAvailableAnimations() {
        List<CustomRouletteEntry> list = new ArrayList<>();
        ModelProperties props = modelAssembly.getModelData().getModelProperties();
        OrderedStringMap<String, String> rootAnims = props.getExtraAnimation();
        for (int i = 0; i < rootAnims.size(); i++) {
            String key = rootAnims.getKeyAt(i);
            if (!key.startsWith("#")) list.add(new CustomRouletteEntry(key, "", i, rootAnims.getValueAt(i)));
        }
        Map<String, OrderedStringMap<String, String>> classify = props.getExtraAnimationClassify();
        for (Map.Entry<String, OrderedStringMap<String, String>> entry : classify.entrySet()) {
            String category = entry.getKey();
            OrderedStringMap<String, String> subAnims = entry.getValue();
            for (int i = 0; i < subAnims.size(); i++) {
                String key = subAnims.getKeyAt(i);
                if (!key.startsWith("#")) list.add(new CustomRouletteEntry(key, category, i, subAnims.getValueAt(i)));
            }
        }
        return list;
    }

    @Override
    protected void init() {
        clearWidgets();
        layout();
    }

    private void layout() {
        int w = Math.min(this.width - 28, 720);
        int h = Math.min(this.height - 28, 390);
        panelLeft = (this.width - w) / 2;
        panelTop = (this.height - h) / 2;
        panelRight = panelLeft + w;
        panelBottom = panelTop + h;
        int innerLeft = panelLeft + 10;
        int innerRight = panelRight - 10;
        listTop = panelTop + 48;
        listBottom = panelBottom - 42;
        listW = Math.max(90, (innerRight - innerLeft - PANEL_GAP) / 2);
        leftX = innerLeft;
        rightX = innerLeft + listW + PANEL_GAP;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        layout();
        g.fill(0, 0, this.width, this.height, RoulettePanelStyle.BG);
        RoulettePanelStyle.glassPanel(g, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 18, RoulettePanelStyle.PANEL_ACTIVE);
        RoulettePanelStyle.drawCentered(g, this.font, this.title, this.width / 2, panelTop + 5, RoulettePanelStyle.TEXT);

        String target = selectedGroupIndex >= 0 && selectedGroupIndex < pendingGroups.size()
                ? pendingGroups.get(selectedGroupIndex).name
                : Component.translatable("gui.sparkle_morpher.roulette.editor.root_label").getString();
        RoulettePanelStyle.drawCentered(g, this.font,
                Component.literal(RoulettePanelStyle.trim(this.font, "[ " + target + " ]", panelRight - panelLeft - 24)),
                this.width / 2, panelTop + 28, 0xFFFFC857);

        renderListShell(g, leftX, listTop, listW, listBottom - listTop, Component.translatable("gui.sparkle_morpher.roulette.editor.available"));
        renderListShell(g, rightX, listTop, listW, listBottom - listTop, Component.translatable("gui.sparkle_morpher.roulette.editor.custom_layout"));
        renderLeftList(g, mouseX, mouseY, leftX, listTop + 18, listW, listBottom - listTop - 18);
        renderRightList(g, mouseX, mouseY, rightX, listTop + 18, listW, listBottom - listTop - 18);
        renderToolbar(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderListShell(GuiGraphics g, int x, int y, int w, int h, Component title) {
        RoulettePanelStyle.secondaryGlassPanel(g, x, y, w, h);
        g.fill(x, y, x + w, y + 16, RoulettePanelStyle.PANEL_ACTIVE);
        g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, title.getString(), w - 10)), x + 5, y + 5, RoulettePanelStyle.TEXT, false);
    }

    private void renderLeftList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        hoveredLeftIndex = -1;
        Set<String> added = addedKeys();
        leftScroll = Mth.clamp(leftScroll, 0, maxLeftScroll(h));
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < availableAnimations.size(); i++) {
            int rowY = y + i * ENTRY_H - leftScroll;
            if (rowY < y - ENTRY_H || rowY >= y + h) continue;
            CustomRouletteEntry entry = availableAnimations.get(i);
            boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
            if (hover) hoveredLeftIndex = i;
            boolean alreadyAdded = added.contains(keyOf(entry));
            RoulettePanelStyle.fill(g, x + 3, rowY + 1, w - 6, ENTRY_H - 2, hover ? RoulettePanelStyle.PANEL_HOVER : alreadyAdded ? 0x44282828 : 0x55303030);
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, listLabel(entry), w - 14)),
                    x + 7, rowY + 5, alreadyAdded ? RoulettePanelStyle.MUTED : RoulettePanelStyle.TEXT, false);
        }
        g.disableScissor();
        renderScrollbar(g, x + w - 3, y, h, leftScroll, maxLeftScroll(h), availableAnimations.size() * ENTRY_H);
    }

    private void renderRightList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        hoveredRightIndex = -1;
        hoveredRightKind = 0;
        hoveredRightExtra = -1;
        rightScroll = Mth.clamp(rightScroll, 0, maxRightScroll(h));
        g.enableScissor(x, y, x + w, y + h);
        int logical = renderRootSection(g, mouseX, mouseY, x, y, w, h, 0);
        for (int groupIndex = 0; groupIndex < pendingGroups.size(); groupIndex++) {
            logical = renderGroupSection(g, mouseX, mouseY, x, y, w, h, logical, groupIndex);
        }
        g.disableScissor();
        renderScrollbar(g, x + w - 3, y, h, rightScroll, maxRightScroll(h), rightContentHeight());
    }

    private int renderRootSection(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int logical) {
        int rowY = y + logical * ENTRY_H - rightScroll;
        boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
        if (rowY >= y - ENTRY_H && rowY < y + h) {
            if (hover) {
                hoveredRightKind = 4;
                hoveredRightIndex = -1;
            }
            RoulettePanelStyle.fill(g, x + 3, rowY + 1, w - 6, ENTRY_H - 2,
                    selectedGroupIndex < 0 ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030);
            g.drawString(this.font, Component.literal("[Root]"), x + 7, rowY + 5, selectedGroupIndex < 0 ? 0xFFFFFFFF : 0xFFFFC857, false);
        }
        logical++;
        for (int i = 0; i < pendingRootEntries.size(); i++) {
            rowY = y + logical * ENTRY_H - rightScroll;
            if (rowY >= y - ENTRY_H && rowY < y + h) {
                hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
                if (hover) {
                    hoveredRightKind = 1;
                    hoveredRightIndex = i;
                }
                RoulettePanelStyle.fill(g, x + 9, rowY + 1, w - 12, ENTRY_H - 2, hover ? RoulettePanelStyle.RED_SOFT : 0x44303030);
                g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, listLabel(pendingRootEntries.get(i)), w - 24)),
                        x + 14, rowY + 5, hover ? 0xFFFFD8D8 : RoulettePanelStyle.TEXT, false);
            }
            logical++;
        }
        return logical;
    }

    private int renderGroupSection(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int logical, int groupIndex) {
        MutableGroup group = pendingGroups.get(groupIndex);
        int rowY = y + logical * ENTRY_H - rightScroll;
        boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
        if (rowY >= y - ENTRY_H && rowY < y + h) {
            if (hover) {
                hoveredRightKind = 2;
                hoveredRightIndex = groupIndex;
            }
            RoulettePanelStyle.fill(g, x + 3, rowY + 1, w - 6, ENTRY_H - 2,
                    groupIndex == selectedGroupIndex ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030);
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, "[#" + group.name + "]", w - 14)),
                    x + 7, rowY + 5, groupIndex == selectedGroupIndex ? 0xFFFFFFFF : 0xFFFFC857, false);
        }
        logical++;
        for (int i = 0; i < group.animations.size(); i++) {
            rowY = y + logical * ENTRY_H - rightScroll;
            if (rowY >= y - ENTRY_H && rowY < y + h) {
                hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
                if (hover) {
                    hoveredRightKind = 3;
                    hoveredRightIndex = i;
                    hoveredRightExtra = groupIndex;
                }
                RoulettePanelStyle.fill(g, x + 9, rowY + 1, w - 12, ENTRY_H - 2, hover ? RoulettePanelStyle.RED_SOFT : 0x44303030);
                g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, listLabel(group.animations.get(i)), w - 24)),
                        x + 14, rowY + 5, hover ? 0xFFFFD8D8 : RoulettePanelStyle.TEXT, false);
            }
            logical++;
        }
        return logical;
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        int buttons = 5;
        int totalW = buttons * RoulettePanelStyle.ICON + (buttons - 1) * TOOL_GAP;
        int x = this.width / 2 - totalW / 2;
        int y = panelBottom - 28;
        renderIconButton(g, mouseX, mouseY, x, y, RoulettePanelStyle.Glyph.CREATE, Component.translatable("gui.sparkle_morpher.roulette.editor.new_group"), this::createNewGroup, true);
        x += RoulettePanelStyle.ICON + TOOL_GAP;
        renderIconButton(g, mouseX, mouseY, x, y, RoulettePanelStyle.Glyph.DELETE, Component.translatable("gui.sparkle_morpher.roulette.editor.delete_group"), this::deleteSelectedGroup, selectedGroupIndex >= 0 && selectedGroupIndex < pendingGroups.size());
        x += RoulettePanelStyle.ICON + TOOL_GAP;
        renderIconButton(g, mouseX, mouseY, x, y, RoulettePanelStyle.Glyph.SAVE, Component.translatable("gui.sparkle_morpher.roulette.editor.save"), this::saveLayout, true);
        x += RoulettePanelStyle.ICON + TOOL_GAP;
        renderIconButton(g, mouseX, mouseY, x, y, RoulettePanelStyle.Glyph.RELOAD, Component.translatable("gui.sparkle_morpher.roulette.editor.revert"), this::revertLayout, true);
        x += RoulettePanelStyle.ICON + TOOL_GAP;
        renderIconButton(g, mouseX, mouseY, x, y, RoulettePanelStyle.Glyph.CLOSE, Component.translatable("gui.back"), this::onClose, true);
    }

    private void renderIconButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, RoulettePanelStyle.Glyph glyph, Component tooltip, Runnable action, boolean active) {
        RoulettePanelStyle.iconButton(g, mouseX, mouseY, x, y, glyph, active);
        if (active) hits.add(new Hit(x, y, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON, tooltip, action));
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (Hit hit : hits) {
            if (!RoulettePanelStyle.inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            String text = hit.tooltip.getString();
            if (text.isBlank()) return;
            int tw = Math.min(220, this.font.width(text) + 10);
            int tx = Math.min(mouseX + 10, this.width - tw - 4);
            int ty = Math.min(mouseY + 10, this.height - 18);
            RoulettePanelStyle.fill(g, tx, ty, tw, 16, 0xEE101010);
            RoulettePanelStyle.border(g, tx, ty, tw, 16, 0x88FFFFFF);
            g.drawString(this.font, Component.literal(RoulettePanelStyle.trim(this.font, text, tw - 8)), tx + 5, ty + 5, 0xFFFFFFFF, false);
            return;
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll, int contentH) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, h * h / Math.max(1, contentH));
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, maxScroll);
        RoulettePanelStyle.fill(g, x, thumbY, 1, thumbH, 0xB8FFFFFF);
    }

    private Set<String> addedKeys() {
        Set<String> added = new HashSet<>();
        for (CustomRouletteEntry entry : pendingRootEntries) added.add(keyOf(entry));
        for (MutableGroup group : pendingGroups) for (CustomRouletteEntry entry : group.animations) added.add(keyOf(entry));
        return added;
    }

    private String keyOf(CustomRouletteEntry entry) {
        return entry.key() + "@" + entry.category();
    }

    private String listLabel(CustomRouletteEntry entry) {
        String label = localizedLabel(entry);
        return StringUtils.isNotBlank(entry.category()) ? "[" + entry.category() + "] " + label : label;
    }

    private String localizedLabel(CustomRouletteEntry entry) {
        String path = StringUtils.isBlank(entry.category())
                ? "properties.extra_animation.%s".formatted(entry.key())
                : "properties.extra_animation_classify.%s.%s".formatted(entry.category(), entry.key());
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, path, entry.displayLabel());
    }

    private int maxLeftScroll(int h) {
        return Math.max(0, availableAnimations.size() * ENTRY_H - h);
    }

    private int rightContentHeight() {
        int rows = 1 + pendingRootEntries.size();
        for (MutableGroup group : pendingGroups) rows += 1 + group.animations.size();
        return rows * ENTRY_H;
    }

    private int maxRightScroll(int h) {
        return Math.max(0, rightContentHeight() - h);
    }

    private void saveLayout() {
        List<CustomRouletteGroup> groups = new ArrayList<>();
        for (MutableGroup group : pendingGroups) groups.add(new CustomRouletteGroup(group.name, new ArrayList<>(group.animations)));
        CustomRouletteStore.save(new CustomRouletteLayout(modelId, new ArrayList<>(pendingRootEntries), groups));
        try {
            GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.RouletteContentMode.CUSTOM);
        } catch (Exception ignored) {
        }
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null) player.sendSystemMessage(Component.translatable("message.sparkle_morpher.roulette.editor.saved"));
        onClose();
    }

    private void revertLayout() {
        CustomRouletteStore.delete(modelId);
        pendingRootEntries.clear();
        pendingGroups.clear();
        selectedGroupIndex = -1;
        try {
            GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.RouletteContentMode.ORIGINAL);
        } catch (Exception ignored) {
        }
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null) player.sendSystemMessage(Component.translatable("message.sparkle_morpher.roulette.editor.reverted"));
        onClose();
    }

    private void createNewGroup() {
        pendingGroups.add(new MutableGroup("Group" + (pendingGroups.size() + 1)));
        selectedGroupIndex = pendingGroups.size() - 1;
    }

    private void deleteSelectedGroup() {
        if (selectedGroupIndex >= 0 && selectedGroupIndex < pendingGroups.size()) {
            pendingGroups.remove(selectedGroupIndex);
            selectedGroupIndex = pendingGroups.isEmpty() ? -1 : Math.min(selectedGroupIndex, pendingGroups.size() - 1);
        }
    }

    private void addSelectedAnimation() {
        if (hoveredLeftIndex < 0 || hoveredLeftIndex >= availableAnimations.size()) return;
        CustomRouletteEntry entry = availableAnimations.get(hoveredLeftIndex);
        if (addedKeys().contains(keyOf(entry))) return;
        if (selectedGroupIndex < 0) pendingRootEntries.add(entry);
        else if (selectedGroupIndex < pendingGroups.size()) pendingGroups.get(selectedGroupIndex).animations.add(entry);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (RoulettePanelStyle.inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                hit.action.run();
                return true;
            }
        }
        if (hoveredLeftIndex >= 0) {
            addSelectedAnimation();
            return true;
        }
        if (hoveredRightKind == 4) {
            selectedGroupIndex = -1;
            return true;
        }
        if (hoveredRightKind == 2 && hoveredRightIndex >= 0 && hoveredRightIndex < pendingGroups.size()) {
            selectedGroupIndex = hoveredRightIndex;
            return true;
        }
        if (hoveredRightKind == 1 && hoveredRightIndex >= 0 && hoveredRightIndex < pendingRootEntries.size()) {
            pendingRootEntries.remove(hoveredRightIndex);
            return true;
        }
        if (hoveredRightKind == 3 && hoveredRightExtra >= 0 && hoveredRightExtra < pendingGroups.size()
                && hoveredRightIndex >= 0 && hoveredRightIndex < pendingGroups.get(hoveredRightExtra).animations.size()) {
            pendingGroups.get(hoveredRightExtra).animations.remove(hoveredRightIndex);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int bodyTop = listTop + 18;
        int bodyH = listBottom - listTop - 18;
        if (RoulettePanelStyle.inside(mouseX, mouseY, leftX, bodyTop, listW, bodyH)) {
            leftScroll = Mth.clamp(leftScroll - (int) (scrollY * ENTRY_H), 0, maxLeftScroll(bodyH));
            return true;
        }
        if (RoulettePanelStyle.inside(mouseX, mouseY, rightX, bodyTop, listW, bodyH)) {
            rightScroll = Mth.clamp(rightScroll - (int) (scrollY * ENTRY_H), 0, maxRightScroll(bodyH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
