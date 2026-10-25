package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStationSelectionTest {
    private static final ModelRepoEntry A = new ModelRepoEntry("Alice", "https://example.invalid/a.ysm", "a.ysm", 1, "");
    private static final ModelRepoEntry B = new ModelRepoEntry("Bob", "https://example.invalid/b.ysm", "b.ysm", 1, "");

    @Test void noSelectionDoesNotDownloadTheCatalogue() throws Exception {
        var f = fixture();
        assertEquals(0, f.controller.enqueueSelectedResources());
        assertTrue(f.queued.isEmpty());
    }

    @Test void singleSelectionDownloadsOnlyThatModel() throws Exception {
        var f = fixture();
        f.controller.clickResource(A);
        assertEquals(1, f.controller.enqueueSelectedResources());
        assertEquals(List.of(A), f.queued);
    }

    @Test void emptyMultiselectDoesNotDownloadTheCatalogue() throws Exception {
        var f = fixture();
        f.controller.toggleResourceMultiSelect();
        assertEquals(0, f.controller.enqueueSelectedResources());
        assertTrue(f.queued.isEmpty());
    }

    @Test void multiselectDownloadsOnlyCheckedModels() throws Exception {
        var f = fixture();
        f.controller.toggleResourceMultiSelect();
        f.controller.clickResource(B);
        assertEquals(1, f.controller.enqueueSelectedResources());
        assertEquals(List.of(B), f.queued);
    }

    @Test void deselectingTheLastModelDoesNotBecomeSelectAll() throws Exception {
        var f = fixture();
        f.controller.toggleResourceMultiSelect();
        f.controller.clickResource(B);
        f.controller.clickResource(B);
        assertEquals(0, f.controller.enqueueSelectedResources());
        assertTrue(f.queued.isEmpty());
    }

    @Test void searchingDoesNotImplicitlySelectMatchingModels() throws Exception {
        var f = fixture();
        f.state.resourceSearchText = "Alice";
        assertEquals(0, f.controller.enqueueSelectedResources());
        assertTrue(f.queued.isEmpty());
    }

    @Test void previewFocusDoesNotChangeCheckedDownloadsOrApplyAPlayerModel() throws Exception {
        var f = fixture();
        f.controller.toggleResourceMultiSelect();
        f.controller.clickResource(A);
        f.controller.focusResource(B);
        assertEquals(B, f.controller.selectedResource());
        assertEquals(1, f.controller.enqueueSelectedResources());
        assertEquals(List.of(A), f.queued);
        assertTrue(f.state.selectedModelId.isBlank());
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture() throws Exception {
        var state = new ModelPanelState();
        var queued = new ArrayList<ModelRepoEntry>();
        var controller = new ModernPlayerModelScreenController(state, new ModernPlayerModelScreenController.Host() {
            @Override public void postStatus(Component component, ChatFormatting color) { }
            @Override public void postResourceStatus(Component component, ChatFormatting color) { }
            @Override public void requestRerender() { }
        }, null, entries -> { queued.addAll(entries); return entries.size(); });
        var entries = ModernPlayerModelScreenController.class.getDeclaredField("resourceEntries");
        entries.setAccessible(true);
        ((List<ModelRepoEntry>) entries.get(controller)).addAll(List.of(A, B));
        return new Fixture(state, controller, queued);
    }

    private record Fixture(ModelPanelState state, ModernPlayerModelScreenController controller, List<ModelRepoEntry> queued) { }
}
