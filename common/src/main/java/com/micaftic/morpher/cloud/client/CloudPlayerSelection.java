package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.model.CloudAssetIdentity;
import java.util.Locale;
import java.util.Objects;

/** A player's selection, never an endpoint, credential or model payload. */
public record CloudPlayerSelection(String instanceId, String originSha256, CloudAssetRef ref,
        String format, String textureId) {
    public CloudPlayerSelection {
        Objects.requireNonNull(ref, "ref");
        if (instanceId == null || !instanceId.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid Cloud instance id");
        if (originSha256 == null || !originSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid Cloud origin fingerprint");
        format = Objects.requireNonNull(format, "format").toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("ysm", "zip", "bbmodel", "gltf", "glb").contains(format))
            throw new IllegalArgumentException("Unsupported Cloud model format");
        textureId = Objects.requireNonNull(textureId, "textureId");
        if (textureId.isBlank() || textureId.length() > 256 || textureId.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid Cloud texture id");
    }

    public String runtimeModelId() {
        return new CloudAssetIdentity(instanceId, "catalog", ref.assetId(),
                Long.toString(ref.revision()), ref.rawSha256()).runtimeModelId();
    }

    public String importFileName() { return "player-model." + format; }
}
