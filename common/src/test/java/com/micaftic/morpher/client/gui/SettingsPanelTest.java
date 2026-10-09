package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPanelTest {
    @Test
    void selectingGroupResetsScrollAndLayoutClampsPersistedState() {
        ModelPanelState state = new ModelPanelState();
        SettingsPanel panel = new SettingsPanel(state);
        panel.scrollBy(12);

        panel.selectGroup(ModelPanelState.SettingGroup.RENDERING);

        assertEquals(ModelPanelState.SettingGroup.RENDERING, panel.selectedGroup());
        assertEquals(0, panel.scroll());
        panel.scrollBy(20);
        SettingsPanelLayout.Window window = panel.layout(10, 110, 40, 20, 8);
        assertEquals(5, window.maxScroll());
        assertEquals(5, panel.scroll());
    }

    @Test
    void scrollNeverBecomesNegativeAndRowsRemainBounded() {
        SettingsPanel panel = new SettingsPanel(new ModelPanelState());
        panel.scrollBy(-10);
        SettingsPanelLayout.Window window = panel.layout(0, 1, 20, 22, 0);

        assertEquals(0, panel.scroll());
        assertEquals(0, window.endRowExclusive());
    }
}
