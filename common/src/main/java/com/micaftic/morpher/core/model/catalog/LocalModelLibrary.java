package com.micaftic.morpher.core.model.catalog;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.peer.PeerAssetStore;
import com.micaftic.morpher.peer.PeerContentRoot;
import com.micaftic.morpher.peer.PeerModelIdentity;
import org.jetbrains.annotations.Nullable;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Typed sources alongside legacy selection flags. Catalog rescans only replace their own local namespace. */
public final class LocalModelLibrary {
    public record Source(String runtimeId, ModelSourceType type, @Nullable Path path, boolean restricted,
                         PeerModelIdentity peerIdentity, PeerAssetStore.Verified verifiedAsset,
                         @Nullable String contentSha256) {
        /** Memory-only Cloud sources carry verified bytes instead of a file path. */
        public boolean isMemory() { return path == null; }
    }
    private final Map<String, Source> sources = new ConcurrentHashMap<>();
    private final Set<String> restrictedDigests = new HashSet<>();
    private final Path provenance;
    private boolean loaded, provenanceFailed;
    public LocalModelLibrary(Path provenance) { this.provenance = provenance; }
    public Map<String, Source> snapshot() { return Map.copyOf(sources); }
    public Source source(String runtimeId) { return sources.get(LocalModelCatalog.canonicalKey(runtimeId)); }
    public void refreshLocal(Map<String, LocalModelCatalog.Entry> entries) {
        sources.entrySet().removeIf(e -> e.getValue().type == ModelSourceType.LOCAL && !entries.containsKey(e.getKey()));
        entries.forEach((id, entry) -> sources.compute(id, (key, old) -> {
            if(old != null && old.type != ModelSourceType.LOCAL) return old;
            ModelSourceType type = CloudAssetIdentity.isRuntimeModelId(id) ? ModelSourceType.CLOUD : entry.sourceType;
            return new Source(id, type, entry.path, entry.auth || type != ModelSourceType.LOCAL, null, null, null);
        }));
    }
    public void imported(String id, Path path, ModelSourceType type) throws IOException {
        String key = LocalModelCatalog.canonicalKey(id);
        if(CloudAssetIdentity.isRuntimeModelId(key)) type = ModelSourceType.CLOUD;
        sources.put(key, new Source(key, type, path.toAbsolutePath().normalize(), type != ModelSourceType.LOCAL, null, null, null));
        if(type == ModelSourceType.CLOUD || type == ModelSourceType.LEGACY_SERVER) rememberRestricted(path);
    }
    /**
     * Registers verified Cloud display bytes that were never written to disk.
     * The content digest still joins the restricted table so a later copy of the same
     * bytes into the custom folder cannot be shared to Peer.
     */
    public void importedMemory(String id, String contentSha256) throws IOException {
        String key = LocalModelCatalog.canonicalKey(id);
        if(key == null) throw new IOException("Cloud memory source has no runtime id");
        String digest = contentDigest(contentSha256);
        sources.put(key, new Source(key, ModelSourceType.CLOUD, null, true, null, null, digest));
        rememberRestrictedDigest(digest);
    }
    public void peer(PeerModelIdentity identity, PeerAssetStore.Verified asset) throws IOException {
        if(!identity.contentRoot().equals(asset.manifest().contentRoot())) throw new IOException("Peer source identity mismatch");
        sources.put(identity.runtimeModelId(), new Source(identity.runtimeModelId(), ModelSourceType.PEER, asset.entrypoint(), true, identity, asset, null));
    }
    public void removePeer() { sources.entrySet().removeIf(e -> e.getValue().type == ModelSourceType.PEER); }
    public void remove(String id) { sources.remove(LocalModelCatalog.canonicalKey(id)); }
    public boolean isPeer(String id) { Source value = source(id); return value != null && value.type == ModelSourceType.PEER; }
    public boolean canShare(Source source, Path customRoot, boolean userApproved) throws IOException {
        if(!userApproved || source == null || source.path == null || source.type != ModelSourceType.LOCAL || source.restricted
                || CloudAssetIdentity.isRuntimeModelId(source.runtimeId)) return false;
        Path root = customRoot.toRealPath(), selected = source.path.toRealPath();
        if(!selected.startsWith(root)) return false;
        synchronized(this) { loadRestricted(); if(provenanceFailed) return false; }
        if(Files.isRegularFile(selected)) return canShareFile(selected);
        return Files.isDirectory(selected);
    }
    public boolean canShareFile(Path file) throws IOException {
        synchronized(this) { loadRestricted(); if(provenanceFailed) return false; }
        String hash = digest(file).sha256();
        if(isRestricted(hash)) return false;
        for(Source origin : sources.values()) if(origin.restricted && origin.type != ModelSourceType.PEER
                && origin.path != null && Files.isRegularFile(origin.path) && Files.size(origin.path) == Files.size(file)
                && digest(origin.path).sha256().equals(hash)) return false;
        return true;
    }
    private synchronized boolean isRestricted(String digest) { return provenanceFailed || restrictedDigests.contains(digest); }
    private synchronized void loadRestricted() throws IOException {
        if(loaded) return; loaded = true;
        try {
            if(Files.exists(provenance)) {
                if(Files.size(provenance) > 600_000) throw new IOException("Local provenance index exceeds budget");
                for(String line : Files.readAllLines(provenance)) { new PeerContentRoot(line); restrictedDigests.add(line); }
            }
        } catch(IOException | IllegalArgumentException e) { provenanceFailed = true; throw new IOException("Local provenance index is unavailable", e); }
    }
    private void rememberRestricted(Path path) throws IOException {
        rememberRestrictedDigest(digest(path).sha256());
    }
    private void rememberRestrictedDigest(String digest) throws IOException {
        synchronized(this) {
            loadRestricted(); if(!restrictedDigests.add(digest)) return;
            if(restrictedDigests.size() > 8192) { provenanceFailed = true; throw new IOException("Local provenance index is full"); }
            Files.createDirectories(provenance.toAbsolutePath().getParent());
            Path staged = Files.createTempFile(provenance.toAbsolutePath().getParent(), "origins-", ".tmp");
            try { Files.write(staged, new TreeSet<>(restrictedDigests)); Files.move(staged, provenance, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch(IOException failure) { provenanceFailed = true; throw failure; }
            finally { Files.deleteIfExists(staged); }
        }
    }
    private static String contentDigest(String value) throws IOException {
        String digest = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if(digest == null || !digest.matches("[0-9a-f]{64}")) throw new IOException("Cloud memory source has no SHA-256 digest");
        return digest;
    }
    private static PeerContentRoot digest(Path path) throws IOException {
        var digest = PeerContentRoot.newDigest(); try(var input = Files.newInputStream(path)) { byte[] buffer = new byte[64 * 1024]; int n; while((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        return new PeerContentRoot(HexFormat.of().formatHex(digest.digest()));
    }
}
