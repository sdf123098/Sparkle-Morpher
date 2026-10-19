package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.core.storage.ModelStoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UiAppearancePreferencesTest {
    @TempDir Path root;

    @Test void everyPageObservesLiveChangesAndTheySurviveReload(){
        ModelStoragePaths.withRoot(root);
        try{
            var settings=UiAppearancePreferences.current();
            var childPage=UiAppearancePreferences.current();
            assertSame(settings,childPage);
            settings.opacity=0;settings.blur=0;
            assertEquals(0,childPage.backgroundColor()>>>24);
            assertEquals(0,childPage.blurRadius());
            assertEquals(0,childPage.blurTint()>>>24);
            settings.opacity=100;settings.blur=16;
            assertEquals(255,childPage.backgroundColor()>>>24);
            assertEquals(16,childPage.blurRadius());
            assertTrue(settings.save());
            var reloaded=new UiAppearancePreferences(root.resolve("ui.properties"));
            assertEquals(childPage.backgroundColor(),reloaded.backgroundColor());
            assertEquals(childPage.blurRadius(),reloaded.blurRadius());
        }finally{ModelStoragePaths.withRoot(null);}
    }
}
