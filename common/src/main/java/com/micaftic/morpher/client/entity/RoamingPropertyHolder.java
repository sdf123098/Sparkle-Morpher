package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.molang.runtime.Struct;
import org.jetbrains.annotations.Nullable;

public interface RoamingPropertyHolder {
    @Nullable
    Struct getServerVarContainer();
}