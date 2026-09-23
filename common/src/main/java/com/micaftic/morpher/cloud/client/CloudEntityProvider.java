package com.micaftic.morpher.cloud.client;

import java.util.Objects;
import java.util.UUID;

/**
 * Loader/client adapter for one Cloud-managed entity kind.
 *
 * <p>The adapter never creates a binding. It only applies an already accepted
 * Cloud appearance to a concrete client-side entity.</p>
 */
public interface CloudEntityProvider {
    Kind kind();

    void applyAppearance(UUID entityUuid, CloudScopeClient.CloudAppearance appearance, long bindingRevision);

    /**
     * Returns whether this adapter currently has the explicitly bound entity
     * loaded on the client. Providers must not use this method to discover or
     * create a Cloud binding.
     */
    default boolean isAvailable(UUID entityUuid) {
        return false;
    }

    enum Kind {
        PLAYER("PLAYER"),
        FAKE_PLAYER("FAKE_PLAYER"),
        MAID("MAID");

        private final String wireValue;

        Kind(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        public static Kind fromWireValue(String value) {
            Objects.requireNonNull(value, "value");
            for (Kind kind : values()) {
                if (kind.wireValue.equals(value)) return kind;
            }
            throw new IllegalArgumentException("Unsupported Cloud entity kind: " + value);
        }
    }
}
