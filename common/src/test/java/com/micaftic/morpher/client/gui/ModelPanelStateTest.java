package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
