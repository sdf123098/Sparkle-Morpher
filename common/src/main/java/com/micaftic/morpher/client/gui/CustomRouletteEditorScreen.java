package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.animation.custom.CustomRouletteEntry;
import com.micaftic.morpher.client.animation.custom.CustomRouletteGroup;
import com.micaftic.morpher.client.animation.custom.CustomRouletteLayout;
import com.micaftic.morpher.client.animation.custom.CustomRouletteStore;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.util.data.OrderedStringMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义轮盘布局编辑器。
 * <p>
 * 左侧显示模型所有可用动画，右侧显示当前自定义布局。
 * 点击左侧动画添加到当前目标（根级或选中分组），点击右侧动画移除。
 * 底部按钮：保存、恢复原始、新建分组、删除分组。
 */
public class CustomRouletteEditorScreen extends Screen {

    private final String modelId;
    private final ModelAssembly modelAssembly;

    // 模型所有可用动画（不可变）
    private final List<CustomRouletteEntry> availableAnimations;

    // 编辑器内部状态（可变）
    private final List<CustomRouletteEntry> pendingRootEntries;
    private final List<MutableGroup> pendingGroups;

    // 当前添加目标：-1 = 根级，0+ = 分组索引
    private int selectedGroupIndex = -1;

    // 滚动偏移
    private int leftScroll = 0;
    private int rightScroll = 0;

    // 悬停追踪
    private int hoveredLeftIndex = -1;
    private int hoveredRightIndex = -1;
    private int hoveredRightKind = 0; // 0=root header, 1=root entry, 2=group header, 3=group entry

    /** 可变分组（内部编辑用，保存时转换为不可变 record） */
    private static class MutableGroup {
        String name;
        final List<CustomRouletteEntry> animations = new ArrayList<>();
        MutableGroup(String name) { this.name = name; }
    }

    public CustomRouletteEditorScreen(String modelId, ModelAssembly modelAssembly) {
        super(Component.translatable("gui.sparkle_morpher.roulette.editor.title"));
        this.modelId = modelId;
        this.modelAssembly = modelAssembly;

        // 构建所有可用动画列表
        this.availableAnimations = buildAvailableAnimations();

        // 加载现有自定义布局或从空白开始
        CustomRouletteLayout existing = CustomRouletteStore.load(modelId);
        if (existing != null) {
            this.pendingRootEntries = new ArrayList<>(existing.rootEntries());
            this.pendingGroups = new ArrayList<>();
            for (CustomRouletteGroup group : existing.groups()) {
                MutableGroup mg = new MutableGroup(group.name());
                mg.animations.addAll(group.animations());
                pendingGroups.add(mg);
            }
        } else {
            this.pendingRootEntries = new ArrayList<>();
            this.pendingGroups = new ArrayList<>();
        }
    }

    private List<CustomRouletteEntry> buildAvailableAnimations() {
        List<CustomRouletteEntry> list = new ArrayList<>();
        ModelProperties props = modelAssembly.getModelData().getModelProperties();

        // 根级动画
        OrderedStringMap<String, String> rootAnims = props.getExtraAnimation();
        for (int i = 0; i < rootAnims.size(); i++) {
            String key = rootAnims.getKeyAt(i);
            if (key.startsWith("#")) continue; // 跳过导航条目（#return, #submenu 等）
            list.add(new CustomRouletteEntry(key, "", i, rootAnims.getValueAt(i)));
        }

        // 子菜单动画
        Map<String, OrderedStringMap<String, String>> classify = props.getExtraAnimationClassify();
        for (Map.Entry<String, OrderedStringMap<String, String>> entry : classify.entrySet()) {
            String category = entry.getKey();
            OrderedStringMap<String, String> subAnims = entry.getValue();
            for (int i = 0; i < subAnims.size(); i++) {
                String key = subAnims.getKeyAt(i);
                if (key.startsWith("#")) continue; // 跳过导航条目
                list.add(new CustomRouletteEntry(key, category, i, subAnims.getValueAt(i)));
            }
        }

        return list;
    }

    @Override
    protected void init() {
        clearWidgets();
        int centerX = this.width / 2;
        int bottomY = this.height - 28;

        // 新建分组
        addRenderableWidget(new FlatColorButton(centerX - 150, bottomY, 60, 20,
                Component.translatable("gui.sparkle_morpher.roulette.editor.new_group"), button -> createNewGroup()));

        // 删除分组
        addRenderableWidget(new FlatColorButton(centerX - 80, bottomY, 60, 20,
                Component.translatable("gui.sparkle_morpher.roulette.editor.delete_group"), button -> deleteSelectedGroup()));

        // 保存
        addRenderableWidget(new FlatColorButton(centerX + 20, bottomY, 60, 20,
                Component.translatable("gui.sparkle_morpher.roulette.editor.save"), button -> saveLayout()));

        // 恢复原始
        addRenderableWidget(new FlatColorButton(centerX + 90, bottomY, 80, 20,
                Component.translatable("gui.sparkle_morpher.roulette.editor.revert"), button -> revertLayout()));
    }

