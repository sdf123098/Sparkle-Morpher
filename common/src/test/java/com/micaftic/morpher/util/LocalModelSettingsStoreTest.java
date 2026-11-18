package com.micaftic.morpher.util;

import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.render.BoneIds;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalModelSettingsStoreTest {
    @TempDir Path directory;

    @Test
    void switchingModelsRestoresTheirOwnTextureWithoutDroppingClothing() {
        Path file = directory.resolve("settings.json");
        int dress = BoneIds.id("dress");
        Int2FloatOpenHashMap changes = new Int2FloatOpenHashMap();
        changes.put(dress, 2f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        LocalModelSettingsStore.saveTexture(file, "model/a", "red");
        LocalModelSettingsStore.saveTexture(file, "model/b", "blue");

        assertEquals("red", LocalModelSettingsStore.texture(file, "model/a"));
        assertEquals("blue", LocalModelSettingsStore.texture(file, "model/b"));
        RoamingStruct returned = new RoamingStruct(42, null);
        LocalModelSettingsStore.restore(file, "model/a", returned);
        assertEquals(2f, returned.getProperty(dress));
    }

    @Test
    void restoredClothingWinsOverQueuedPlayerInitDefaults() {
        Path file = directory.resolve("settings.json");
        int dress = BoneIds.id("dress");
        Int2FloatOpenHashMap changes = new Int2FloatOpenHashMap();
        changes.put(dress, 2f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        RoamingStruct returned = new RoamingStruct(42, null);
        var queued = new ArrayList<Runnable>();
        // PLAYER_INIT initializes default roaming values before PlayerCapability restores preferences.
        queued.add(() -> returned.putProperty(dress, 0f));
        queued.add(LocalModelSettingsStore.restoreTask(file, "model/a", returned));
        for (Runnable task : queued) task.run();

        assertEquals(2f, returned.getProperty(dress));
    }

    @Test
    void switchingBeforeTheNextTickSavesPendingOutfitToItsOriginalModel() {
        Path file = directory.resolve("settings.json");
        int dress = BoneIds.id("dress");
        RoamingStruct leaving = new RoamingStruct(42, null);
        leaving.putProperty(dress, 3f);
        // The selected model may already be B when the render state for A is reset.
        String leavingModelId = "model/a";
        LocalModelSettingsStore.savePending(file, leavingModelId, leaving);

        RoamingStruct returnedA = new RoamingStruct(42, null);
        LocalModelSettingsStore.restore(file, "model/a", returnedA);
        RoamingStruct selectedB = new RoamingStruct(43, null);
        LocalModelSettingsStore.restore(file, "model/b", selectedB);
        assertEquals(3f, returnedA.getProperty(dress));
        assertEquals(0f, selectedB.getProperty(dress));
        assertFalse(leaving.hasPendingChanges());
        LocalModelSettingsStore.savePending(file, leavingModelId, leaving);
        LocalModelSettingsStore.restore(file, "model/a", returnedA);
        assertEquals(3f, returnedA.getProperty(dress));
    }

    @Test
    void doesNotQueueRestorationWithoutValidSavedRoaming() throws Exception {
        Path file = directory.resolve("settings.json");
        RoamingStruct target = new RoamingStruct(42, null);
        assertNull(LocalModelSettingsStore.restoreTask(file, "model/a", target));
        LocalModelSettingsStore.saveTexture(file, "model/a", "red");
        assertNull(LocalModelSettingsStore.restoreTask(file, "model/a", target));
        Files.writeString(file, "{\"models\":{\"model/a\":{\"\":2,\"invalid\":{},\"nonfinite\":\"NaN\"}}}");
        assertNull(LocalModelSettingsStore.restoreTask(file, "model/a", target));
    }

    @Test
    void queuedRestorationCapturesValuesBeforeLaterModelChanges() {
        Path file = directory.resolve("settings.json");
        int dress = BoneIds.id("dress");
        Int2FloatOpenHashMap changes = new Int2FloatOpenHashMap();
        changes.put(dress, 2f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        RoamingStruct target = new RoamingStruct(42, null);
        Runnable restore = LocalModelSettingsStore.restoreTask(file, "model/a", target);
        changes.put(dress, 5f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        restore.run();
        assertEquals(2f, target.getProperty(dress));
    }
}
