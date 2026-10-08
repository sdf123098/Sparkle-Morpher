package com.micaftic.morpher.cloud.client;

import java.util.Objects;

/** A display timeline belongs to one explicit binding and one exact appearance revision. */
public record CloudEntityMotion(String worldEpoch,String dimensionId,String entityKind,String targetId,
        long bindingRevision,long appearanceRevision,CloudPlayerMotion motion) {
    public CloudEntityMotion {
        EntityDisplayContext.slug(worldEpoch);EntityDisplayContext.identifier(dimensionId);EntityDisplayContext.slug(targetId);
        if(!"MAID".equals(entityKind)&&!"FAKE_PLAYER".equals(entityKind)
            ||bindingRevision<0||bindingRevision>=9007199254740991L||appearanceRevision<0||appearanceRevision>=9007199254740991L)
            throw new IllegalArgumentException("Invalid entity motion binding");
        Objects.requireNonNull(motion).toJson();
    }
}
