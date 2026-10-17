package com.micaftic.morpher.peer;

import java.util.Set;

/** Frozen V1 interpreter names. Declaring a profile is separate from enabling its validated importer. */
public final class PeerProfiles {
    public static final Set<String> V1=Set.of("ysm-v1","ysm-directory-v1","ysm-zip-v1","bbmodel-v1","bbmodel-zip-v1",
            "figura-geometry-v1","bedrock-v1","gltf-v1","glb-v1");
    public static final Set<String> ENABLED=V1;
    private PeerProfiles(){}
    public static void requireKnown(String profile){if(!V1.contains(profile))throw new IllegalArgumentException("Unsupported Peer importer profile");}
}
