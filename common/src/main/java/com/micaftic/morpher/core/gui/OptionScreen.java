package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.gui.components.buttons.FooterButton;
import com.micaftic.morpher.core.gui.components.buttons.TabButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class OptionScreen extends Screen {
    @Nullable
    protected final Screen parentScreen;
    protected final List<OptionGroup> groups = new ArrayList<>();
    protected final List<TabButton> tabButtons = new ArrayList<>();
    protected final List<OptionRow<?>> activeRows = new ArrayList<>();
    protected OptionGroup activeGroup;
    @Nullable
    protected OptionRow<?> hoveredRow;

    protected int panelLeft;
    protected int panelTop;
    protected int panelRight;
    protected int panelBottom;

    protected int tabAreaLeft;
    protected int tabAreaTop;
    protected int tabAreaRight;
    protected int tabAreaBottom;

    protected int rowAreaLeft;
    protected int rowAreaTop;
    protected int rowAreaRight;
    protected int rowAreaBottom;

    protected int rowScrollOffset;
    protected float rowScrollDisplay;
    protected int maxRowScroll;
    protected int rowContentHeight;

    protected int tabScrollOffset;
    protected float tabScrollDisplay;
    protected int maxTabScroll;
    private int tabContentHeight;
    private int tabContentWidth;

    protected boolean compactTabs;

    private long lastFrameNanos;

    private boolean draggingRowScrollbar;
    private boolean draggingTabScrollbar;

    protected FooterButton applyBtn;
    protected FooterButton undoBtn;
    protected FooterButton saveBtn;
    protected FooterButton cancelBtn;

    private static final Map<Class<? extends OptionScreen>, String> lastSelectedGroup = new HashMap<>();

    public OptionScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parentScreen = parent;
    }

    protected abstract void registerGroups();

    @Override
    protected void init() {
        groups.clear();
        tabButtons.clear();
        activeRows.clear();
        registerGroups();

        int totalWidth = computePanelWidth();
        int totalHeight = computePanelHeight();
        panelLeft = (this.width - totalWidth) / 2;
        panelTop = (this.height - totalHeight) / 2;
        panelRight = panelLeft + totalWidth;
        panelBottom = panelTop + totalHeight;

        compactTabs = shouldUseCompactTabs();
        boolean tabs = showTabs();

        if (!tabs) {
            tabAreaLeft = panelLeft;
            tabAreaRight = panelLeft;
            tabAreaTop = panelTop + 6 + 18;
            tabAreaBottom = tabAreaTop;

            rowAreaLeft = panelLeft;
            rowAreaTop = panelTop + 6 + 18;
            rowAreaRight = computeRowAreaRight();
            rowAreaBottom = panelBottom - footerReservedHeight();
        } else if (compactTabs) {
            tabAreaLeft = panelLeft;
            tabAreaRight = panelRight;
            tabAreaTop = panelTop + 6 + 18;
            tabAreaBottom = tabAreaTop + 22;

            rowAreaLeft = panelLeft;
            rowAreaTop = tabAreaBottom + 4;
            rowAreaRight = computeRowAreaRight();
            rowAreaBottom = panelBottom - footerReservedHeight();
        } else {
            tabAreaLeft = panelLeft;
            tabAreaTop = panelTop + 6 + 18;
            tabAreaRight = panelLeft + 110;
            tabAreaBottom = panelBottom - footerReservedHeight();

            rowAreaLeft = panelLeft + 110 + 6;
            rowAreaTop = panelTop + 6 + 18;
            rowAreaRight = computeRowAreaRight();
            rowAreaBottom = panelBottom - footerReservedHeight();
        }

        tabContentHeight = 0;
        tabContentWidth = 0;
        if (tabs && compactTabs) {
            int tabX = tabAreaLeft;
            for (OptionGroup g : groups) {
                int textW = this.font.width(g.getTitle());
                int w = Mth.clamp(textW + 16, 60, 140);
                TabButton tb = new TabButton(tabX, tabAreaTop, w, 22, g, this::selectGroup);
                tb.setHorizontal(true);
                tabButtons.add(tb);
                tabX += w + 2;
            }
            tabContentWidth = tabX - tabAreaLeft;
            maxTabScroll = Math.max(0, tabContentWidth - (tabAreaRight - tabAreaLeft));
        } else if (tabs) {
            int tabY = tabAreaTop;
            for (OptionGroup g : groups) {
                TabButton tb = new TabButton(tabAreaLeft, tabY, 110, 22, g, this::selectGroup);
                tabButtons.add(tb);
                tabY += 22;
            }
            tabContentHeight = tabY - tabAreaTop;
            maxTabScroll = Math.max(0, tabContentHeight - (tabAreaBottom - tabAreaTop));
        }
        tabScrollOffset = 0;
        tabScrollDisplay = 0;

        if (showFooterButtons()) {
            int footerY = panelBottom - 56;
            int btnW = RoulettePanelStyle.ICON;
            int btnH = RoulettePanelStyle.ICON;
            int gap = 6;
            cancelBtn = new FooterButton(panelRight - btnW, footerY, btnW, btnH, Component.translatable("gui.sparkle_morpher.config.cancel"), this::onCancel, RoulettePanelStyle.Glyph.CANCEL);
            saveBtn = new FooterButton(cancelBtn.getX() - btnW - gap, footerY, btnW, btnH, Component.translatable("gui.sparkle_morpher.config.save"), this::onSave, RoulettePanelStyle.Glyph.SAVE);
            applyBtn = new FooterButton(saveBtn.getX() - btnW - gap, footerY, btnW, btnH, Component.translatable("gui.sparkle_morpher.config.apply"), this::onApply, RoulettePanelStyle.Glyph.APPLY);
            undoBtn = new FooterButton(panelLeft, footerY, btnW, btnH, Component.translatable("gui.sparkle_morpher.config.undo"), this::onUndo, RoulettePanelStyle.Glyph.RELOAD);
            addRenderableWidget(undoBtn);
            addRenderableWidget(applyBtn);
            addRenderableWidget(saveBtn);
            addRenderableWidget(cancelBtn);
        } else {
            cancelBtn = null;
            saveBtn = null;
            applyBtn = null;
            undoBtn = null;
        }

        if (!groups.isEmpty()) {
            OptionGroup toSelect = groups.get(0);
            String stored = lastSelectedGroup.get(getClass());
            if (stored != null) {
                for (OptionGroup candidate : groups) {
                    if (stored.equals(candidate.getTranslationKey())) {
                        toSelect = candidate;
                        break;
                    }
                }
            }
            selectGroup(toSelect);
        }
    }

    protected int computePanelWidth() {
        return Math.min(this.width - 40, 540);
    }

    protected int computePanelHeight() {
        return Math.min(this.height - 40, 320);
    }

    protected int computeRowAreaRight() {
        return panelRight;
    }

    protected int footerReservedHeight() {
        return showFooterButtons() ? 60 : 12;
    }

    protected boolean shouldUseCompactTabs() {
        return this.width < 500;
    }

    protected boolean showFooterButtons() {
        return true;
    }

    protected boolean showTabs() {
        return true;
    }

    protected void selectGroup(OptionGroup group) {
        if (activeGroup == group && !activeRows.isEmpty()) return;
        for (OptionRow<?> r : activeRows) r.closeOverlay();
        activeRows.clear();
        activeGroup = group;
        lastSelectedGroup.put(getClass(), group.getTranslationKey());
        int selectedIndex = -1;
        for (int i = 0; i < tabButtons.size(); i++) {
            TabButton tb = tabButtons.get(i);
            tb.setSelected(tb.getGroup() == group);
            if (tb.getGroup() == group) selectedIndex = i;
        }
        if (selectedIndex >= 0 && maxTabScroll > 0) {
            TabButton sel = tabButtons.get(selectedIndex);
            if (compactTabs) {
                int btnLeft = sel.getX() - tabAreaLeft;
                int btnRight = btnLeft + sel.getWidth();
                int viewW = tabAreaRight - tabAreaLeft;
                if (btnLeft < tabScrollOffset) tabScrollOffset = btnLeft;
                else if (btnRight > tabScrollOffset + viewW) tabScrollOffset = btnRight - viewW;
            } else {
                int btnTop = selectedIndex * 22;
                int btnBot = btnTop + 22;
                int viewH = tabAreaBottom - tabAreaTop;
                if (btnTop < tabScrollOffset) tabScrollOffset = btnTop;
                else if (btnBot > tabScrollOffset + viewH) tabScrollOffset = btnBot - viewH;
            }
            tabScrollOffset = Mth.clamp(tabScrollOffset, 0, maxTabScroll);
        }

        int rowY = rowAreaTop;
        int rowW = rowAreaRight - rowAreaLeft;
        for (OptionRow<?> template : group.getRows()) {
            template.setX(rowAreaLeft);
            template.setY(rowY);
            template.setWidth(rowW);
            activeRows.add(template);
            rowY += template.getHeight() + 2;
        }
        rowContentHeight = rowY - rowAreaTop;
        maxRowScroll = Math.max(0, rowContentHeight - (rowAreaBottom - rowAreaTop));
        rowScrollOffset = Math.min(rowScrollOffset, maxRowScroll);
        rowScrollDisplay = Math.min(rowScrollDisplay, maxRowScroll);
    }

    protected boolean anyDirty() {
        for (OptionGroup g : groups) if (g.isDirty()) return true;
        return false;
    }

    protected void onApply() {
        for (OptionGroup g : groups) g.apply();
    }

    protected void onSave() {
        onApply();
        Minecraft.getInstance().setScreen(parentScreen);
    }

    protected void onCancel() {
        for (OptionGroup g : groups) g.undo();
        Minecraft.getInstance().setScreen(parentScreen);
    }

    protected void onUndo() {
        if (activeGroup != null) activeGroup.undo();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(0, 0, this.width, this.height, RoulettePanelStyle.BG);

        renderPanelBackdrop(g);

        g.fill(panelLeft, panelTop, panelRight, panelTop + 18, RoulettePanelStyle.PANEL_ACTIVE);
        g.text(this.font, this.title, panelLeft + 6, panelTop + 5, RoulettePanelStyle.TEXT, false);

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        float lerp = 1.0f - (float) Math.exp(-dt * 18.0f);
        rowScrollDisplay += (rowScrollOffset - rowScrollDisplay) * lerp;
        if (Math.abs(rowScrollOffset - rowScrollDisplay) < 0.5f) rowScrollDisplay = rowScrollOffset;
        tabScrollDisplay += (tabScrollOffset - tabScrollDisplay) * lerp;
        if (Math.abs(tabScrollOffset - tabScrollDisplay) < 0.5f) tabScrollDisplay = tabScrollOffset;

        int descY = panelBottom - 32;

        boolean inRowArea = mouseX >= rowAreaLeft && mouseX < rowAreaRight && mouseY >= rowAreaTop && mouseY < rowAreaBottom;
        int adjMouseY = inRowArea ? mouseY + Math.round(rowScrollDisplay) : Integer.MIN_VALUE;

        hoveredRow = null;
        if (inRowArea) {
            for (OptionRow<?> row : activeRows) {
                if (mouseX >= row.getX() && mouseX < row.getX() + row.getWidth() && adjMouseY >= row.getY() && adjMouseY < row.getY() + row.getHeight()) {
                    hoveredRow = row;
                    break;
                }
            }
        }

        boolean dirty = anyDirty();
        if (applyBtn != null) applyBtn.active = dirty;
        if (undoBtn != null) undoBtn.active = activeGroup != null && activeGroup.isDirty();

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        renderHeaderActions(g, mouseX, mouseY, partialTick);

        if (!tabButtons.isEmpty()) {
            boolean inTabArea = mouseX >= tabAreaLeft && mouseX < tabAreaRight && mouseY >= tabAreaTop && mouseY < tabAreaBottom;
            int adjTabMouseX = mouseX;
            int adjTabMouseY = mouseY;
            if (compactTabs) {
                adjTabMouseX = inTabArea ? mouseX + Math.round(tabScrollDisplay) : Integer.MIN_VALUE;
            } else {
                adjTabMouseY = inTabArea ? mouseY + Math.round(tabScrollDisplay) : Integer.MIN_VALUE;
            }
            g.enableScissor(tabAreaLeft, tabAreaTop, tabAreaRight, tabAreaBottom);
            g.pose().pushMatrix();
            if (compactTabs) g.pose().translate(-tabScrollDisplay, 0);
            else g.pose().translate(0, -tabScrollDisplay);
            for (TabButton tb : tabButtons) {
                tb.extractWidgetRenderState(extractor, adjTabMouseX, adjTabMouseY, partialTick);
            }
            g.pose().popMatrix();
            g.disableScissor();
            if (maxTabScroll > 0) renderTabScrollbar(g);
        }

        g.enableScissor(rowAreaLeft, rowAreaTop, rowAreaRight, rowAreaBottom);
        g.pose().pushMatrix();
        g.pose().translate(0, -rowScrollDisplay);
        for (OptionRow<?> row : activeRows) {
            row.extractWidgetRenderState(extractor, mouseX, adjMouseY, partialTick);
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (maxRowScroll > 0) renderRowScrollbar(g);

        renderDescription(g, descY);

        renderExtras(g, mouseX, mouseY, partialTick);

        for (OptionRow<?> row : activeRows) {
            if (row.isOverlayOpen()) {
                row.renderOverlay(g, mouseX, mouseY, partialTick, rowScrollDisplay);
            }
        }
    }

    protected void renderExtras(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    protected void renderHeaderActions(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    protected void collectBlurRegions(List<int[]> out) {
        out.add(new int[]{panelLeft, panelTop, panelRight - panelLeft, 18});
        int tabScroll = Math.round(tabScrollDisplay);
        for (TabButton tb : tabButtons) {
            if (compactTabs) {
                int x = tb.getX() - tabScroll;
                int xRight = x + tb.getWidth();
                int left = Math.max(x, tabAreaLeft);
                int right = Math.min(xRight, tabAreaRight);
                if (right > left) out.add(new int[]{left, tb.getY(), right - left, tb.getHeight()});
            } else {
                int y = tb.getY() - tabScroll;
                int yBot = y + tb.getHeight();
                int top = Math.max(y, tabAreaTop);
                int bot = Math.min(yBot, tabAreaBottom);
                if (bot > top) out.add(new int[]{tb.getX(), top, tb.getWidth(), bot - top});
            }
        }
        int rowScroll = Math.round(rowScrollDisplay);
        for (OptionRow<?> row : activeRows) {
            int y = row.getY() - rowScroll;
            int yBot = y + row.getHeight();
            int top = Math.max(y, rowAreaTop);
            int bot = Math.min(yBot, rowAreaBottom);
            if (bot > top) out.add(new int[]{row.getX(), top, row.getWidth(), bot - top});
        }
        addFooterRect(out, applyBtn);
        addFooterRect(out, undoBtn);
        addFooterRect(out, saveBtn);
        addFooterRect(out, cancelBtn);
        if (hoveredRow != null) {
            int descY = panelBottom - 32;
            out.add(new int[]{panelLeft, descY, panelRight - panelLeft, 28});
        }
    }

    private void addFooterRect(List<int[]> out, FooterButton btn) {
        if (btn == null || !btn.visible) return;
        out.add(new int[]{btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()});
    }

    private void renderPanelBackdrop(GuiGraphicsExtractor g) {
        RoulettePanelStyle.glassPanel(g, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop);
    }

    private void renderRowScrollbar(GuiGraphicsExtractor g) {
        int trackX = rowAreaRight - 1;
        int trackTop = rowAreaTop + 1;
        int trackBot = rowAreaBottom - 1;
        int trackH = trackBot - trackTop;
        int areaH = rowAreaBottom - rowAreaTop;
        int thumbH = Math.max(16, trackH * areaH / Math.max(1, rowContentHeight));
        int thumbY = trackTop + (int) ((trackH - thumbH) * rowScrollDisplay / Math.max(1, maxRowScroll));
        g.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, draggingRowScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    private void renderTabScrollbar(GuiGraphicsExtractor g) {
        if (compactTabs) {
            int trackY = tabAreaBottom - 1;
            int trackLeft = tabAreaLeft + 1;
            int trackRight = tabAreaRight - 1;
            int trackW = trackRight - trackLeft;
            int areaW = tabAreaRight - tabAreaLeft;
            int thumbW = Math.max(16, trackW * areaW / Math.max(1, tabContentWidth));
            int thumbX = trackLeft + (int) ((trackW - thumbW) * tabScrollDisplay / Math.max(1, maxTabScroll));
            g.fill(thumbX, trackY, thumbX + thumbW, trackY + 1, draggingTabScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
            return;
        }
        int trackX = tabAreaRight - 1;
        int trackTop = tabAreaTop + 1;
        int trackBot = tabAreaBottom - 1;
        int trackH = trackBot - trackTop;
        int areaH = tabAreaBottom - tabAreaTop;
        int thumbH = Math.max(16, trackH * areaH / Math.max(1, tabContentHeight));
        int thumbY = trackTop + (int) ((trackH - thumbH) * tabScrollDisplay / Math.max(1, maxTabScroll));
        g.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, draggingTabScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    protected void renderDescription(GuiGraphicsExtractor g, int descY) {
        if (hoveredRow == null || hoveredRow.getOption() == null) return;
        g.fill(panelLeft, descY, panelRight, descY + 28, 0x80000000);
        Option<?> opt = hoveredRow.getOption();
        Component title = opt.getLabel();
        g.text(this.font, title, panelLeft + 6, descY + 4, -1, false);

        Component desc = opt.getDescription();
        int maxWidth = panelRight - panelLeft - 6 * 2;
        List<FormattedCharSequence> lines = this.font.split(desc, maxWidth);
        int lineY = descY + 16;
        int max = Math.min(lines.size(), (28 - 16) / 10);
        for (int i = 0; i < max; i++) {
            g.text(this.font, lines.get(i), panelLeft + 6, lineY, 0xFFCCCCCC, false);
            lineY += 10;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        for (OptionRow<?> row : activeRows) {
            if (row.isOverlayOpen() && row.overlayMouseClicked(event.x(), event.y(), event.button(), rowScrollDisplay)) {
                return true;
            }
        }
        for (OptionRow<?> row : activeRows) {
            if (row.isOverlayOpen()) row.closeOverlay();
        }

        if (maxRowScroll > 0 && isOnRowScrollbar(event.x(), event.y())) {
            draggingRowScrollbar = true;
            updateRowScrollFromMouse(event.y());
            return true;
        }
        if (maxTabScroll > 0 && isOnTabScrollbar(event.x(), event.y())) {
            draggingTabScrollbar = true;
            updateTabScrollFromMouse(event.x(), event.y());
            return true;
        }
        if (headerMouseClicked(event, flag)) {
            return true;
        }
        if (event.x() >= tabAreaLeft && event.x() < tabAreaRight && event.y() >= tabAreaTop && event.y() < tabAreaBottom) {
            double adjX = compactTabs ? event.x() + tabScrollDisplay : event.x();
            double adjY = compactTabs ? event.y() : event.y() + tabScrollDisplay;
            for (TabButton tb : tabButtons) {
                if (tb.mouseClicked(new MouseButtonEvent(adjX, adjY, event.buttonInfo()), flag)) {
                    return true;
                }
            }
            return true;
        }
        if (event.x() >= rowAreaLeft && event.x() < rowAreaRight && event.y() >= rowAreaTop && event.y() < rowAreaBottom) {
            double adjY = event.y() + rowScrollDisplay;
            for (OptionRow<?> row : activeRows) {
                if (row.mouseClicked(new MouseButtonEvent(event.x(), adjY, event.buttonInfo()), flag)) {
                    setFocused(row);
                    if (event.button() == 0) setDragging(true);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    protected boolean headerMouseClicked(MouseButtonEvent event, boolean flag) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingRowScrollbar) {
            updateRowScrollFromMouse(event.y());
            return true;
        }
        if (draggingTabScrollbar) {
            updateTabScrollFromMouse(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingRowScrollbar) {
            draggingRowScrollbar = false;
            return true;
        }
        if (draggingTabScrollbar) {
            draggingTabScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        for (OptionRow<?> row : activeRows) {
            if (row.isOverlayOpen() && row.overlayMouseScrolled(mouseX, mouseY, delta, rowScrollDisplay)) {
                return true;
            }
        }
        if (mouseX >= tabAreaLeft && mouseX < tabAreaRight && mouseY >= tabAreaTop && mouseY < tabAreaBottom) {
            tabScrollOffset = Mth.clamp((int) (tabScrollOffset - delta * 20), 0, maxTabScroll);
            return true;
        }
        if (mouseX >= rowAreaLeft && mouseX < rowAreaRight && mouseY >= rowAreaTop && mouseY < rowAreaBottom) {
            rowScrollOffset = Mth.clamp((int) (rowScrollOffset - delta * 20), 0, maxRowScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOnRowScrollbar(double mouseX, double mouseY) {
        int trackX = rowAreaRight - 4;
        return mouseX >= trackX && mouseX < trackX + 3 && mouseY >= rowAreaTop && mouseY < rowAreaBottom;
    }

    private boolean isOnTabScrollbar(double mouseX, double mouseY) {
        if (compactTabs) {
            int trackY = tabAreaBottom - 4;
            return mouseY >= trackY && mouseY < trackY + 3 && mouseX >= tabAreaLeft && mouseX < tabAreaRight;
        }
        int trackX = tabAreaRight - 4;
        return mouseX >= trackX && mouseX < trackX + 3 && mouseY >= tabAreaTop && mouseY < tabAreaBottom;
    }

    private void updateRowScrollFromMouse(double mouseY) {
        int trackTop = rowAreaTop + 1;
        int trackBot = rowAreaBottom - 1;
        double t = Mth.clamp((mouseY - trackTop) / Math.max(1, trackBot - trackTop), 0.0, 1.0);
        rowScrollOffset = (int) (t * maxRowScroll);
    }

    private void updateTabScrollFromMouse(double mouseX, double mouseY) {
        if (compactTabs) {
            int trackLeft = tabAreaLeft + 1;
            int trackRight = tabAreaRight - 1;
            double t = Mth.clamp((mouseX - trackLeft) / Math.max(1, trackRight - trackLeft), 0.0, 1.0);
            tabScrollOffset = (int) (t * maxTabScroll);
            return;
        }
        int trackTop = tabAreaTop + 1;
        int trackBot = tabAreaBottom - 1;
        double t = Mth.clamp((mouseY - trackTop) / Math.max(1, trackBot - trackTop), 0.0, 1.0);
        tabScrollOffset = (int) (t * maxTabScroll);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
