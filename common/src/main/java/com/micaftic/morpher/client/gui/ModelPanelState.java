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
        DEVELOPER,
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

    /**
     * Developer-options read-only snapshot of every panel field, in a stable
     * order suitable for JSON serialisation. Never mutates anything.
     */
    java.util.Map<String, Object> devSnapshot() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("activeTab", activeTab.name());
        out.put("modelFilter", modelFilter.name());
        out.put("settingGroup", settingGroup.name());
        out.put("secondaryPanel", secondaryPanel.name());
        out.put("modelSearchText", modelSearchText);
        out.put("resourceSearchText", resourceSearchText);
        out.put("siteEditText", siteEditText);
        out.put("categoryEditText", categoryEditText);
        out.put("selectedModelId", selectedModelId);
        out.put("selectedTextureId", selectedTextureId);
        out.put("currentPath", currentPath);
        out.put("selectedResourceUrl", selectedResourceUrl);
        out.put("selectedTaskId", selectedTaskId);
        out.put("multiSelectMode", multiSelectMode);
        out.put("resourceMultiSelectMode", resourceMultiSelectMode);
        out.put("modelScroll", modelScroll);
        out.put("resourceScroll", resourceScroll);
        out.put("settingsScroll", settingsScroll);
        out.put("sitesScroll", sitesScroll);
        out.put("categoryScroll", categoryScroll);
        out.put("compactPreviewExpanded", compactPreviewExpanded);
        out.put("resourceLoaded", resourceLoaded);
        out.put("resourceLoading", resourceLoading);
        out.put("resourceRequestId", resourceRequestId);
        return out;
    }

    /**
     * Developer-options writeback. Validates every entry first and only then
     * applies the whole batch: a half-applied panel state would be worse than
     * none, so unknown keys and malformed values reject everything.
     *
     * secondaryPanel excludes CONFIRM: that panel needs a host callback and
     * would leave an empty modal behind.
     */
    boolean applyDevState(java.util.Map<String, String> in, java.util.List<String> errors) {
        for (java.util.Map.Entry<String, String> e : in.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            switch (k) {
                case "activeTab" -> requireEnum(Tab.class, v, k, errors);
                case "modelFilter" -> requireEnum(ModelFilter.class, v, k, errors);
                case "settingGroup" -> requireEnum(SettingGroup.class, v, k, errors);
                case "secondaryPanel" -> {
                    requireEnum(SecondaryPanel.class, v, k, errors);
                    if ("CONFIRM".equalsIgnoreCase(v)) {
                        errors.add(k + "=CONFIRM (requires host context)");
                    }
                }
                case "multiSelectMode", "resourceMultiSelectMode", "compactPreviewExpanded",
                     "resourceLoaded", "resourceLoading" -> requireBool(v, k, errors);
                case "modelScroll", "resourceScroll", "settingsScroll", "sitesScroll",
                     "categoryScroll", "resourceRequestId" -> requireNonNegativeInt(v, k, errors);
                case "modelSearchText", "resourceSearchText", "siteEditText", "categoryEditText",
                     "selectedModelId", "selectedTextureId", "currentPath", "selectedResourceUrl",
                     "selectedTaskId" -> {
                    // 自由文本：无需校验（应用时截断）
                }
                default -> errors.add(k + " (unknown field)");
            }
        }
        if (!errors.isEmpty()) {
            return false;
        }
        in.forEach((k, v) -> {
            switch (k) {
                case "activeTab" -> activeTab = Tab.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                case "modelFilter" -> modelFilter = ModelFilter.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                case "settingGroup" -> settingGroup = SettingGroup.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                case "secondaryPanel" -> secondaryPanel = SecondaryPanel.valueOf(v.toUpperCase(java.util.Locale.ROOT));
                case "modelSearchText" -> modelSearchText = clampDev(v);
                case "resourceSearchText" -> resourceSearchText = clampDev(v);
                case "siteEditText" -> siteEditText = clampDev(v);
                case "categoryEditText" -> categoryEditText = clampDev(v);
                case "selectedModelId" -> selectedModelId = clampDev(v);
                case "selectedTextureId" -> selectedTextureId = clampDev(v);
                case "currentPath" -> currentPath = clampDev(v);
                case "selectedResourceUrl" -> selectedResourceUrl = clampDev(v);
                case "selectedTaskId" -> selectedTaskId = clampDev(v);
                case "multiSelectMode" -> multiSelectMode = Boolean.parseBoolean(v);
                case "resourceMultiSelectMode" -> resourceMultiSelectMode = Boolean.parseBoolean(v);
                case "compactPreviewExpanded" -> compactPreviewExpanded = Boolean.parseBoolean(v);
                case "resourceLoaded" -> resourceLoaded = Boolean.parseBoolean(v);
                case "resourceLoading" -> resourceLoading = Boolean.parseBoolean(v);
                case "modelScroll" -> modelScroll = Integer.parseInt(v);
                case "resourceScroll" -> resourceScroll = Integer.parseInt(v);
                case "settingsScroll" -> settingsScroll = Integer.parseInt(v);
                case "sitesScroll" -> sitesScroll = Integer.parseInt(v);
                case "categoryScroll" -> categoryScroll = Integer.parseInt(v);
                case "resourceRequestId" -> resourceRequestId = Integer.parseInt(v);
                default -> {
                    // 前面已拒绝未知键，这里不可达
                }
            }
        });
        return true;
    }

    private static String clampDev(String v) {
        return v.length() > 256 ? v.substring(0, 256) : v;
    }

    private static boolean requireBool(String v, String key, java.util.List<String> errors) {
        if (!"true".equals(v) && !"false".equals(v)) {
            errors.add(key + "=" + v + " (expected true/false)");
            return false;
        }
        return true;
    }

    private static boolean requireNonNegativeInt(String v, String key, java.util.List<String> errors) {
        try {
            if (Integer.parseInt(v) < 0) {
                errors.add(key + "=" + v + " (expected >= 0)");
                return false;
            }
        } catch (NumberFormatException ex) {
            errors.add(key + "=" + v + " (expected integer)");
            return false;
        }
        return true;
    }

    private static <E extends Enum<E>> boolean requireEnum(Class<E> type, String v, String key,
                                                          java.util.List<String> errors) {
        try {
            Enum.valueOf(type, v.toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            errors.add(key + "=" + v + " (not a valid value)");
            return false;
        }
    }
}
