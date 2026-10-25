package com.micaftic.morpher.client.gui.resource.download;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DownloadQueueCancellationTest {
    @BeforeEach @AfterEach void resetQueue() {
        synchronized (DownloadQueue.LOCK) {
            DownloadQueue.currentTask = null;
            DownloadQueue.downloadLoading = false;
            DownloadQueue.QUEUE.clear();
            DownloadQueue.HISTORY.clear();
        }
    }

    @Test void cancellingABlockedDownloadImmediatelyReleasesItsQueueSlot() {
        var active = task("active", config(1000));
        activate(active);
        DownloadQueue.cancelCurrent();
        assertNull(DownloadQueue.currentTask, "Cancelled HTTP must not occupy the queue until its read timeout");
        assertFalse(DownloadQueue.downloadLoading);
        assertEquals(DownloadQueue.TaskState.CANCELLED, active.state);
        assertEquals(List.of(active), DownloadQueue.HISTORY);
    }

    @Test void retryKeepsTheExactFailedAttemptConfiguration() {
        var original = task("same", config(1000));
        var newer = task("same", config(9000));
        original.state = newer.state = DownloadQueue.TaskState.FAILED;
        DownloadQueue.HISTORY.add(original);
        DownloadQueue.HISTORY.add(newer);
        activate(task("unrelated", config(1000))); // keep retry queued without HTTP
        assertTrue(DownloadQueue.retry(DownloadQueue.snapshot(original)));
        assertSame(original.config, DownloadQueue.QUEUE.getFirst().config);
    }

    @Test void cancelAllClearsActiveAndQueuedTasksBeforeLateCallbacksArrive() {
        var active = task("active", config(1000));
        var queued = task("queued", config(1000));
        activate(active);
        DownloadQueue.QUEUE.add(queued);
        AtomicInteger aborts = new AtomicInteger();
        active.cancellation.onCancel(aborts::incrementAndGet);
        assertEquals(2, DownloadQueue.cancelAll());
        assertNull(DownloadQueue.currentTask);
        assertTrue(DownloadQueue.QUEUE.isEmpty());
        assertFalse(DownloadQueue.downloadLoading);
        assertEquals(1, aborts.get());
        assertEquals(List.of(active, queued), DownloadQueue.HISTORY);
        assertTrue(DownloadQueue.HISTORY.stream().allMatch(task -> task.state == DownloadQueue.TaskState.CANCELLED));
        ResourceImportCoordinator.onDownloadFinished(active, new byte[]{1}, null);
        ResourceImportCoordinator.onLocalSaveFinished(active, "active", new byte[]{1}, null);
        ResourceImportCoordinator.onLocalImportFinished(active, "active", new byte[]{1}, null);
        assertNull(DownloadQueue.currentTask);
        assertEquals(2, DownloadQueue.HISTORY.size());
        assertEquals(0, DownloadQueue.cancelAll());
    }

    @Test void cancellingAQueuedRowLeavesTheActiveDownloadAlone() {
        var active = task("active", config(1000));
        var queued = task("queued", config(1000));
        activate(active);
        DownloadQueue.QUEUE.add(queued);
        assertTrue(DownloadQueue.cancel(DownloadQueue.snapshot(queued)));
        assertSame(active, DownloadQueue.currentTask);
        assertFalse(active.cancelRequested);
        assertTrue(DownloadQueue.QUEUE.isEmpty());
        assertEquals(DownloadQueue.TaskState.CANCELLED, queued.state);
    }

    @Test void staleRowCannotCancelANewAttemptForTheSameUrl() {
        var old = task("same", config(1000));
        activate(old);
        var oldRow = DownloadQueue.snapshot(old);
        DownloadQueue.cancelCurrent();
        var retry = task("same", config(1000));
        activate(retry);
        assertFalse(DownloadQueue.cancel(oldRow));
        assertSame(retry, DownloadQueue.currentTask);
        assertFalse(retry.cancelRequested);
        assertNotEquals(oldRow.taskId(), DownloadQueue.snapshot(retry).taskId());
    }

    @Test void successfulAndForgottenAttemptsCannotBeRetried() {
        var done = task("done", config(1000));
        done.state = DownloadQueue.TaskState.DONE;
        DownloadQueue.HISTORY.add(done);
        assertFalse(DownloadQueue.retry(DownloadQueue.snapshot(done)));
        var failed = task("failed", config(1000));
        failed.state = DownloadQueue.TaskState.FAILED;
        var row = DownloadQueue.snapshot(failed);
        assertFalse(DownloadQueue.retry(row));
    }

    @Test void cancelledSaveIsRejectedBeforeItTouchesModelStorage() {
        assertThrows(CancellationException.class, () -> ResourceImportCoordinator.saveDownloadedModel("model.ysm", "cancelled", new byte[]{1},
                () -> { throw new CancellationException(); }));
    }

    @Test void cancelAllDoesNotFailAGlobalUploadThatReplacedTheTasksOwnedSession() throws Exception {
        var constructor = ModelUploadSession.class.getDeclaredConstructor(String.class, String.class, java.nio.file.Path.class, boolean.class);
        constructor.setAccessible(true);
        var owned = constructor.newInstance("owned", "owned.ysm", java.nio.file.Path.of("owned.ysm"), false);
        var replacement = constructor.newInstance("other", "other.ysm", java.nio.file.Path.of("other.ysm"), false);
        var instance = ModelUploadSession.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object original = instance.get(null);
        try {
            instance.set(null, replacement);
            var active = task("owned", config(1000));
            activate(active);
            active.state = DownloadQueue.TaskState.UPLOADING;
            active.uploadSession = owned;
            DownloadQueue.cancelAll();
            assertSame(replacement, ModelUploadSession.getInstance());
            assertEquals(ModelUploadSession.State.STARTING, replacement.getState());
        } finally { instance.set(null, original); }
    }

    @Test void lateDownloadSaveAndImportCallbacksCannotMutateAnotherAttempt() {
        var old = task("old", config(1000));
        var current = task("current", config(1000));
        activate(current);
        ResourceImportCoordinator.onDownloadFinished(old, new byte[]{1}, null);
        ResourceImportCoordinator.onLocalSaveFinished(old, "old", new byte[]{1}, null);
        ResourceImportCoordinator.onLocalImportFinished(old, "old", new byte[]{1}, Component.literal("old failure"));
        assertSame(current, DownloadQueue.currentTask);
        assertTrue(DownloadQueue.downloadLoading);
        assertEquals(DownloadQueue.TaskState.DOWNLOADING, current.state);
        assertTrue(DownloadQueue.HISTORY.isEmpty());
    }

    static DownloadQueue.DownloadTask task(String id, ResourceStationConfig.State config) {
        return new DownloadQueue.DownloadTask(new ModelRepoEntry(id, "https://models.test/" + id, id + ".ysm", 10, ""), config);
    }

    static ResourceStationConfig.State config(int timeout) {
        return new ResourceStationConfig.State(List.of(), "", timeout, 1024, false, List.of());
    }

    static void activate(DownloadQueue.DownloadTask task) {
        DownloadQueue.currentTask = task;
        DownloadQueue.downloadLoading = true;
        task.state = DownloadQueue.TaskState.DOWNLOADING;
    }
}
