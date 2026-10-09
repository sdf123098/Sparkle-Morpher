package com.micaftic.morpher.client.upload.picker;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilePickerCoordinatorTest {

    @AfterEach
    void clearPickerState() {
        FilePickerCoordinator.cancelPicking();
    }

    @Test
    void cancellingPickerDiscardsQueuedSelectionAndError() {
        FilePickerCoordinator.complete(new FilePickerCoordinator.PickedFile("picked.ysm", new byte[]{1, 2, 3}));
        FilePickerCoordinator.setError(Component.literal("late picker failure"));

        FilePickerCoordinator.cancelPicking();

        assertFalse(FilePickerCoordinator.isPicking());
        assertNull(FilePickerCoordinator.pollCompleted());
        assertEquals("", FilePickerCoordinator.consumeLastError().getString());
    }
}
