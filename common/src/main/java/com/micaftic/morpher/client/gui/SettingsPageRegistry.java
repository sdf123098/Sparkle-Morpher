package com.micaftic.morpher.client.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Stable page IDs and grouped navigation. Adding a page never divides a fixed horizontal tab strip. */
final class SettingsPageRegistry {
    enum Kind { OPTIONS, NETWORK, CLOUD, SERVER, RESOURCES, PEER_LAN, APPEARANCE, PLANNED }
    record Page(String id, String categoryKey, String titleKey, String descriptionKey,
                Kind kind, ModelPanelState.SettingGroup group) {
        Page {
            Objects.requireNonNull(id); Objects.requireNonNull(categoryKey); Objects.requireNonNull(titleKey);
            Objects.requireNonNull(descriptionKey); Objects.requireNonNull(kind);
            if (id.isBlank() || (kind == Kind.OPTIONS && group == null)) throw new IllegalArgumentException("Invalid settings page");
        }
    }
    private static final LinkedHashMap<String, Page> PAGES = new LinkedHashMap<>();
    static {
        for (var group : ModelPanelState.SettingGroup.values()) {
            if (group == ModelPanelState.SettingGroup.MISC) continue;
            String id = group.name().toLowerCase(java.util.Locale.ROOT);
            register(new Page(id, "gui.sparkle_morpher.settings.category.settings",
                    "gui.sparkle_morpher.model_panel.setting_group." + id,
                    "gui.sparkle_morpher.settings.description." + id, Kind.OPTIONS, group));
        }
        network("appearance", Kind.APPEARANCE);
        network("network", Kind.NETWORK);
        network("cloud", Kind.CLOUD);
        network("resources", Kind.RESOURCES);
        network("peer", Kind.PEER_LAN);
    }
    private SettingsPageRegistry() {}
    private static void network(String id, Kind kind) {
        register(new Page(id, "gui.sparkle_morpher.settings.category.settings",
                "gui.sparkle_morpher.settings.page." + id,
                "gui.sparkle_morpher.settings.description." + id, kind, null));
    }
    static synchronized void register(Page page) {
        if (PAGES.putIfAbsent(page.id(), page) != null) throw new IllegalArgumentException("Duplicate settings page");
    }
    static synchronized List<Page> pages() {
        var order=List.of("general","appearance","rendering","performance","cache","cloud","network","peer","resources","developer");
        return PAGES.values().stream().sorted(java.util.Comparator.comparingInt(page->{int i=order.indexOf(page.id());return i<0?order.size():i;})).toList();
    }
    static synchronized Page page(String id) {
        String current = id == null ? "general" : switch (id) {
            case "peer_lan", "peer_public", "peer_tunnel" -> "peer";
            case "server" -> "network";
            case "misc" -> "developer";
            default -> id;
        };
        return PAGES.getOrDefault(current, PAGES.get("general"));
    }
}