    private void saveLayout() {
        List<CustomRouletteGroup> groups = new ArrayList<>();
        for (MutableGroup mg : pendingGroups) {
            groups.add(new CustomRouletteGroup(mg.name, new ArrayList<>(mg.animations)));
        }
        CustomRouletteLayout layout = new CustomRouletteLayout(modelId, new ArrayList<>(pendingRootEntries), groups);
        CustomRouletteStore.save(layout);
        // 自动切换到自定义模式
        try {
            GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.RouletteContentMode.CUSTOM);
        } catch (Exception ignored) {
            // 配置可能在某些平台不允许运行时修改，用户需手动切换
        }
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.roulette.editor.saved"));
        }
        onClose();
    }

    private void revertLayout() {
        CustomRouletteStore.delete(modelId);
        pendingRootEntries.clear();
        pendingGroups.clear();
        selectedGroupIndex = -1;
        try {
            GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.RouletteContentMode.ORIGINAL);
        } catch (Exception ignored) {}
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.roulette.editor.reverted"));
        }
        onClose();
    }

    private void createNewGroup() {
        String groupName = "Group" + (pendingGroups.size() + 1);
        pendingGroups.add(new MutableGroup(groupName));
        selectedGroupIndex = pendingGroups.size() - 1;
    }

    private void deleteSelectedGroup() {
        if (selectedGroupIndex >= 0 && selectedGroupIndex < pendingGroups.size()) {
            pendingGroups.remove(selectedGroupIndex);
            if (pendingGroups.isEmpty()) selectedGroupIndex = -1;
            else selectedGroupIndex = Math.min(selectedGroupIndex, pendingGroups.size() - 1);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 标题
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.sparkle_morpher.roulette.editor.title"), this.width / 2, 8, 0xFFFFFF);

        // 目标指示器
        String targetLabel = selectedGroupIndex < 0 ? I18n.get("gui.sparkle_morpher.roulette.editor.root_label") :
                (selectedGroupIndex < pendingGroups.size() ? pendingGroups.get(selectedGroupIndex).name : I18n.get("gui.sparkle_morpher.roulette.editor.root_label"));
        guiGraphics.drawCenteredString(this.font, Component.literal("[ " + targetLabel + " ]"), this.width / 2, 22, 0xFFFFCC00);

        int leftX = 8;
        int rightX = this.width / 2 + 8;
        int topY = 36;
        int panelWidth = this.width / 2 - 16;
        int listHeight = this.height - 70;
        int entryHeight = 12;

        // 左面板标题
        guiGraphics.drawString(this.font, Component.translatable("gui.sparkle_morpher.roulette.editor.available"), leftX, topY - 2, 0xFFAAAAAA, false);
        // 右面板标题
        guiGraphics.drawString(this.font, Component.translatable("gui.sparkle_morpher.roulette.editor.custom_layout"), rightX, topY - 2, 0xFFAAAAAA, false);

        renderLeftPanel(guiGraphics, mouseX, mouseY, leftX, topY + 10, panelWidth, listHeight - 10, entryHeight);
        renderRightPanel(guiGraphics, mouseX, mouseY, rightX, topY + 10, panelWidth, listHeight - 10, entryHeight);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int entryH) {
        hoveredLeftIndex = -1;
        g.enableScissor(x, y, x + w, y + h);

        // 已添加的动画集合（避免重复添加提示）
        java.util.Set<String> addedKeys = new java.util.HashSet<>();
        for (CustomRouletteEntry e : pendingRootEntries) addedKeys.add(e.key() + "@" + e.category());
        for (MutableGroup mg : pendingGroups) for (CustomRouletteEntry e : mg.animations) addedKeys.add(e.key() + "@" + e.category());

        for (int i = 0; i < availableAnimations.size(); i++) {
            int drawY = y + i * entryH - leftScroll;
            if (drawY < y - entryH || drawY > y + h) continue;

            CustomRouletteEntry entry = availableAnimations.get(i);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= drawY && mouseY < drawY + entryH;
            if (hovered) hoveredLeftIndex = i;

            boolean alreadyAdded = addedKeys.contains(entry.key() + "@" + entry.category());
            int color = alreadyAdded ? 0xFF666666 : (hovered ? 0xFFFFFF00 : 0xFFCCCCCC);
            String label = entry.displayLabel();
            if (StringUtils.isNotBlank(entry.category())) label = "[" + entry.category() + "] " + label;
            g.drawString(this.font, label, x + 2, drawY + 1, color, false);
        }

        g.disableScissor();
    }

    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int entryH) {
        hoveredRightIndex = -1;
        hoveredRightKind = 0;
        g.enableScissor(x, y, x + w, y + h);
        int logicalIdx = 0;

        // 根级条目
        if (!pendingRootEntries.isEmpty()) {
            int headerY = y + logicalIdx * entryH - rightScroll;
            if (headerY >= y && headerY < y + h) {
                boolean headerHover = mouseX >= x && mouseX < x + w && mouseY >= headerY && mouseY < headerY + entryH;
                g.drawString(this.font, Component.literal("[Root]").withStyle(net.minecraft.ChatFormatting.GREEN), x + 2, headerY + 1,
                        selectedGroupIndex < 0 ? 0xFFFFCC00 : 0xFF55FF55, false);
            }
            logicalIdx++;
            for (int i = 0; i < pendingRootEntries.size(); i++) {
                int drawY = y + logicalIdx * entryH - rightScroll;
                logicalIdx++;
                if (drawY < y - entryH || drawY > y + h) continue;
                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= drawY && mouseY < drawY + entryH;
                if (hovered) { hoveredRightIndex = i; hoveredRightKind = 1; }
                int color = hovered ? 0xFFFF6666 : 0xFFCCCCCC;
                g.drawString(this.font, "  " + pendingRootEntries.get(i).displayLabel(), x + 2, drawY + 1, color, false);
            }
        }

        // 分组条目
        for (int gi = 0; gi < pendingGroups.size(); gi++) {
            MutableGroup group = pendingGroups.get(gi);
            int headerY = y + logicalIdx * entryH - rightScroll;
            boolean headerHover = mouseX >= x && mouseX < x + w && mouseY >= headerY && mouseY < headerY + entryH;
            if (headerHover && headerY >= y && headerY < y + h) {
                hoveredRightIndex = gi;
                hoveredRightKind = 2;
            }
            if (headerY >= y && headerY < y + h) {
                int headerColor = gi == selectedGroupIndex ? 0xFFFFCC00 : 0xFFAAAAAA;
                g.drawString(this.font, Component.literal("[#" + group.name + "]"), x + 2, headerY + 1, headerColor, false);
            }
            logicalIdx++;

            for (int i = 0; i < group.animations.size(); i++) {
                int drawY = y + logicalIdx * entryH - rightScroll;
                logicalIdx++;
                if (drawY < y - entryH || drawY > y + h) continue;
                boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= drawY && mouseY < drawY + entryH;
                if (hovered) { hoveredRightIndex = i; hoveredRightKind = 3; hoveredRightExtra = gi; }
                int color = hovered ? 0xFFFF6666 : 0xFFCCCCCC;
                g.drawString(this.font, "  " + group.animations.get(i).displayLabel(), x + 2, drawY + 1, color, false);
            }
        }

        g.disableScissor();
    }

    // 用于分组条目悬停时记录所属分组索引
    private int hoveredRightExtra = -1;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 左面板点击：添加动画到当前目标
        if (hoveredLeftIndex >= 0 && hoveredLeftIndex < availableAnimations.size()) {
            CustomRouletteEntry entry = availableAnimations.get(hoveredLeftIndex);
            // 检查是否已添加
            boolean alreadyAdded = false;
            for (CustomRouletteEntry e : pendingRootEntries) {
                if (e.key().equals(entry.key()) && e.category().equals(entry.category())) { alreadyAdded = true; break; }
            }
            if (!alreadyAdded) {
                for (MutableGroup mg : pendingGroups) {
                    for (CustomRouletteEntry e : mg.animations) {
                        if (e.key().equals(entry.key()) && e.category().equals(entry.category())) { alreadyAdded = true; break; }
                    }
                }
            }
            if (!alreadyAdded) {
                if (selectedGroupIndex < 0) {
                    pendingRootEntries.add(entry);
                } else if (selectedGroupIndex < pendingGroups.size()) {
                    pendingGroups.get(selectedGroupIndex).animations.add(entry);
                }
            }
            return true;
        }

        // 右面板点击
        if (hoveredRightKind == 2 && hoveredRightIndex >= 0 && hoveredRightIndex < pendingGroups.size()) {
            // 点击分组标题：选中该分组作为添加目标
            selectedGroupIndex = hoveredRightIndex;
            return true;
        }
        if (hoveredRightKind == 1 && hoveredRightIndex >= 0 && hoveredRightIndex < pendingRootEntries.size()) {
            // 点击根级条目：移除
            pendingRootEntries.remove(hoveredRightIndex);
            return true;
        }
        if (hoveredRightKind == 3 && hoveredRightExtra >= 0 && hoveredRightExtra < pendingGroups.size()
                && hoveredRightIndex >= 0 && hoveredRightIndex < pendingGroups.get(hoveredRightExtra).animations.size()) {
            // 点击分组内条目：移除
            pendingGroups.get(hoveredRightExtra).animations.remove(hoveredRightIndex);
            return true;
        }

        // 点击 "[Root]" 标题行（选中根级作为添加目标）
        if (!pendingRootEntries.isEmpty() && hoveredRightKind == 0) {
            selectedGroupIndex = -1;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.width / 2) {
            leftScroll = Math.max(0, leftScroll - (int)(scrollY * 12));
        } else {
            rightScroll = Math.max(0, rightScroll - (int)(scrollY * 12));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
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
