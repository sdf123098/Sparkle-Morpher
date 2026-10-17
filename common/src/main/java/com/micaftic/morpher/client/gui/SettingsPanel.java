package com.micaftic.morpher.client.gui;

import java.util.Objects;

/** Owns settings-tab presentation state while leaving widgets and config actions to the Screen. */
final class SettingsPanel {
    private final ModelPanelState state;

    SettingsPanel(ModelPanelState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    ModelPanelState.SettingGroup selectedGroup() {
        return selectedPage().group();
    }

    void selectGroup(ModelPanelState.SettingGroup group) {
        selectPage(Objects.requireNonNull(group, "group").name().toLowerCase(java.util.Locale.ROOT));
    }

    SettingsPageRegistry.Page selectedPage() {
        if (state.settingsPageId == null) {
            state.settingsPageId = state.settingGroup.name().toLowerCase(java.util.Locale.ROOT);
        }
        return SettingsPageRegistry.page(state.settingsPageId);
    }

    void selectPage(String id) {
        var previous = selectedPage();
        state.settingsPageScrolls.put(previous.id(), state.settingsScroll);
        var next = SettingsPageRegistry.page(id);
        state.settingsPageId = next.id();
        if (next.group() != null) state.settingGroup = next.group();
        state.settingsScroll = state.settingsPageScrolls.getOrDefault(next.id(), 0);
    }

    int navigationScroll(int rowCount, int visibleRows) {
        state.settingsNavigationScroll = Math.max(0, Math.min(state.settingsNavigationScroll,
                Math.max(0, rowCount - visibleRows)));
        return state.settingsNavigationScroll;
    }

    void scrollNavigationBy(int delta) {
        state.settingsNavigationScroll = Math.max(0, state.settingsNavigationScroll + delta);
    }

    int scroll() {
        return state.settingsScroll;
    }

    void scrollBy(int delta) {
        state.settingsScroll = Math.max(0, state.settingsScroll + delta);
    }

    SettingsPanelLayout.Window layout(int top, int bottom, int reservedHeight, int rowHeight, int rowCount) {
        SettingsPanelLayout.Window window = SettingsPanelLayout.window(
                top, bottom, reservedHeight, rowHeight, rowCount, state.settingsScroll);
        state.settingsScroll = window.scroll();
        return window;
    }
}
