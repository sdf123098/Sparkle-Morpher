package com.micaftic.morpher.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.neoforged.api.distmarker.Dist;import net.neoforged.api.distmarker.OnlyIn;import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;


import java.util.concurrent.TimeUnit;

@OnlyIn(Dist.CLIENT)
public final class AnimatableCacheUtil {
    public static final Cache<ResourceLocation, Entity> ENTITIES_CACHE = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();
}