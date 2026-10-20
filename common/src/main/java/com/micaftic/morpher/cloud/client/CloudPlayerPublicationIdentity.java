package com.micaftic.morpher.cloud.client;

/** Keeps experimental offline publication distinct from verified game identity authorization. */
public final class CloudPlayerPublicationIdentity {
    public enum Kind { NONE, VERIFIED, CLOUD_OFFLINE }
    private CloudPlayerPublicationIdentity() {}
    public static Kind choose(boolean verified, boolean onlineAuthenticationFailed, boolean capability, boolean explicitlyEnabled, boolean keyPresent) {
        if(onlineAuthenticationFailed)return Kind.NONE;
        if(verified)return Kind.VERIFIED;
        return capability&&explicitlyEnabled&&keyPresent?Kind.CLOUD_OFFLINE:Kind.NONE;
    }
}
