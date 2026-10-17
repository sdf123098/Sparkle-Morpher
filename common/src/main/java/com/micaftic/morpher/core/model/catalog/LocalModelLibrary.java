package com.micaftic.morpher.core.model.catalog;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.peer.PeerAssetStore;
import com.micaftic.morpher.peer.PeerContentRoot;
import com.micaftic.morpher.peer.PeerModelIdentity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Typed sources alongside legacy selection flags. Catalog rescans only replace their own local namespace. */
public final class LocalModelLibrary {
    public record Source(String runtimeId, ModelSourceType type, Path path, boolean restricted,
                         PeerModelIdentity peerIdentity, PeerAssetStore.Verified verifiedAsset) {}
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
            return new Source(id, type, entry.path, entry.auth || type != ModelSourceType.LOCAL, null, null);
        }));
    }
    public void imported(String id, Path path, ModelSourceType type) throws IOException {
        String key = LocalModelCatalog.canonicalKey(id);
        if(CloudAssetIdentity.isRuntimeModelId(key)) type = ModelSourceType.CLOUD;
        sources.put(key, new Source(key, type, path.toAbsolutePath().normalize(), type != ModelSourceType.LOCAL, null, null));
        if(type == ModelSourceType.CLOUD || type == ModelSourceType.LEGACY_SERVER) rememberRestricted(path);
    }
    public void peer(PeerModelIdentity identity, PeerAssetStore.Verified asset) throws IOException {
        if(!identity.contentRoot().equals(asset.manifest().contentRoot())) throw new IOException("Peer source identity mismatch");
        sources.put(identity.runtimeModelId(), new Source(identity.runtimeModelId(), ModelSourceType.PEER, asset.entrypoint(), true, identity, asset));
    }
    public void removePeer() { sources.entrySet().removeIf(e -> e.getValue().type == ModelSourceType.PEER); }
    public void remove(String id) { sources.remove(LocalModelCatalog.canonicalKey(id)); }
    public boolean isPeer(String id) { Source value = source(id); return value != null && value.type == ModelSourceType.PEER; }
    public boolean canShare(Source source, Path customRoot, boolean userApproved) throws IOException {
        if(!userApproved || source == null || source.type != ModelSourceType.LOCAL || source.restricted
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
                && Files.isRegularFile(origin.path) && Files.size(origin.path) == Files.size(file)
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
        PeerContentRoot root = digest(path);
        synchronized(this) {
            loadRestricted(); if(!restrictedDigests.add(root.sha256())) return;
            if(restrictedDigests.size() > 8192) { provenanceFailed = true; throw new IOException("Local provenance index is full"); }
            Files.createDirectories(provenance.toAbsolutePath().getParent());
            Path staged = Files.createTempFile(provenance.toAbsolutePath().getParent(), "origins-", ".tmp");
            try { Files.write(staged, new TreeSet<>(restrictedDigests)); Files.move(staged, provenance, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch(IOException failure) { provenanceFailed = true; throw failure; }
            finally { Files.deleteIfExists(staged); }
        }
    }
    private static PeerContentRoot digest(Path path) throws IOException {
        var digest = PeerContentRoot.newDigest(); try(var input = Files.newInputStream(path)) { byte[] buffer = new byte[64 * 1024]; int n; while((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        return new PeerContentRoot(HexFormat.of().formatHex(digest.digest()));
    }
}
