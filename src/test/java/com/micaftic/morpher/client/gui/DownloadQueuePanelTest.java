package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.resource.download.DownloadQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadQueuePanelTest {
    @Test
    void keepsAllUnfinishedAndAtMostEightFinishedRowsInOrder() {
        List<DownloadQueue.TaskSnapshot> unfinished = List.of(task("active", DownloadQueue.TaskState.DOWNLOADING));
        List<DownloadQueue.TaskSnapshot> finished = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> task("done-" + i, DownloadQueue.TaskState.DONE)).toList();
        DownloadQueue.Snapshot source = new DownloadQueue.Snapshot(null, unfinished, finished, 1, 10, 0,
                Component.literal("working"), ChatFormatting.GREEN);

        DownloadQueuePanel.View view = DownloadQueuePanel.snapshot(source);

        assertEquals(9, view.rows().size());
        assertEquals("active", view.rows().get(0).name());
        assertEquals("done-7", view.rows().get(8).name());
        assertEquals(0xFF4CAF50, view.rows().get(1).color());
        assertEquals(0xFFE05252, view.rows().get(0).color());
    }

    @Test
    void clipsRowsAndHandlesShortViewports() {
        List<DownloadQueue.TaskSnapshot> rows = List.of(
                task("a", DownloadQueue.TaskState.DOWNLOADING),
                task("b", DownloadQueue.TaskState.FAILED),
                task("c", DownloadQueue.TaskState.CANCELLED));
        DownloadQueuePanel.View view = DownloadQueuePanel.snapshot(new DownloadQueue.Snapshot(null, rows, List.of(),
                3, 0, 0, Component.empty(), ChatFormatting.GRAY));

        assertEquals(3, view.visibleRowCount(100, 170, 24));
        assertEquals(0, view.visibleRowCount(100, 121, 24));
        assertEquals(0xFFD23232, view.rows().get(1).color());
        assertEquals(0xFF8F8F8F, view.rows().get(2).color());
    }

    @Test
    void emptySnapshotProducesNoRowsAndViewCannotBeMutated() {
        DownloadQueuePanel.View view = DownloadQueuePanel.snapshot(new DownloadQueue.Snapshot(null,
                List.of(), List.of(), 0, 0, 0, Component.empty(), ChatFormatting.GRAY));

        assertEquals(0, view.rows().size());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> view.rows().add(new DownloadQueuePanel.Row("unexpected", 0, 0)));
    }

    private static DownloadQueue.TaskSnapshot task(String name, DownloadQueue.TaskState state) {
        return new DownloadQueue.TaskSnapshot(name, name + ".zip", state, 0.5f, Component.empty());
    }
}
