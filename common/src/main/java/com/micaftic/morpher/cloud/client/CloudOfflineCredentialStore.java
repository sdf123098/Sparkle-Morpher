package com.micaftic.morpher.cloud.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import com.google.gson.*;
import com.micaftic.morpher.cloud.CloudInstanceConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Private credentials, scoped to the selected Cloud instance, origin and authenticated account. */
public final class CloudOfflineCredentialStore {
    private final Path file;
    public CloudOfflineCredentialStore(Path file) { this.file=file.toAbsolutePath().normalize(); }
    public record Scope(String instanceId, URI origin, String accountId) {
        public Scope {
            var config=CloudInstanceConfig.v1(instanceId,origin);
            instanceId=config.instanceId();
            String host=config.origin().getHost();
            if(host==null||accountId==null||!accountId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))throw new IllegalArgumentException("Invalid offline credential scope");
            int port=config.origin().getPort();
            origin=URI.create("https://"+host.toLowerCase(Locale.ROOT)+(port==-1||port==443?"":":"+port));
        }
    }
    public record Credential(String offlineKey, boolean enabled) {
        public Credential {
            if(offlineKey==null||!offlineKey.matches("spm-offline-v1\\.cok_[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid offline key format");
        }
        public String keyId() { return offlineKey.split("\\.")[1]; }
        @Override public String toString() { return "OfflineCredential[REDACTED]"; }
    }
    /** Persist before enrollment so a lost HTTP response can be retried without creating another device. */
    public synchronized Credential loadOrCreate(Scope scope) throws IOException {
        var existing=load(scope);if(existing.isPresent())return existing.get();
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        var credential=new Credential("spm-offline-v1.cok_"+UUID.randomUUID().toString().replace("-","")+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(secret),true);
        save(scope,credential);return credential;
    }
    public synchronized Optional<Credential> load(Scope scope) throws IOException {
        for(JsonElement entry:read()) {
            JsonObject row=entry.getAsJsonObject();
            if(scope.equals(scope(row)))return Optional.of(new Credential(row.get("offline_key").getAsString(),row.get("enabled").getAsBoolean()));
        }
        return Optional.empty();
    }
    public synchronized void save(Scope scope, Credential credential) throws IOException {
        Objects.requireNonNull(credential);
        JsonArray rows=without(read(),scope);JsonObject row=new JsonObject();
        row.addProperty("instance_id",scope.instanceId());row.addProperty("origin",scope.origin().toString());row.addProperty("account_id",scope.accountId());
        row.addProperty("offline_key",credential.offlineKey());row.addProperty("enabled",credential.enabled());rows.add(row);write(rows);
    }
    public synchronized void remove(Scope scope) throws IOException { write(without(read(),scope)); }

    private JsonArray read() throws IOException {
        if(!Files.exists(file))return new JsonArray();
        if(Files.size(file)>1024*1024)throw new IOException("Offline credential file is too large");
        try {
            JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException();
            JsonArray rows=root.getAsJsonArray("entries");
            if(rows==null||rows.size()>1024)throw new IllegalArgumentException();
            Set<Scope> seen=new HashSet<>();
            for(var entry:rows){var row=entry.getAsJsonObject();if(!seen.add(scope(row)))throw new IllegalArgumentException();new Credential(row.get("offline_key").getAsString(),row.get("enabled").getAsBoolean());}
            return rows;
        }catch(RuntimeException error){throw new IOException("Invalid offline credential file; existing contents were preserved");}
    }
    private static Scope scope(JsonObject row){return new Scope(row.get("instance_id").getAsString(),URI.create(row.get("origin").getAsString()),row.get("account_id").getAsString());}
    private static JsonArray without(JsonArray rows,Scope scope){JsonArray next=new JsonArray();for(var entry:rows)if(!scope.equals(scope(entry.getAsJsonObject())))next.add(entry.deepCopy());return next;}
    private void write(JsonArray rows) throws IOException {
        Path parent=file.getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"offline-credentials-",".tmp");
        try {
            if(Files.getFileAttributeView(temp,PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));
            else {
                var acl=Files.getFileAttributeView(temp,AclFileAttributeView.class);
                if(acl==null)throw new IOException("Cannot restrict offline credential file permissions");
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
            JsonObject root=new JsonObject();root.addProperty("version",1);root.add("entries",rows);
            Files.writeString(temp,root.toString(),StandardCharsets.UTF_8);
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
}
