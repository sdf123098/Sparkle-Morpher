package com.micaftic.morpher.client.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SemanticSkeleton {

    public static final SemanticSkeleton EMPTY = new SemanticSkeleton(Collections.emptyMap());

    private final Map<String, String> boneTargets;

    public SemanticSkeleton(Map<String, String> boneTargets) {
        this.boneTargets = Collections.unmodifiableMap(new LinkedHashMap<>(boneTargets));
    }

    public Map<String, String> getBoneTargets() {
        return this.boneTargets;
    }

    public String getTarget(String semanticBone) {
        return this.boneTargets.get(semanticBone);
    }
}
