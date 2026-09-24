package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudGeneratedAccountStoreTest {
    @Test
    void generatedCredentialsSurviveRestartAndCannotBeOverwritten() throws Exception {
        var path = Files.createTempDirectory("spm-cloud-account").resolve("account.json");
        var store = new CloudGeneratedAccountStore(path);
        assertTrue(store.load().isEmpty());

        var account = CloudGeneratedAccountStore.generate();
        assertTrue(account.accountId().matches("[A-Za-z0-9][A-Za-z0-9._-]*"));
        assertTrue(account.password().length() >= 32);
        store.save(account);

        assertEquals(account, new CloudGeneratedAccountStore(path).load().orElseThrow());
        assertThrows(java.io.IOException.class, () -> store.save(CloudGeneratedAccountStore.generate()));
        assertEquals(account, store.load().orElseThrow());
        assertFalse(Files.readString(path).contains("access_token"));
    }

    @Test
    void malformedCredentialsAreNotSilentlyReplaced() throws Exception {
        var path = Files.createTempDirectory("spm-cloud-account").resolve("account.json");
        Files.writeString(path, "broken");
        var store = new CloudGeneratedAccountStore(path);
        assertThrows(java.io.IOException.class, store::load);
        assertThrows(java.io.IOException.class, () -> store.save(CloudGeneratedAccountStore.generate()));
    }
}
