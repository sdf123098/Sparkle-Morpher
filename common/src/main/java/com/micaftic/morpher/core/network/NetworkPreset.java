package com.micaftic.morpher.core.network;

/** Network permissions and appearance priority are independent of the selected local model. */
public enum NetworkPreset {
    AUTO(true, true, false), PEER_FIRST(true, true, false), CLOUD_FIRST(true, true, true),
    PEER_ONLY(true, false, false), CLOUD_ONLY(false, true, true), LOCAL_ONLY(false, false, false);
    public enum Source { LOCAL, PEER, CLOUD }
    private final boolean peer, cloud, cloudFirst;
    NetworkPreset(boolean peer, boolean cloud, boolean cloudFirst) { this.peer=peer; this.cloud=cloud; this.cloudFirst=cloudFirst; }
    public boolean allowsPeer(boolean privacy) { return peer && !privacy; }
    public boolean allowsCloud(boolean privacy) { return cloud && !privacy; }
    public boolean prefersCloud() { return cloudFirst; }
    public Source select(boolean peerReady, boolean cloudReady, boolean privacy) {
        if(cloudFirst && cloudReady && allowsCloud(privacy)) return Source.CLOUD;
        if(peerReady && allowsPeer(privacy)) return Source.PEER;
        if(cloudReady && allowsCloud(privacy)) return Source.CLOUD;
        return Source.LOCAL;
    }
    public static NetworkPreset fromConfig(String value) {
        try { return value == null ? AUTO : valueOf(value); } catch(IllegalArgumentException ignored) { return AUTO; }
    }
}
