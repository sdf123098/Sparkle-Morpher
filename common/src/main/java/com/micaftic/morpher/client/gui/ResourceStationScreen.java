package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.client.gui.button.IconButton;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager.TaskSnapshot;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager.TaskState;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.mixin.client.ScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ResourceStationScreen extends Screen {

    private enum TabState {
        BROWSE, SITES, DOWNLOADS
    }

    private static final int ENTRY_HEIGHT = 28;
    private static final int PREVIEW_SIZE = 24;
    private static final int TAB_HEIGHT = 20;
    private static final int TOOLBAR_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 18;
    private static final int URL_ROW_HEIGHT = 14;
    private static final int ICON_SIZE = 18;
    private static final ExecutorService RESOURCE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SM Resource Station");
        thread.setDaemon(true);
        return thread;
    });

    private final PlayerModelScreen parentScreen;
    private ResourceStationConfig.State config;
    private final List<ModelRepoEntry> entries = new ArrayList<>();
    private final Map<String, Identifier> previewTextures = new HashMap<>();
    private final Set<String> loadingPreviews = new HashSet<>();
    private EditBox urlBox;
    private EditBox searchBox;
    private int guiLeft;
    private int guiTop;
    private int panelWidth;
    private int panelHeight;
    private int entriesPerPage = 1;
    private int page;
    private int sourceIndex;
    private boolean loading;
    private boolean active;
    private boolean queuedListRefresh;
    private int listRequestId;
    private final ConcurrentLinkedQueue<ListResult> pendingListResults = new ConcurrentLinkedQueue<>();
    private SortMode sortMode = SortMode.NAME;
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;

    private TabState activeTab = TabState.BROWSE;
    private int sitesListScroll = 0;
    private int hoveredSiteIndex = -1;

    private String preservedUrl;
    private String preservedSearch;
    private boolean urlFocused;
    private boolean searchFocused;

    public ResourceStationScreen(PlayerModelScreen parentScreen) {
        super(Component.translatable("gui.sparkle_morpher.resource_station.title"));
        this.parentScreen = parentScreen;
        this.config = ResourceStationConfig.load();
        this.sourceIndex = Math.max(0, this.config.urls().indexOf(this.config.selectedUrl()));
        this.preservedUrl = this.config.selectedUrl();
        this.preservedSearch = "";
    }

    @Override
    public void init() {
        this.active = true;
        this.panelWidth = Math.min(460, this.width - 20);
        this.panelHeight = Math.min(250, this.height - 20);
        this.guiLeft = (this.width - this.panelWidth) / 2;
        this.guiTop = (this.height - this.panelHeight) / 2;
        this.entriesPerPage = Math.max(1, (this.panelHeight - TAB_HEIGHT - 2 - TOOLBAR_HEIGHT - FOOTER_HEIGHT) / ENTRY_HEIGHT);

        this.preservedUrl = this.urlBox != null ? this.urlBox.getValue() : (this.preservedUrl != null ? this.preservedUrl : this.config.selectedUrl());
        this.preservedSearch = this.searchBox != null ? this.searchBox.getValue() : (this.preservedSearch != null ? this.preservedSearch : "");
        this.urlFocused = this.urlBox != null && this.urlBox.isFocused();
        this.searchFocused = this.searchBox != null && this.searchBox.isFocused();

        clearWidgets();

        switch (this.activeTab) {
            case BROWSE -> initBrowse();
            case SITES -> initSites();
            case DOWNLOADS -> initDownloads();
        }

        if (this.entries.isEmpty() && !this.loading && this.status.getString().isEmpty()) {
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.empty_hint");
            this.statusColor = ChatFormatting.GRAY;
        }
    }

    private void initBrowse() {
        int toolbarY = this.guiTop + TAB_HEIGHT + 2;
        int x = this.guiLeft + 4;

        addRenderableWidget(new IconButton(x, toolbarY + 3, ICON_SIZE, ICON_SIZE, 0, 32, button -> Minecraft.getInstance().setScreen(this.parentScreen))
                .setTooltipText("gui.sparkle_morpher.model.return"));
        x += ICON_SIZE + 2;

        addRenderableWidget(new IconButton(x, toolbarY + 3, ICON_SIZE, ICON_SIZE, 0, 64, button -> refresh())
                .setTooltipText("gui.sparkle_morpher.resource_station.refresh"));
        x += ICON_SIZE + 2;

        addRenderableWidget(new IconButton(x, toolbarY + 3, ICON_SIZE, ICON_SIZE, 16, 64, button -> enqueueAllVisible())
                .setTooltipText("gui.sparkle_morpher.resource_station.queue_all"));
        x += ICON_SIZE + 4;

        int searchBoxWidth = Math.max(60, this.panelWidth - x - this.guiLeft - ICON_SIZE * 2 - 8 - 60);
        this.searchBox = new EditBox(this.font, x, toolbarY + 4, searchBoxWidth, 14, Component.translatable("gui.sparkle_morpher.resource_station.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setValue(this.preservedSearch);
        this.searchBox.setTextColor(0xFFF3F3E0);
        this.searchBox.setFocused(this.searchFocused);
        addWidget(this.searchBox);
        if (this.searchFocused) {
            setFocused(this.searchBox);
        }
        x += searchBoxWidth + 4;

        IconButton sortBtn = new IconButton(x, toolbarY + 3, ICON_SIZE, ICON_SIZE, 32, 64, button -> cycleSort());
        sortBtn.setTooltipLines(List.of(Component.translatable("gui.sparkle_morpher.resource_station.sort_mode", this.sortMode.label())));
        addRenderableWidget(sortBtn);
        x += ICON_SIZE + 2;

        boolean isMainland = this.config.mainlandChinaMode();
        int modeIconU = isMainland ? 48 : 128;
        String modeTooltipKey = isMainland ? "gui.sparkle_morpher.resource_station.mode.mainland" : "gui.sparkle_morpher.resource_station.mode.native";
        IconButton modeBtn = new IconButton(x, toolbarY + 3, ICON_SIZE, ICON_SIZE, modeIconU, 64, button -> toggleMode());
        modeBtn.setTooltipText(modeTooltipKey);
        addRenderableWidget(modeBtn);

        List<ModelRepoEntry> visible = filteredEntries();
        clampPage(visible.size());
        int start = this.page * this.entriesPerPage;
        for (int i = 0; i < this.entriesPerPage; i++) {
            int index = start + i;
            if (index >= visible.size()) {
                break;
            }
            ModelRepoEntry entry = visible.get(index);
            int y = entryY(i);
            boolean queued = ResourceDownloadManager.isQueued(entry);
            FlatColorButton download = new FlatColorButton(this.guiLeft + this.panelWidth - 56, y + 2, 46, ENTRY_HEIGHT - 4,
                    queued
                            ? Component.translatable("gui.sparkle_morpher.resource_station.queued_short")
                            : Component.literal("\u2193"),
                    button -> enqueue(entry));
            download.active = !queued;
            addRenderableWidget(download);
            ensurePreview(entry);
        }

        int footerY = this.guiTop + this.panelHeight - FOOTER_HEIGHT;
        addRenderableWidget(new IconButton(this.guiLeft + 4, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 0, 32, button -> {
            if (this.page > 0) {
                this.page--;
                init();
            }
        }).setTooltipText("gui.sparkle_morpher.resource_station.prev_page"));
        addRenderableWidget(new IconButton(this.guiLeft + 4 + ICON_SIZE + 2, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 160, 64, button -> {
            if (this.page < maxPage(filteredEntries().size())) {
                this.page++;
                init();
            }
        }).setTooltipText("gui.sparkle_morpher.resource_station.next_page"));
    }

    private void initSites() {
        int contentTop = this.guiTop + TAB_HEIGHT + 2;
        int urlY = contentTop + 4;
        int actionY = contentTop + 22;

        this.urlBox = new EditBox(this.font, this.guiLeft + 30, urlY, this.panelWidth - 40, 14, Component.translatable("gui.sparkle_morpher.resource_station.url"));
        this.urlBox.setMaxLength(2048);
        this.urlBox.setValue(this.preservedUrl);
        this.urlBox.setTextColor(0xFFF3F3E0);
        this.urlBox.setFocused(this.urlFocused);
        addWidget(this.urlBox);
        if (this.urlFocused) {
            setFocused(this.urlBox);
        }

        int x = this.guiLeft + 4;

        IconButton prevBtn = new IconButton(x, actionY + 1, ICON_SIZE, ICON_SIZE, 0, 32, button -> switchSource(-1));
        prevBtn.setTooltipText("gui.sparkle_morpher.resource_station.prev_source");
        prevBtn.active = this.config.urls().size() > 1;
        addRenderableWidget(prevBtn);
        x += ICON_SIZE + 2;

        IconButton nextBtn = new IconButton(x, actionY + 1, ICON_SIZE, ICON_SIZE, 160, 64, button -> switchSource(1));
        nextBtn.setTooltipText("gui.sparkle_morpher.resource_station.next_source");
        nextBtn.active = this.config.urls().size() > 1;
        addRenderableWidget(nextBtn);
        x += ICON_SIZE + 2;

        IconButton saveBtn = new IconButton(x, actionY + 1, ICON_SIZE, ICON_SIZE, 64, 64, button -> saveUrl());
        saveBtn.setTooltipText("gui.sparkle_morpher.resource_station.save");
        addRenderableWidget(saveBtn);
        x += ICON_SIZE + 2;

        IconButton deleteBtn = new IconButton(x, actionY + 1, ICON_SIZE, ICON_SIZE, 80, 64, button -> deleteSource());
        deleteBtn.setTooltipText("gui.sparkle_morpher.resource_station.delete_source");
        addRenderableWidget(deleteBtn);

        int footerY = this.guiTop + this.panelHeight - FOOTER_HEIGHT;
        addRenderableWidget(new IconButton(this.guiLeft + 4, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 0, 32, button -> Minecraft.getInstance().setScreen(this.parentScreen))
                .setTooltipText("gui.sparkle_morpher.model.return"));
    }

    private void initDownloads() {
        int footerY = this.guiTop + this.panelHeight - FOOTER_HEIGHT;

        addRenderableWidget(new IconButton(this.guiLeft + 4, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 0, 32, button -> Minecraft.getInstance().setScreen(this.parentScreen))
                .setTooltipText("gui.sparkle_morpher.model.return"));
        addRenderableWidget(new IconButton(this.guiLeft + 4 + ICON_SIZE + 2, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 96, 64, button -> {
            ResourceDownloadManager.clearFinished();
            init();
        }).setTooltipText("gui.sparkle_morpher.resource_station.clear_finished"));
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        IconButton cancelBtn = new IconButton(this.guiLeft + 4 + ICON_SIZE * 2 + 4, footerY + 1, ICON_SIZE, FOOTER_HEIGHT - 2, 112, 64, button -> {
            ModelUploadSession.failCurrent(Component.translatable("gui.sparkle_morpher.resource_station.cancelled"));
            init();
        });
        cancelBtn.setTooltipText("gui.sparkle_morpher.resource_station.cancel");
        cancelBtn.active = snapshot.currentTask() != null;
        addRenderableWidget(cancelBtn);
    }

    @Override
    public void removed() {
        this.active = false;
        this.listRequestId++;
        this.pendingListResults.clear();
    }

    @Override
    public void tick() {
        super.tick();
        applyPendingListResult();
        ResourceDownloadManager.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + this.panelWidth, this.guiTop + this.panelHeight, 0xE0202020, 0xE0202020);
        renderTabBar(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop + TAB_HEIGHT, this.guiLeft + this.panelWidth, this.guiTop + TAB_HEIGHT + 2, 0xFFB15D2B, 0xFFB15D2B);
        renderContent(extractor, mouseX, mouseY, partialTick);
        renderFooter(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(r -> r instanceof FlatColorButton).forEach(r -> ((FlatColorButton) r).renderTooltip(extractor, this, mouseX, mouseY));
    }

    private void renderTabBar(GuiGraphicsExtractor extractor) {
        int tabWidth = this.panelWidth / 3;
        TabState[] tabs = TabState.values();
        for (int i = 0; i < tabs.length; i++) {
            TabState tab = tabs[i];
            int tabX = this.guiLeft + i * tabWidth;
            boolean selected = this.activeTab == tab;
            int bgColor = selected ? 0xFFB15D2B : 0x40404040;
            int textColor = selected ? 0xFFFFFFFF : 0xFF9A9A9A;
            extractor.fillGradient(tabX, this.guiTop, tabX + tabWidth, this.guiTop + TAB_HEIGHT, bgColor, bgColor);

            String symbol = switch (tab) {
                case BROWSE -> "\u2261";
                case SITES -> "\u25CE";
                case DOWNLOADS -> "\u2193";
            };
            Component label = switch (tab) {
                case BROWSE -> Component.translatable("gui.sparkle_morpher.resource_station.tab.browse");
                case SITES -> Component.translatable("gui.sparkle_morpher.resource_station.tab.sites");
                case DOWNLOADS -> Component.translatable("gui.sparkle_morpher.resource_station.tab.downloads");
            };
            int symbolWidth = this.font.width(symbol);
            int labelWidth = this.font.width(label);
            int groupWidth = symbolWidth + 2 + labelWidth;
            int groupX = tabX + (tabWidth - groupWidth) / 2;
            extractor.text(this.font, Component.literal(symbol), groupX, this.guiTop + (TAB_HEIGHT - 8) / 2, textColor, false);
            extractor.text(this.font, label, groupX + symbolWidth + 2, this.guiTop + (TAB_HEIGHT - 8) / 2, textColor, false);
        }
    }

    private void renderContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        switch (this.activeTab) {
            case BROWSE -> renderBrowseContent(extractor, mouseX, mouseY, partialTick);
            case SITES -> renderSitesContent(extractor, mouseX, mouseY, partialTick);
            case DOWNLOADS -> renderDownloadsContent(extractor);
        }
    }

    private void renderBrowseContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        if (this.searchBox != null) {
            this.searchBox.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
        }

        int total = filteredEntries().size();
        String pageText = (Math.min(this.page, maxPage(total)) + 1) + "/" + (maxPage(total) + 1) + " (" + total + ")";
        extractor.text(this.font, Component.literal(pageText), this.guiLeft + this.panelWidth - 10 - this.font.width(pageText), this.guiTop + TAB_HEIGHT + 6, 0xFF9A9A9A, false);

        renderEntries(extractor);
    }

    private void renderEntries(GuiGraphicsExtractor extractor) {
        int listTop = entryY(0);
        int listBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT - 2;
        int pw = this.panelWidth;
        extractor.fillGradient(this.guiLeft + 4, listTop - 2, this.guiLeft + pw - 4, listBottom, 0x66000000, 0x66000000);
        List<ModelRepoEntry> visible = filteredEntries();
        if (this.loading) {
            drawCenteredText(extractor, Component.translatable("gui.sparkle_morpher.resource_station.loading"), this.guiLeft + pw / 2, listTop + 42, 0xFFE8D9B8);
            return;
        }
        if (visible.isEmpty()) {
            drawCenteredText(extractor, Component.translatable("gui.sparkle_morpher.resource_station.no_results"), this.guiLeft + pw / 2, listTop + 42, 0xFF8F8F8F);
            return;
        }
        int start = this.page * this.entriesPerPage;
        for (int i = 0; i < this.entriesPerPage; i++) {
            int index = start + i;
            if (index >= visible.size()) {
                break;
            }
            ModelRepoEntry entry = visible.get(index);
            int y = entryY(i);
            int bg = (i & 1) == 0 ? 0x77313131 : 0x77262626;
            extractor.fillGradient(this.guiLeft + 6, y, this.guiLeft + pw - 6, y + ENTRY_HEIGHT - 2, bg, bg);
            Identifier preview = this.previewTextures.get(entry.url());
            if (preview != null) {
                extractor.blit(preview, this.guiLeft + 10, y + 2, this.guiLeft + 10 + PREVIEW_SIZE, y + 2 + PREVIEW_SIZE, 0f, 1f, 0f, 1f);
            } else {
                extractor.fillGradient(this.guiLeft + 10, y + 2, this.guiLeft + 10 + PREVIEW_SIZE, y + 2 + PREVIEW_SIZE, 0xAA101010, 0xAA101010);
            }
            int textX = this.guiLeft + 10 + PREVIEW_SIZE + 4;
            extractor.text(this.font, Component.literal(trim(entry.name(), pw - textX - 60)), textX, y + 2, 0xFFEDE1CC, false);
            String detail = detailLine(entry);
            extractor.text(this.font, Component.literal(trim(detail, pw - textX - 60)), textX, y + 14, 0xFF9A9A9A, false);
        }
    }

    private void renderSitesContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int contentTop = this.guiTop + TAB_HEIGHT + 2;

        extractor.text(this.font, Component.translatable("gui.sparkle_morpher.resource_station.url"), this.guiLeft + 6, contentTop + 6, 0xFFAFAFAF, false);
        if (this.urlBox != null) {
            this.urlBox.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
        }

        int listTop = contentTop + 42;
        int listBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT - 2;
        int pw = this.panelWidth;
        List<String> urls = this.config.urls();
        int maxVisible = Math.max(1, (listBottom - listTop) / URL_ROW_HEIGHT);

        this.hoveredSiteIndex = -1;
        if (mouseY >= listTop && mouseY < listBottom && mouseX >= this.guiLeft + 4 && mouseX < this.guiLeft + pw - 4) {
            int row = (int) ((mouseY - listTop) / URL_ROW_HEIGHT) + this.sitesListScroll;
            if (row >= 0 && row < urls.size()) {
                this.hoveredSiteIndex = row;
            }
        }

        extractor.fillGradient(this.guiLeft + 4, listTop - 2, this.guiLeft + pw - 4, listBottom, 0x66000000, 0x66000000);

        int visibleCount = Math.min(maxVisible, urls.size() - this.sitesListScroll);
        for (int i = 0; i < visibleCount; i++) {
            int index = i + this.sitesListScroll;
            String url = urls.get(index);
            int rowY = listTop + i * URL_ROW_HEIGHT;
            boolean isActive = url.equals(this.config.selectedUrl());
            boolean isHovered = index == this.hoveredSiteIndex;
            if (isActive) {
                extractor.fillGradient(this.guiLeft + 6, rowY, this.guiLeft + pw - 6, rowY + URL_ROW_HEIGHT - 1, 0x40B15D2B, 0x40B15D2B);
            } else if (isHovered) {
                extractor.fillGradient(this.guiLeft + 6, rowY, this.guiLeft + pw - 6, rowY + URL_ROW_HEIGHT - 1, 0x30303030, 0x30303030);
            }
            extractor.text(this.font, Component.literal(trim(url, pw - 30)), this.guiLeft + 10, rowY + 1, isActive ? 0xFFF3F3E0 : 0xFF9A9A9A, false);
            if (isActive) {
                extractor.text(this.font, Component.literal("\u25CF"), this.guiLeft + pw - 18, rowY + 1, 0xFFB15D2B, false);
            }
        }

        if (urls.isEmpty()) {
            drawCenteredText(extractor, Component.translatable("gui.sparkle_morpher.resource_station.no_urls"), this.guiLeft + pw / 2, listTop + 20, 0xFF8F8F8F);
        }
    }

    private void renderDownloadsContent(GuiGraphicsExtractor extractor) {
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        int contentTop = this.guiTop + TAB_HEIGHT + 2;
        int pw = this.panelWidth;
        int y = contentTop + 4;
        int statusColorValue = snapshot.statusColor().getColor() == null ? 0xFFBDBDBD : snapshot.statusColor().getColor();
        extractor.text(this.font, snapshot.status(), this.guiLeft + 6, y, statusColorValue, false);
        y += 14;

        List<TaskSnapshot> rows = new ArrayList<>();
        rows.addAll(snapshot.unfinishedTasks());
        rows.addAll(snapshot.finishedTasks().stream().limit(8).toList());
        if (rows.isEmpty()) {
            drawCenteredText(extractor, Component.translatable("gui.sparkle_morpher.resource_station.no_downloads"), this.guiLeft + pw / 2, contentTop + 68, 0xFF8F8F8F);
            return;
        }

        int listBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT - 2;
        int maxRows = Math.min((listBottom - y) / 16, rows.size());
        int nameWidth = Math.max(60, pw - 260);
        for (int i = 0; i < maxRows; i++) {
            TaskSnapshot row = rows.get(i);
            int rowY = y + i * 16;
            extractor.fillGradient(this.guiLeft + 6, rowY - 1, this.guiLeft + pw - 6, rowY + 14, i % 2 == 0 ? 0x66313131 : 0x66262626, i % 2 == 0 ? 0x66313131 : 0x66262626);
            extractor.text(this.font, Component.literal(trim(row.name(), nameWidth)), this.guiLeft + 10, rowY + 1, 0xFFEDE1CC, false);
            int stateX = this.guiLeft + 10 + nameWidth + 4;
            extractor.text(this.font, stateLabel(row.state()), stateX, rowY + 1, stateColor(row.state()), false);
            int barX = stateX + 30;
            int barWidth = Math.min(70, pw - (barX - this.guiLeft) - 90);
            int barY = rowY + 2;
            extractor.fillGradient(barX, barY, barX + barWidth, barY + 5, 0xAA101010, 0xAA101010);
            int fill = Math.max(0, Math.min(barWidth, (int) (row.progress() * barWidth)));
            extractor.fillGradient(barX, barY, barX + fill, barY + 5, 0xFFB15D2B, 0xFFB15D2B);
            int msgX = barX + barWidth + 4;
            int msgWidth = Math.max(40, pw - (msgX - this.guiLeft) - 10);
            extractor.text(this.font, Component.literal(trim(row.message().getString(), msgWidth)), msgX, rowY + 1, 0xFF9FA8A6, false);
        }
    }

    private void renderFooter(GuiGraphicsExtractor extractor) {
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        Component message = snapshot.status().getString().isBlank() ? this.status : snapshot.status();
        ChatFormatting color = snapshot.status().getString().isBlank() ? this.statusColor : snapshot.statusColor();
        int colorValue = color.getColor() == null ? 0xFFBDBDBD : color.getColor();
        int footerY = this.guiTop + this.panelHeight - FOOTER_HEIGHT;
        extractor.text(this.font, message, this.guiLeft + this.panelWidth - 6 - this.font.width(message), footerY + 4, colorValue, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (mouseY >= this.guiTop && mouseY < this.guiTop + TAB_HEIGHT) {
            int tabWidth = this.panelWidth / 3;
            for (int i = 0; i < 3; i++) {
                int tabX = this.guiLeft + i * tabWidth;
                if (mouseX >= tabX && mouseX < tabX + tabWidth) {
                    TabState newTab = TabState.values()[i];
                    if (this.activeTab != newTab) {
                        this.activeTab = newTab;
                        init();
                    }
                    return true;
                }
            }
        }

        if (this.activeTab == TabState.SITES) {
            int contentTop = this.guiTop + TAB_HEIGHT + 2;
            int listTop = contentTop + 42;
            int listBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT - 2;
            if (mouseY >= listTop && mouseY < listBottom && mouseX >= this.guiLeft + 4 && mouseX < this.guiLeft + this.panelWidth - 4) {
                int row = (int) ((mouseY - listTop) / URL_ROW_HEIGHT) + this.sitesListScroll;
                List<String> urls = this.config.urls();
                if (row >= 0 && row < urls.size()) {
                    String selected = urls.get(row);
                    if (!selected.equals(this.config.selectedUrl())) {
                        this.config = this.config.withUrlsAndSelected(urls, selected);
                        ResourceStationConfig.save(this.config);
                        this.sourceIndex = row;
                        this.preservedUrl = selected;
                        this.entries.clear();
                        this.previewTextures.clear();
                        this.loadingPreviews.clear();
                        this.pendingListResults.clear();
                        this.page = 0;
                        this.status = Component.translatable("gui.sparkle_morpher.resource_station.site_selected", row + 1, urls.size());
                        this.statusColor = ChatFormatting.GRAY;
                        init();
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.activeTab == TabState.SITES) {
            int contentTop = this.guiTop + TAB_HEIGHT + 2;
            int listTop = contentTop + 42;
            int listBottom = this.guiTop + this.panelHeight - FOOTER_HEIGHT - 2;
            if (mouseY >= listTop && mouseY < listBottom) {
                int maxVisible = Math.max(1, (listBottom - listTop) / URL_ROW_HEIGHT);
                int maxScroll = Math.max(0, this.config.urls().size() - maxVisible);
                this.sitesListScroll = (int) Math.max(0, Math.min(this.sitesListScroll - (int) scrollY, maxScroll));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.activeTab == TabState.BROWSE && this.searchBox != null) {
            String search = this.searchBox.getValue();
            boolean handled = this.searchBox.charTyped(event) || super.charTyped(event);
            if (!Objects.equals(search, this.searchBox.getValue())) {
                this.page = 0;
                init();
            }
            return handled;
        }
        if (this.activeTab == TabState.SITES && this.urlBox != null) {
            return this.urlBox.charTyped(event) || super.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.activeTab == TabState.BROWSE && this.searchBox != null) {
            String search = this.searchBox.getValue();
            boolean handled = this.searchBox.keyPressed(event) || super.keyPressed(event);
            if (!Objects.equals(search, this.searchBox.getValue())) {
                this.page = 0;
                init();
            }
            return handled;
        }
        if (this.activeTab == TabState.SITES && this.urlBox != null) {
            return this.urlBox.keyPressed(event) || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    private void refresh() {
        if (this.loading) {
            this.queuedListRefresh = true;
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.loading");
            this.statusColor = ChatFormatting.YELLOW;
            return;
        }
        startListRefresh();
    }

    private void startListRefresh() {
        ResourceStationConfig.State requestConfig = this.config;
        String requestUrl = requestConfig.selectedUrl();
        int requestId = ++this.listRequestId;
        this.loading = true;
        this.queuedListRefresh = false;
        this.entries.clear();
        this.previewTextures.clear();
        this.loadingPreviews.clear();
        this.page = 0;
        this.status = Component.translatable("gui.sparkle_morpher.resource_station.loading");
        this.statusColor = ChatFormatting.YELLOW;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.list(requestUrl, requestConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).whenComplete((result, error) -> this.pendingListResults.add(new ListResult(requestId, requestUrl, result, error)));
    }

    private void applyPendingListResult() {
        ListResult result;
        while ((result = this.pendingListResults.poll()) != null) {
            if (applyListResult(result)) {
                return;
            }
        }
    }

    private boolean applyListResult(ListResult result) {
        if (!this.active || result.requestId != this.listRequestId) {
            return false;
        }
        this.loading = false;
        if (!Objects.equals(this.config.selectedUrl(), result.sourceUrl) || this.queuedListRefresh) {
            startListRefresh();
            return true;
        }
        if (result.error != null) {
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.error", rootMessage(result.error));
            this.statusColor = ChatFormatting.RED;
        } else {
            this.entries.clear();
            this.previewTextures.clear();
            this.loadingPreviews.clear();
            this.entries.addAll(result.entries);
            sortEntries();
            this.page = 0;
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.loaded", result.entries.size());
            this.statusColor = ChatFormatting.GREEN;
        }
        init();
        return true;
    }

    private void saveUrl() {
        String url = this.urlBox == null ? this.config.selectedUrl() : this.urlBox.getValue().trim();
        if (url.isBlank()) {
            return;
        }
        ArrayList<String> urls = new ArrayList<>(this.config.urls());
        if (!urls.contains(url)) {
            urls.add(0, url);
        }
        this.sourceIndex = urls.indexOf(url);
        this.config = this.config.withUrlsAndSelected(urls, url);
        ResourceStationConfig.save(this.config);
        this.preservedUrl = url;
        this.status = Component.translatable("gui.sparkle_morpher.resource_station.saved");
        this.statusColor = ChatFormatting.GRAY;
    }

    private void switchSource(int delta) {
        saveUrl();
        List<String> urls = this.config.urls();
        if (urls.size() <= 1) {
            return;
        }
        this.sourceIndex = Math.floorMod(this.sourceIndex + delta, urls.size());
        String selected = urls.get(this.sourceIndex);
        this.config = this.config.withUrlsAndSelected(urls, selected);
        ResourceStationConfig.save(this.config);
        this.preservedUrl = selected;
        if (this.urlBox != null) {
            this.urlBox.setValue(selected);
        }
        this.entries.clear();
        this.previewTextures.clear();
        this.loadingPreviews.clear();
        this.pendingListResults.clear();
        this.page = 0;
        this.status = Component.translatable("gui.sparkle_morpher.resource_station.site_selected", this.sourceIndex + 1, urls.size());
        this.statusColor = ChatFormatting.GRAY;
        init();
    }

    private void deleteSource() {
        String url = this.urlBox == null ? this.config.selectedUrl() : this.urlBox.getValue().trim();
        if (url.isBlank()) {
            return;
        }
        ArrayList<String> urls = new ArrayList<>(this.config.urls());
        if (!urls.remove(url) || urls.isEmpty()) {
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.cannot_delete");
            this.statusColor = ChatFormatting.RED;
            return;
        }
        this.sourceIndex = Math.min(this.sourceIndex, urls.size() - 1);
        String newSelected = urls.get(this.sourceIndex);
        this.config = this.config.withUrlsAndSelected(urls, newSelected);
        ResourceStationConfig.save(this.config);
        this.preservedUrl = newSelected;
        if (this.urlBox != null) {
            this.urlBox.setValue(newSelected);
        }
        this.status = Component.translatable("gui.sparkle_morpher.resource_station.url_deleted");
        this.statusColor = ChatFormatting.GRAY;
        init();
    }

    private void toggleMode() {
        this.config = this.config.withMainlandChinaMode(!this.config.mainlandChinaMode());
        ResourceStationConfig.save(this.config);
        init();
    }

    private void cycleSort() {
        this.sortMode = this.sortMode.next();
        sortEntries();
        init();
    }

    private void sortEntries() {
        Comparator<ModelRepoEntry> comparator = switch (this.sortMode) {
            case SIZE -> Comparator.comparingLong(entry -> entry.size() < 0 ? Long.MAX_VALUE : entry.size());
            case AUTHOR -> Comparator.comparing(entry -> entry.author().toLowerCase(Locale.ROOT));
            case NAME -> Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT));
        };
        this.entries.sort(comparator.thenComparing(ModelRepoEntry::fileName));
    }

    private void enqueue(ModelRepoEntry entry) {
        if (ResourceDownloadManager.enqueue(entry, this.config)) {
            this.status = Component.translatable("gui.sparkle_morpher.resource_station.queued", entry.name());
            this.statusColor = ChatFormatting.YELLOW;
        }
        init();
    }

    private void enqueueAllVisible() {
        int added = ResourceDownloadManager.enqueueAll(filteredEntries(), this.config);
        this.status = Component.translatable("gui.sparkle_morpher.resource_station.queue_added", added);
        this.statusColor = added > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
        init();
    }

    private List<ModelRepoEntry> filteredEntries() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return new ArrayList<>(this.entries);
        }
        List<ModelRepoEntry> result = new ArrayList<>();
        for (ModelRepoEntry entry : this.entries) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.fileName().toLowerCase(Locale.ROOT).contains(query)
                    || entry.description().toLowerCase(Locale.ROOT).contains(query)
                    || entry.author().toLowerCase(Locale.ROOT).contains(query)
                    || entry.tags().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(entry);
            }
        }
        return result;
    }

    private void ensurePreview(ModelRepoEntry entry) {
        if (entry.previewUrl() == null || entry.previewUrl().isBlank() || this.previewTextures.containsKey(entry.url()) || !this.loadingPreviews.add(entry.url())) {
            return;
        }
        ResourceStationConfig.State requestConfig = this.config;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.downloadPreview(entry, requestConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).orTimeout(Math.max(10_000L, requestConfig.timeoutMs() * 2L), TimeUnit.MILLISECONDS).whenComplete((data, error) -> Minecraft.getInstance().execute(() -> {
            this.loadingPreviews.remove(entry.url());
            if (!this.active || error != null || data == null) {
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "resource_preview/" + sha1(entry.url()));
            OuterFileTexture texture = new OuterFileTexture(data);
            texture.doLoad();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            this.previewTextures.put(entry.url(), id);
        }));
    }

    private int entryY(int index) {
        return this.guiTop + TAB_HEIGHT + 2 + TOOLBAR_HEIGHT + index * ENTRY_HEIGHT;
    }

    private int maxPage(int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, (total - 1) / this.entriesPerPage);
    }

    private void clampPage(int total) {
        this.page = Math.max(0, Math.min(this.page, maxPage(total)));
    }

    private String detailLine(ModelRepoEntry entry) {
        List<String> parts = new ArrayList<>();
        parts.add(entry.fileName());
        if (entry.size() > 0) {
            parts.add(ModelUploadSession.formatBytes((int) Math.min(Integer.MAX_VALUE, entry.size())));
        }
        if (!entry.author().isBlank()) {
            parts.add(entry.author());
        }
        if (!entry.tags().isBlank()) {
            parts.add(entry.tags());
        }
        return String.join("  |  ", parts);
    }

    private String trim(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int keep = value.length();
        while (keep > 0 && this.font.width(value.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return value.substring(0, Math.max(0, keep)) + ellipsis;
    }

    private Component stateLabel(TaskState state) {
        return Component.translatable("gui.sparkle_morpher.resource_station.state." + state.name().toLowerCase(Locale.ROOT));
    }

    private int stateColor(TaskState state) {
        return switch (state) {
            case DONE -> ChatFormatting.GREEN.getColor();
            case FAILED -> ChatFormatting.RED.getColor();
            default -> ChatFormatting.YELLOW.getColor();
        };
    }

    private void drawCenteredText(GuiGraphicsExtractor extractor, Component text, int centerX, int y, int color) {
        int textWidth = this.font.width(text);
        extractor.text(this.font, text, centerX - textWidth / 2, y, color, false);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private enum SortMode {
        NAME("name"),
        SIZE("size"),
        AUTHOR("author");

        private final String key;

        SortMode(String key) {
            this.key = key;
        }

        private Component label() {
            return Component.translatable("gui.sparkle_morpher.resource_station.sort." + this.key);
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record ListResult(int requestId, String sourceUrl, List<ModelRepoEntry> entries, Throwable error) {
    }
}
