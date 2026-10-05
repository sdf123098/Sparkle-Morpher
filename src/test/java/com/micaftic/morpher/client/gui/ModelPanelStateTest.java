package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelPanelStateTest {
    @Test
    void newCommunityCloudTabStartsWithAllModels() {
        ModelPanelState state = new ModelPanelState();
        state.modelSource = ModelPanelState.ModelSource.COMMUNITY_CLOUD;
        state.restoreCloudTab("self-hosted");
        assertEquals("ALL", state.cloudView.name());
    }

    @Test
    void communityCloudHasManagementViewsAndUsesTheirCatalogScopes() {
        ModelPanelState state = new ModelPanelState();
        state.modelSource = ModelPanelState.ModelSource.COMMUNITY_CLOUD;
        assertEquals(java.util.List.of(ModelPanelState.CloudView.ALL, ModelPanelState.CloudView.RECENT,
                ModelPanelState.CloudView.FAVORITES, ModelPanelState.CloudView.MINE, ModelPanelState.CloudView.PUBLIC),
                state.cloudViews());
        state.cloudView = ModelPanelState.CloudView.MINE;
        assertEquals("mine", state.cloudCatalogScope());
        state.cloudView = ModelPanelState.CloudView.ALL;
        assertEquals("accessible", state.cloudCatalogScope());
        assertFalse(state.cloudSearchRequired());
        state.cloudView = ModelPanelState.CloudView.PUBLIC;
        assertEquals("public", state.cloudCatalogScope());
        assertTrue(state.cloudSearchRequired());
        state.modelSource = ModelPanelState.ModelSource.SPM_CLOUD;
        assertFalse(state.cloudViews().contains(ModelPanelState.CloudView.ALL));
        assertTrue(state.cloudSearchRequired());
        state.cloudSearchText = " fox ";
        assertFalse(state.cloudSearchRequired());
    }

    @Test
    void communityVisibilityChangesRequireOwnershipOutsideMyModels() {
        ModelPanelState state = new ModelPanelState();
        state.modelSource = ModelPanelState.ModelSource.COMMUNITY_CLOUD;
        for (var view : state.cloudViews()) {
            state.cloudView = view;
            assertTrue(state.canChangeCloudVisibility(true));
            assertEquals(view == ModelPanelState.CloudView.MINE, state.canChangeCloudVisibility(false));
        }
        state.modelSource = ModelPanelState.ModelSource.LOCAL;
        assertFalse(state.canChangeCloudVisibility(true));
    }

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
