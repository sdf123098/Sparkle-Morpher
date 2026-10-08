package com.micaftic.morpher.core.storage;

import java.nio.file.*;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalDisplayPreferencesStoreTest{
    @TempDir Path directory;
    @Test void legacy_hidden_models_and_audio_choices_seed_once_without_overriding_client_edits()throws Exception{
        Path file=directory.resolve("display.json");var legacy=new LocalDisplayPreferencesStore.Preferences(Set.of("legacy/昔涟"),1);
        assertEquals(legacy,LocalDisplayPreferencesStore.loadOrSeed(file,legacy));assertFalse(legacy.visible("legacy/昔涟"));assertTrue(legacy.visible("default"));
        var edited=new LocalDisplayPreferencesStore.Preferences(Set.of("local/auth"),2);LocalDisplayPreferencesStore.save(file,edited);
        assertEquals(edited,LocalDisplayPreferencesStore.loadOrSeed(file,legacy));assertThrows(UnsupportedOperationException.class,()->edited.hiddenModels().clear());
    }
    @Test void rejects_duplicate_unknown_coerced_invalid_utf8_and_oversized_settings_without_overwriting()throws Exception{
        Path file=directory.resolve("display.json");String good="{\"schema_version\":1,\"sound_mode\":0,\"hidden_models\":[]}";
        for(String bad:new String[]{good.replace("\"sound_mode\":0","\"sound_mode\":0,\"sound_mode\":2"),good.replace("\"sound_mode\":0","\"sound_mode\":\"0\""),good.replace("[]","[\"a\",\"a\"]"),good.replace("[]","[] ,\"server_permission\":true"),good+" {}",good.replace(":1",":2")," ".repeat(262145)}){
            Files.writeString(file,bad);assertThrows(IOException.class,()->LocalDisplayPreferencesStore.loadOrSeed(file,LocalDisplayPreferencesStore.DEFAULT));assertEquals(bad,Files.readString(file));
        }
        byte[] malformed={(byte)0xc3,0x28};Files.write(file,malformed);assertThrows(IOException.class,()->LocalDisplayPreferencesStore.read(file));assertArrayEquals(malformed,Files.readAllBytes(file));
    }
    @Test void short_sound_policy_uses_encoded_size_and_duration_and_none_always_rejects(){
        var shortOnly=new LocalDisplayPreferencesStore.Preferences(Set.of(),1);
        assertTrue(shortOnly.acceptsSound(1000,48000,48000));assertFalse(shortOnly.acceptsSound(40960,48000,48000));
        assertFalse(shortOnly.acceptsSound(1000,192000,48000));assertFalse(shortOnly.acceptsSound(1000,0,0));
        assertFalse(new LocalDisplayPreferencesStore.Preferences(Set.of(),2).acceptsSound(1000,48000,48000));
        assertTrue(LocalDisplayPreferencesStore.DEFAULT.acceptsSound(100000,1000000,48000));
    }
}
