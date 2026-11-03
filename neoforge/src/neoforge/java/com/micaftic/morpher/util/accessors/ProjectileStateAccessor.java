package com.micaftic.morpher.util.accessors;

public interface ProjectileStateAccessor {
    boolean ysm$isInGround();

    int ysm$getInGroundTime();

    String ysm$getOwnerItemId();
    /** Display metadata only; null means that historical equipment is unknown. */
    void ysm$setDisplayFiringItem(String itemId);
}
