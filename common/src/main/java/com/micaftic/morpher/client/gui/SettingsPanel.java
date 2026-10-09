package com.micaftic.morpher.client.gui;

import java.util.Objects;

/** Owns settings-tab presentation state while leaving widgets and config actions to the Screen. */
final class SettingsPanel {
    private final ModelPanelState state;

    SettingsPanel(ModelPanelState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    ModelPanelState.SettingGroup selectedGroup() {
        return state.settingGroup;
    }

    void selectGroup(ModelPanelState.SettingGroup group) {
        state.settingGroup = Objects.requireNonNull(group, "group");
        state.settingsScroll = 0;
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
