package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPanelStateTest {
    @Test
    void modelFilterSeparatesServerAvailableAndLocalOnlyModels() {
        assertTrue(Arrays.asList(ModelPanelState.ModelFilter.values())
                .contains(ModelPanelState.ModelFilter.SERVER_AVAILABLE));
        assertTrue(Arrays.asList(ModelPanelState.ModelFilter.values())
                .contains(ModelPanelState.ModelFilter.LOCAL_ONLY));
        assertTrue(ModelPanelState.ModelFilter.SERVER_AVAILABLE.matchesAvailability(false));
        assertTrue(ModelPanelState.ModelFilter.LOCAL_ONLY.matchesAvailability(true));
    }

    @Test
    void cloudTabsKeepSearchAndViewIndependent() {
        ModelPanelState state = new ModelPanelState();
        state.selectedCloudInstanceId = "official";
        state.cloudScroll = 3;
        state.modelScroll = 7;
        state.cloudSearchText = "fox";
        state.cloudView = ModelPanelState.CloudView.MINE;
        state.saveCloudTab();

        state.selectedCloudInstanceId = "community";
        state.restoreCloudTab("community");
        assertEquals(0, state.cloudScroll);
        state.cloudScroll = 5;
        state.cloudSearchText = "cat";
        state.cloudView = ModelPanelState.CloudView.PUBLIC;
        state.saveCloudTab();

        state.selectedCloudInstanceId = "official";
        state.restoreCloudTab("official");
        assertEquals(3, state.cloudScroll);
        assertEquals(7, state.modelScroll);
        assertEquals("fox", state.cloudSearchText);
        assertEquals(ModelPanelState.CloudView.MINE, state.cloudView);
        state.restoreCloudTab("community");
        assertEquals(5, state.cloudScroll);
        assertEquals("cat", state.cloudSearchText);
        assertEquals(ModelPanelState.CloudView.PUBLIC, state.cloudView);
        state.pickerStyle = ModelPickerLayout.Style.LIST;
        state.restoreCloudTab("official");
        assertEquals(0, state.cloudScroll, "mode changes must discard saved page units");
    }
}
