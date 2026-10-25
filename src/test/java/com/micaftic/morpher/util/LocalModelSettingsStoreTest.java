package com.micaftic.morpher.util;

import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.geckolib3.core.molang.MolangParser;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.molang.storage.VariableStorage;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalModelSettingsStoreTest {
    @TempDir Path directory;

    @Test
    void switchingModelsRestoresTheirOwnTextureWithoutDroppingClothing() {
        Path file = directory.resolve("settings.json");
        int dress = StringPool.computeIfAbsent("dress");
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
    void restoredClothingWinsOverQueuedPlayerInitDefaults() throws Exception {
        Path file = directory.resolve("settings.json");
        int dress = StringPool.computeIfAbsent("dress");
        Int2FloatOpenHashMap changes = new Int2FloatOpenHashMap();
        changes.put(dress, 2f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        RoamingStruct returned = new RoamingStruct(42, null);
        VariableStorage storage = new VariableStorage();
        storage.setScoped(StringPool.computeIfAbsent("roaming"), returned);
        AnimationContext<?> context = new AnimationContext<>(null, null, null, null);
        context.setStorage(storage);
        var evaluator = ExpressionEvaluator.evaluator(context);
        var queued = new ArrayList<IValue>();
        // PLAYER_INIT is queued by LivingAnimatable before PlayerCapability restores preferences.
        queued.add(new MolangParser(Map.of()).parseExpressionUnsafe("v.roaming.dress=0;", false));
        queued.add(LocalModelSettingsStore.restoreExpression(file, "model/a", returned));
        for (IValue expression : queued) expression.evalUnsafe(evaluator);

        assertEquals(2f, returned.getProperty(dress));
    }

    @Test
    void switchingBeforeTheNextTickSavesPendingOutfitToItsOriginalModel() {
        Path file = directory.resolve("settings.json");
        int dress = StringPool.computeIfAbsent("dress");
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
        assertNull(LocalModelSettingsStore.restoreExpression(file, "model/a", target));
        LocalModelSettingsStore.saveTexture(file, "model/a", "red");
        assertNull(LocalModelSettingsStore.restoreExpression(file, "model/a", target));
        Files.writeString(file, "{\"models\":{\"model/a\":{\"\":2,\"invalid\":{},\"nonfinite\":\"NaN\"}}}");
        assertNull(LocalModelSettingsStore.restoreExpression(file, "model/a", target));
    }

    @Test
    void queuedRestorationCapturesValuesBeforeLaterModelChanges() {
        Path file = directory.resolve("settings.json");
        int dress = StringPool.computeIfAbsent("dress");
        Int2FloatOpenHashMap changes = new Int2FloatOpenHashMap();
        changes.put(dress, 2f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        RoamingStruct target = new RoamingStruct(42, null);
        IValue restore = LocalModelSettingsStore.restoreExpression(file, "model/a", target);
        changes.put(dress, 5f);
        LocalModelSettingsStore.save(file, "model/a", changes);
        restore.evalUnsafe(null);
        assertEquals(2f, target.getProperty(dress));
    }
}
