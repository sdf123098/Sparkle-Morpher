package com.micaftic.morpher.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;


import java.util.concurrent.TimeUnit;

@Environment(EnvType.CLIENT)
public final class AnimatableCacheUtil {
    public static final Cache<Identifier, Entity> ENTITIES_CACHE = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();
}