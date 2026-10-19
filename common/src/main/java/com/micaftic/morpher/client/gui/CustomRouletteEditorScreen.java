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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    private static final int ENTRY_H = 31;
    private static final int PANEL_GAP = 18;
    private static final int TOOL_GAP = 8;

    private final String modelId;
    private Screen returnScreen;
    private net.minecraft.client.gui.components.EditBox search;
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

    public CustomRouletteEditorScreen(String modelId,ModelAssembly modelAssembly,Screen parent){this(modelId,modelAssembly);this.returnScreen=parent;}
    @Override public void onClose(){com.micaftic.morpher.util.InputUtil.setScreen(returnScreen);}
    private List<CustomRouletteEntry> buildAvailableAnimations() {
        List<CustomRouletteEntry> list = new ArrayList<>();
        ModelProperties props = modelAssembly.getModelData().getModelProperties();

        OrderedStringMap<String, String> rootAnims = props.getExtraAnimation();
        for (int i = 0; i < rootAnims.size(); i++) {
            String key = rootAnims.getKeyAt(i);
            if (!key.startsWith("#")) {
                list.add(new CustomRouletteEntry(key, "", i, rootAnims.getValueAt(i)));
            }
        }

        Map<String, OrderedStringMap<String, String>> classify = props.getExtraAnimationClassify();
        for (Map.Entry<String, OrderedStringMap<String, String>> entry : classify.entrySet()) {
            String category = entry.getKey();
            OrderedStringMap<String, String> subAnims = entry.getValue();
            for (int i = 0; i < subAnims.size(); i++) {
                String key = subAnims.getKeyAt(i);
                if (!key.startsWith("#")) {
                    list.add(new CustomRouletteEntry(key, category, i, subAnims.getValueAt(i)));
                }
            }
        }

        return list;
    }

    @Override
    protected void init(){String query=search==null?"":search.getValue();clearWidgets();layout();search=new net.minecraft.client.gui.components.EditBox(font,leftX+6,panelTop+114,listW-12,12,Component.literal("搜索动画"));search.setBordered(false);search.setValue(query);search.setHint(Component.literal("搜索动画…"));search.setResponder(v->leftScroll=0);addRenderableWidget(search);}

    private void layout(){var c=com.micaftic.morpher.core.gui.IndependentPanelChrome.of(width,height);panelLeft=c.left();panelTop=c.top();panelRight=c.right();panelBottom=c.bottom();leftX=c.contentLeft();listW=Math.max(1,(c.contentRight()-leftX-PANEL_GAP)/2);rightX=leftX+listW+PANEL_GAP;listTop=panelTop+141;listBottom=panelBottom-16;}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        hits.clear();layout();RoulettePanelStyle.glassPanel(g,panelLeft,panelTop,panelRight-panelLeft,panelBottom-panelTop);com.micaftic.morpher.core.gui.IndependentPanelChrome.text(g,font,Component.literal(title.getString()+" · "+modelAssembly.getDisplayName(modelId)),leftX,panelTop+15,1.4f,RoulettePanelStyle.TEXT);renderToolbar(g,mx,my);
        g.text(font,Component.translatable("gui.sparkle_morpher.roulette.editor.available"),leftX,panelTop+80,RoulettePanelStyle.TEXT,false);g.text(font,Component.translatable("gui.sparkle_morpher.roulette.editor.custom_layout"),rightX,panelTop+80,RoulettePanelStyle.TEXT,false);
        RoulettePanelStyle.roundedSurface(g,leftX,panelTop+108,listW,22,3,0x0CFFFFFF,0x22FFFFFF);RoulettePanelStyle.roundedSurface(g,rightX,panelTop+108,listW,22,3,0x0CFFFFFF,0x22FFFFFF);
        String group=selectedGroupIndex<0?"根目录":pendingGroups.get(selectedGroupIndex).name;g.text(font,Component.literal("分组："+group+" ▾"),rightX+6,panelTop+115,RoulettePanelStyle.TEXT,false);hits.add(new Hit(rightX,panelTop+108,listW,22,Component.literal("切换分组"),()->{selectedGroupIndex++;if(selectedGroupIndex>=pendingGroups.size())selectedGroupIndex=-1;}));
        renderListShell(g,leftX,listTop,listW,listBottom-listTop,Component.literal("动画"));renderListShell(g,rightX,listTop,listW,listBottom-listTop,Component.literal("分组 / 动画"));renderLeftList(g,mx,my,leftX,listTop+24,listW,listBottom-listTop-24);renderRightList(g,mx,my,rightX,listTop+24,listW,listBottom-listTop-24);
        super.extractRenderState(g,mx,my,pt);renderTooltip(g,mx,my);
    }

    private void renderListShell(GuiGraphicsExtractor g,int x,int y,int w,int h,Component title){RoulettePanelStyle.fill(g,x,y,w,24,0x0CFFFFFF);g.text(font,title,x+10,y+8,RoulettePanelStyle.MUTED,false);}

    private void renderLeftList(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h){
        hoveredLeftIndex=-1;Set<String> added=addedKeys();leftScroll=Mth.clamp(leftScroll,0,maxLeftScroll(h));String query=search==null?"":search.getValue().strip().toLowerCase(java.util.Locale.ROOT);int logical=0;g.enableScissor(x,y,x+w,y+h);
        for(int i=0;i<availableAnimations.size();i++){var entry=availableAnimations.get(i);String label=listLabel(entry);if(!query.isBlank()&&!label.toLowerCase(java.util.Locale.ROOT).contains(query))continue;int yy=y+logical++*ENTRY_H-leftScroll;if(yy<y-ENTRY_H||yy>=y+h)continue;boolean hover=RoulettePanelStyle.inside(mx,my,x,yy,w,ENTRY_H),already=added.contains(keyOf(entry));if(hover)hoveredLeftIndex=i;if(hover||already)RoulettePanelStyle.fill(g,x,yy,w,ENTRY_H-1,hover?0x18FFFFFF:0x08FFFFFF);RoulettePanelStyle.fill(g,x,yy+ENTRY_H-1,w,1,0x14FFFFFF);g.text(font,Component.literal(RoulettePanelStyle.trim(font,label,w-20)),x+10,yy+11,already?RoulettePanelStyle.MUTED:RoulettePanelStyle.TEXT,false);}
        g.disableScissor();renderScrollbar(g,x+w-3,y,h,leftScroll,maxLeftScroll(h),logical*ENTRY_H);
    }

    private void renderRightList(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        hoveredRightIndex = -1;
        hoveredRightKind = 0;
        hoveredRightExtra = -1;
        rightScroll = Mth.clamp(rightScroll, 0, maxRightScroll(h));

        g.enableScissor(x, y, x + w, y + h);
        int logical = 0;
        logical = renderRootSection(g, mouseX, mouseY, x, y, w, h, logical);
        for (int groupIndex = 0; groupIndex < pendingGroups.size(); groupIndex++) {
            logical = renderGroupSection(g, mouseX, mouseY, x, y, w, h, logical, groupIndex);
        }
        g.disableScissor();
        renderScrollbar(g, x + w - 3, y, h, rightScroll, maxRightScroll(h), rightContentHeight());
    }

    private int renderRootSection(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, int logical) {
        int rowY = y + logical * ENTRY_H - rightScroll;
        boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
        if (rowY >= y - ENTRY_H && rowY < y + h) {
            if (hover) {
                hoveredRightKind = 4;
                hoveredRightIndex = -1;
            }
            int bg = selectedGroupIndex < 0 ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030;
            RoulettePanelStyle.fill(g, x + 3, rowY + 1, w - 6, ENTRY_H - 2, bg);
            g.text(this.font, Component.literal("根目录"), x + 7, rowY + 11,
                    selectedGroupIndex < 0 ? 0xFFFFFFFF : RoulettePanelStyle.MUTED, false);
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
                RoulettePanelStyle.fill(g, x + 9, rowY + 1, w - 12, ENTRY_H - 2,
                        hover ? RoulettePanelStyle.RED_SOFT : 0x44303030);
                g.text(this.font, Component.literal(RoulettePanelStyle.trim(this.font, listLabel(pendingRootEntries.get(i)), w - 24)),
                        x + 14, rowY + 11, hover ? 0xFFFFD8D8 : RoulettePanelStyle.TEXT, false);
            }
            logical++;
        }
        return logical;
    }

    private int renderGroupSection(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, int logical, int groupIndex) {
        MutableGroup group = pendingGroups.get(groupIndex);
        int rowY = y + logical * ENTRY_H - rightScroll;
        boolean hover = RoulettePanelStyle.inside(mouseX, mouseY, x, rowY, w, ENTRY_H);
        if (rowY >= y - ENTRY_H && rowY < y + h) {
            if (hover) {
                hoveredRightKind = 2;
                hoveredRightIndex = groupIndex;
            }
            int bg = groupIndex == selectedGroupIndex ? RoulettePanelStyle.PANEL_ACTIVE : hover ? RoulettePanelStyle.PANEL_HOVER : 0x55303030;
            RoulettePanelStyle.fill(g, x + 3, rowY + 1, w - 6, ENTRY_H - 2, bg);
            String label = "[#" + group.name + "]";
            g.text(this.font, Component.literal(RoulettePanelStyle.trim(this.font, label, w - 14)),
                    x + 7, rowY + 11, groupIndex == selectedGroupIndex ? 0xFFFFFFFF : RoulettePanelStyle.MUTED, false);
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
                RoulettePanelStyle.fill(g, x + 9, rowY + 1, w - 12, ENTRY_H - 2,
                        hover ? RoulettePanelStyle.RED_SOFT : 0x44303030);
                g.text(this.font, Component.literal(RoulettePanelStyle.trim(this.font, listLabel(group.animations.get(i)), w - 24)),
                        x + 14, rowY + 11, hover ? 0xFFFFD8D8 : RoulettePanelStyle.TEXT, false);
            }
            logical++;
        }
        return logical;
    }

    private void renderToolbar(GuiGraphicsExtractor g,int mx,int my){int x=leftX,y=panelTop+42;RoulettePanelStyle.Glyph[] icons={RoulettePanelStyle.Glyph.UP,RoulettePanelStyle.Glyph.PLUS,RoulettePanelStyle.Glyph.DELETE,RoulettePanelStyle.Glyph.RELOAD,RoulettePanelStyle.Glyph.SAVE,RoulettePanelStyle.Glyph.SETTINGS};String[] labels={"返回动作轮盘","新建分组","删除分组","撤销布局修改","保存轮盘布局","轮盘设置"};Runnable[] actions={this::onClose,this::createNewGroup,this::deleteSelectedGroup,this::revertLayout,this::saveLayout,this::openSettings};for(int i=0;i<6;i++){int xx=x+i*26+(i>=4?8:0);com.micaftic.morpher.core.gui.IndependentPanelChrome.icon(g,mx,my,xx,y,icons[i]);if(i!=2||selectedGroupIndex>=0)hits.add(new Hit(xx,y,22,22,Component.literal(labels[i]),actions[i]));}}

    private void renderIconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y,
                                  RoulettePanelStyle.Glyph glyph, Component tooltip, Runnable action, boolean active) {
        RoulettePanelStyle.iconButton(g, mouseX, mouseY, x, y, glyph, active);
        if (active) {
            hits.add(new Hit(x, y, RoulettePanelStyle.ICON, RoulettePanelStyle.ICON, tooltip, action));
        }
    }

    private void renderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Hit hit : hits) {
            if (!RoulettePanelStyle.inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                continue;
            }
            String text = hit.tooltip.getString();
            if (text.isBlank()) {
                return;
            }
            int tw = Math.min(220, this.font.width(text) + 10);
            int tx = Math.min(mouseX + 10, this.width - tw - 4);
            int ty = Math.min(mouseY + 10, this.height - 18);
            RoulettePanelStyle.fill(g, tx, ty, tw, 16, 0xEE101010);
            RoulettePanelStyle.border(g, tx, ty, tw, 16, 0x88FFFFFF);
            g.text(this.font, Component.literal(RoulettePanelStyle.trim(this.font, text, tw - 8)),
                    tx + 5, ty + 5, 0xFFFFFFFF, false);
            return;
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int maxScroll, int contentH) {
        if (maxScroll <= 0) {
            return;
        }
        int thumbH = Math.max(16, h * h / Math.max(1, contentH));
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, maxScroll);
        RoulettePanelStyle.fill(g, x, thumbY, 1, thumbH, 0xB8FFFFFF);
    }

    private Set<String> addedKeys() {
        Set<String> added = new HashSet<>();
        for (CustomRouletteEntry entry : pendingRootEntries) {
            added.add(keyOf(entry));
        }
        for (MutableGroup group : pendingGroups) {
            for (CustomRouletteEntry entry : group.animations) {
                added.add(keyOf(entry));
            }
        }
        return added;
    }

    private String keyOf(CustomRouletteEntry entry) {
        return entry.key() + "@" + entry.category();
    }

    private String listLabel(CustomRouletteEntry entry) {
        String label = localizedLabel(entry);
        if (StringUtils.isNotBlank(entry.category())) {
            return "[" + entry.category() + "] " + label;
        }
        return label;
    }

    private String localizedLabel(CustomRouletteEntry entry) {
        String path = StringUtils.isBlank(entry.category())
                ? "properties.extra_animation.%s".formatted(entry.key())
                : "properties.extra_animation_classify.%s.%s".formatted(entry.category(), entry.key());
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, path, entry.displayLabel());
    }

    private int maxLeftScroll(int h){String q=search==null?"":search.getValue().strip().toLowerCase(java.util.Locale.ROOT);long count=availableAnimations.stream().filter(e->q.isBlank()||listLabel(e).toLowerCase(java.util.Locale.ROOT).contains(q)).count();return Math.max(0,(int)count*ENTRY_H-h);}

    private int rightContentHeight() {
        int rows = 1 + pendingRootEntries.size();
        for (MutableGroup group : pendingGroups) {
            rows += 1 + group.animations.size();
        }
        return rows * ENTRY_H;
    }

    private int maxRightScroll(int h) {
        return Math.max(0, rightContentHeight() - h);
    }

    private void openSettings(){if(returnScreen instanceof com.micaftic.morpher.core.gui.UnifiedRouletteScreen wheel){com.micaftic.morpher.util.InputUtil.setScreen(wheel.settingsScreen());return;}var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null)com.micaftic.morpher.capability.PlayerCapability.get(player).ifPresent(cap->com.micaftic.morpher.util.InputUtil.setScreen(new com.micaftic.morpher.core.gui.RouletteSettingsScreen(this,modelId,modelAssembly,cap)));}
    private void saveLayout() {
        List<CustomRouletteGroup> groups = new ArrayList<>();
        for (MutableGroup group : pendingGroups) {
            groups.add(new CustomRouletteGroup(group.name, new ArrayList<>(group.animations)));
        }
        CustomRouletteLayout layout = new CustomRouletteLayout(modelId, new ArrayList<>(pendingRootEntries), groups);
        CustomRouletteStore.save(layout);
        try {
            GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.RouletteContentMode.CUSTOM);
            GeneralConfig.ROULETTE_CONTENT_MODE.save();
        } catch (Exception ignored) {
        }
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.roulette.editor.saved"));
        }
        onClose();
    }

    private void revertLayout(){pendingRootEntries.clear();pendingGroups.clear();var saved=CustomRouletteStore.load(modelId);if(saved!=null){pendingRootEntries.addAll(saved.rootEntries());for(var group:saved.groups()){var mutable=new MutableGroup(group.name());mutable.animations.addAll(group.animations());pendingGroups.add(mutable);}}selectedGroupIndex=-1;leftScroll=rightScroll=0;}

    private void createNewGroup() {
        String groupName = "Group" + (pendingGroups.size() + 1);
        pendingGroups.add(new MutableGroup(groupName));
        selectedGroupIndex = pendingGroups.size() - 1;
    }

    private void deleteSelectedGroup() {
        if (selectedGroupIndex >= 0 && selectedGroupIndex < pendingGroups.size()) {
            pendingGroups.remove(selectedGroupIndex);
            selectedGroupIndex = pendingGroups.isEmpty() ? -1 : Math.min(selectedGroupIndex, pendingGroups.size() - 1);
        }
    }

    private void addSelectedAnimation() {
        if (hoveredLeftIndex < 0 || hoveredLeftIndex >= availableAnimations.size()) {
            return;
        }
        CustomRouletteEntry entry = availableAnimations.get(hoveredLeftIndex);
        if (addedKeys().contains(keyOf(entry))) {
            return;
        }
        if (selectedGroupIndex < 0) {
            pendingRootEntries.add(entry);
        } else if (selectedGroupIndex < pendingGroups.size()) {
            pendingGroups.get(selectedGroupIndex).animations.add(entry);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x();
        double mouseY = event.y();
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

        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int bodyTop = listTop + 24;
        int bodyH = listBottom - listTop - 24;
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
