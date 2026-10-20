package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class CloudOfflineCredentialStoreTest {
    @TempDir Path directory;
    private static final String KEY="spm-offline-v1.cok_"+"a".repeat(32)+"."+"b".repeat(43);
    private CloudOfflineCredentialStore.Scope scope(String instance,String origin,String account) {return new CloudOfflineCredentialStore.Scope(instance,URI.create(origin),account);}
    @Test void automaticallyCreatedCredentialsAreDurableAndRetriesReuseTheSameDevice() throws Exception {
        var file=directory.resolve("automatic.json");var store=new CloudOfflineCredentialStore(file);var account=scope("one","https://cloud.test","a");
        var first=store.loadOrCreate(account);assertTrue(first.enabled());
        assertEquals(first,new CloudOfflineCredentialStore(file).loadOrCreate(account));
        assertNotEquals(first,store.loadOrCreate(scope("one","https://cloud.test","b")));
    }
    @Test void secretsRemainIsolatedByInstanceOriginAndAccountAndSurviveRestart() throws Exception {
        var file=directory.resolve("offline.json");var store=new CloudOfflineCredentialStore(file);
        var a=scope("one","https://cloud.test","a");var credential=new CloudOfflineCredentialStore.Credential(KEY,true);
        store.save(a,credential);
        assertEquals(credential,new CloudOfflineCredentialStore(file).load(a).orElseThrow());
        for(var other:new CloudOfflineCredentialStore.Scope[]{scope("two","https://cloud.test","a"),scope("one","https://other.test","a"),scope("one","https://cloud.test","b")})assertTrue(store.load(other).isEmpty());
        assertEquals(credential,store.load(scope("one","https://CLOUD.test:443/","a")).orElseThrow());
        store.remove(a);assertTrue(store.load(a).isEmpty());assertFalse(Files.readString(file).contains(KEY));
    }
    @Test void malformedFilesAreNotOverwrittenAndSecretsNeverAppearInDescriptions() throws Exception {
        var file=directory.resolve("offline.json");Files.writeString(file,"broken");var store=new CloudOfflineCredentialStore(file);
        var a=scope("one","https://cloud.test","a");
        assertThrows(java.io.IOException.class,()->store.load(a));
        assertThrows(java.io.IOException.class,()->store.save(a,new CloudOfflineCredentialStore.Credential(KEY,true)));
        assertEquals("broken",Files.readString(file));
        assertFalse(new CloudOfflineCredentialStore.Credential(KEY,true).toString().contains(KEY));
    }
}
