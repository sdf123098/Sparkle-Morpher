package com.micaftic.morpher.client.gui;

final class ModelPanelState {
    enum Tab {
        MODEL,
        RESOURCE,
        SETTINGS
    }

    enum ModelFilter {
        ALL,
        AUTH,
        STAR
    }

    enum SecondaryPanel {
        NONE,
        SITES,
        CATEGORIES,
        IMPORT,
        CONFIRM
    }

    enum SettingGroup {
        GENERAL,
        RENDERING,
        PERFORMANCE,
        CACHE,
        DEBUG,
        MISC
    }

    Tab activeTab = Tab.MODEL;
    ModelFilter modelFilter = ModelFilter.ALL;
    SettingGroup settingGroup = SettingGroup.GENERAL;
    SecondaryPanel secondaryPanel = SecondaryPanel.NONE;
    String modelSearchText = "";
    String resourceSearchText = "";
    String siteEditText = "";
    String categoryEditText = "";
    String selectedModelId = "";
    String selectedTextureId = "";
    String currentPath = "";
    String selectedResourceUrl = "";
    String selectedTaskId = "";
    boolean multiSelectMode;
    boolean resourceMultiSelectMode;
    int modelScroll;
    int resourceScroll;
    int settingsScroll;
    int sitesScroll;
    int categoryScroll;
    boolean compactPreviewExpanded;
    boolean resourceLoaded;
    boolean resourceLoading;
    int resourceRequestId;
    String status = "";
}
