package com.micaftic.morpher.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.storage.LocalDisplayPreferencesStore;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;

/** Immutable snapshots may be read by model workers; only explicit client edits write preferences. */
public final class LocalDisplayPreferences {
    private static volatile LocalDisplayPreferencesStore.Preferences current=LocalDisplayPreferencesStore.DEFAULT;
    private static Path file;
    private static boolean writable;
    private LocalDisplayPreferences(){}
    public static synchronized void initialize(LocalDisplayPreferencesStore.Preferences legacy){
        file=ModelStoragePaths.folder().resolve("local-display-preferences.json");writable=false;
        try{current=LocalDisplayPreferencesStore.loadOrSeed(file,legacy);writable=true;}
        catch(IOException invalid){YesSteveModel.LOGGER.warn("[SPM] Local display preferences retained after read failure",invalid);}
    }
    public static LocalDisplayPreferencesStore.Preferences snapshot(){return current;}
    public static synchronized void soundMode(int value)throws IOException{save(new LocalDisplayPreferencesStore.Preferences(current.hiddenModels(),value));}
    public static synchronized void hide(String modelId)throws IOException{
        var hidden=new HashSet<>(current.hiddenModels());hidden.add(modelId);save(new LocalDisplayPreferencesStore.Preferences(hidden,current.soundMode()));
    }
    public static synchronized void restoreHidden()throws IOException{save(new LocalDisplayPreferencesStore.Preferences(java.util.Set.of(),current.soundMode()));}
    private static void save(LocalDisplayPreferencesStore.Preferences next)throws IOException{
        if(!writable||file==null)throw new IOException("Local display preferences require repair before editing");
        LocalDisplayPreferencesStore.save(file,next);current=next;
    }
}
