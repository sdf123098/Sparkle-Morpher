package com.micaftic.morpher.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.storage.LocalDisplayPreferencesStore;
import com.micaftic.morpher.core.storage.LocalModelDefaultsStore;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.model.LocalModelService;

/** One-time client preference migration before the legacy config is retired. */
public final class LocalModelBootstrap {
    private LocalModelBootstrap() {}
    public static void initialize() {
        try {
            var legacy = LegacyServerConfigMigration.readIfNeeded(ModelStoragePaths.folder().getParent(), ModelStoragePaths.folder());
            LocalModelService.initializeDefaults(legacy.defaults());
            LocalDisplayPreferences.initialize(legacy.display());
        } catch (Exception invalid) {
            YesSteveModel.LOGGER.warn("[SPM] Legacy client preferences could not be migrated; TOML retained", invalid);
            LocalModelService.initializeDefaults(LocalModelDefaultsStore.FALLBACK);
            LocalDisplayPreferences.initialize(LocalDisplayPreferencesStore.DEFAULT);
        }
        LocalModelService.reloadPacks();
    }
}
