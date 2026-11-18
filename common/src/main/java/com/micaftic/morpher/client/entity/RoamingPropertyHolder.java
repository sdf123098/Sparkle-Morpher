package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import org.jetbrains.annotations.Nullable;

public interface RoamingPropertyHolder {
    @Nullable
    RoamingStruct getServerVarContainer();
}
