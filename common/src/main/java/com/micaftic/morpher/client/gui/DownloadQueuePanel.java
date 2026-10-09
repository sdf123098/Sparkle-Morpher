package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.resource.download.DownloadQueue;

import java.util.ArrayList;
import java.util.List;

/** Immutable, read-only view of the RESOURCE tab's queue area. */
public final class DownloadQueuePanel {
    private static final int FINISHED_ROW_LIMIT = 8;

    private DownloadQueuePanel() {
    }

    public static View snapshot(DownloadQueue.Snapshot source) {
        List<Row> rows = new ArrayList<>();
        for (DownloadQueue.TaskSnapshot task : source.unfinishedTasks()) rows.add(row(task));
        source.finishedTasks().stream().limit(FINISHED_ROW_LIMIT).map(DownloadQueuePanel::row).forEach(rows::add);
        return new View(rows, source.status(), source.statusColor());
    }

    private static Row row(DownloadQueue.TaskSnapshot task) {
        int color = switch (task.state()) {
            case DONE -> 0xFF4CAF50;
            case FAILED -> 0xFFD23232;
            case CANCELLED -> 0xFF8F8F8F;
            default -> 0xFFE05252;
        };
        return new Row(task.name(), task.progress(), color);
    }

    public record Row(String name, float progress, int color) {
    }

    public record View(List<Row> rows, net.minecraft.network.chat.Component status,
                       net.minecraft.ChatFormatting statusColor) {
        public View {
            rows = List.copyOf(rows);
        }

        /** Matches the original clipped row loop: 20px row, 24px step, 22px bottom fit. */
        public int visibleRowCount(int firstRowY, int bottomExclusive, int rowStep) {
            if (rowStep <= 0) return 0;
            int room = bottomExclusive - firstRowY - 22;
            if (room < 0) return 0;
            return Math.min(rows.size(), room / rowStep + 1);
        }
    }
}
