package com.micaftic.morpher.peer;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Local provenance metadata, never serialized as a room manifest or inferred from legacy localOnly. */
public record ModelOriginRecord(SourceKind sourceKind, Path sourcePath, PeerContentRoot contentRoot,
        UUID owner, Permission permission, String importerProfile, String originalFormat,
        List<String> dependencies, boolean verified) {
    public enum SourceKind { CUSTOM, BUILTIN, AUTH, CLOUD, LEGACY_SERVER, PEER, UNKNOWN }
    public enum Permission { USER_APPROVED, RESTRICTED, ROOM_RELAY_ONLY, UNKNOWN }
    public ModelOriginRecord {
        Objects.requireNonNull(sourceKind); Objects.requireNonNull(permission);
        dependencies = List.copyOf(dependencies);
        if (dependencies.size() > PeerLimits.RESOURCE_COUNT) throw new IllegalArgumentException("Too many dependencies");
        dependencies.forEach(PeerPaths::validate);
        if (contentRoot != null) new PeerModelIdentity(contentRoot, importerProfile);
    }
    public boolean allowsLocalShare() {
        return verified && contentRoot != null && permission == Permission.USER_APPROVED
                && (sourceKind == SourceKind.CUSTOM || sourceKind == SourceKind.BUILTIN);
    }
    public boolean allowsRoomRelay() {
        return verified && contentRoot != null && sourceKind == SourceKind.PEER && permission == Permission.ROOM_RELAY_ONLY;
    }
    // Path and owner are private provenance, intentionally absent from generated record diagnostics.
    @Override public String toString() { return "ModelOriginRecord[" + sourceKind + "," + permission + ",verified=" + verified + "]"; }
}
