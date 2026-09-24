package com.micaftic.morpher.cloud.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Locally saved credentials for a generated official Cloud account. Keep this file private. */
public final class CloudGeneratedAccountStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path file;

    public CloudGeneratedAccountStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Optional<Account> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported version");
            return Optional.of(new Account(root.get("account_id").getAsString(), root.get("password").getAsString()));
        } catch (RuntimeException failure) {
            throw new IOException("Invalid generated Cloud account file: " + file, failure);
        }
    }

    public void save(Account account) throws IOException {
        Objects.requireNonNull(account, "account");
        if (Files.exists(file)) throw new IOException("Generated Cloud account file already exists: " + file);
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "cloud-generated-account-", ".tmp");
        try {
            restrictPermissions(temp);
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("account_id", account.accountId());
            root.addProperty("password", account.password());
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void restrictPermissions(Path file) throws IOException {
        if (Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl != null) {
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                        .setPrincipal(acl.getOwner())
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
        }
    }

    public static Account generate() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return new Account("spm_" + UUID.randomUUID().toString().replace("-", ""),
                Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    public record Account(String accountId, String password) {
        public Account {
            if (accountId == null || !accountId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                throw new IllegalArgumentException("Invalid generated Cloud account ID");
            }
            if (password == null || password.length() < 8 || password.length() > 1024) {
                throw new IllegalArgumentException("Invalid generated Cloud account password");
            }
        }
    }
}
