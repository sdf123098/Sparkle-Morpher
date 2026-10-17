package com.micaftic.morpher.peer;

/** Opt-in policy. Reading a legacy config preserves Cloud. No static networking side effects. */
public enum ConnectivityPolicy {
    LOCAL_ONLY(false,false), PEER_ROOM(true,false), CLOUD(false,true), HYBRID(true,true);
    private final boolean peer, cloud;
    ConnectivityPolicy(boolean peer, boolean cloud) { this.peer=peer; this.cloud=cloud; }
    public boolean allowsPeer(boolean privacy) { return peer && !privacy; }
    public boolean allowsCloud(boolean privacy) { return cloud && !privacy; }
    public static ConnectivityPolicy fromConfig(String value) {
        if (value==null || value.isBlank()) return CLOUD;
        try { return valueOf(value); } catch (IllegalArgumentException e) { return CLOUD; }
    }
}
