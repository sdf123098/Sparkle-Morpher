package com.micaftic.morpher.util.accessors;

public interface ProjectileStateAccessor {
    boolean isInGround();

    int getInGroundTime();

    String getOwnerItemId();
    /** Display metadata only; null means that historical equipment is unknown. */
    void ysm$setDisplayFiringItem(String itemId);
}