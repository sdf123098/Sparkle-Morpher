package com.micaftic.morpher.client;

import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.model.lifecycle.GpuCacheTrimCoordinator;
import com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Single owner for resident assemblies and the lifecycle markers that govern their release. */
public final class ClientModelResidency {
    private volatile Map<String, ModelAssembly> assemblies = Object2ReferenceMaps.emptyMap();
    private final ConcurrentHashMap<String, Long> lastUsedAt = new ConcurrentHashMap<>();
    private final GpuCacheTrimCoordinator<ModelAssembly> gpuTrim = new GpuCacheTrimCoordinator<>();
    private final KeyedRequestLeaseRegistry<String> cpuReloads = new KeyedRequestLeaseRegistry<>();
    private final Set<ModelAssembly> deferredReleases = ConcurrentHashMap.newKeySet();
    private volatile Boolean lastLazyLoadingMode;
    private long lastTrimMillis;

    public Map<String, ModelAssembly> assemblies() { return assemblies; }
    public void publishAssemblies(Map<String, ModelAssembly> value) { assemblies = value; }
    public ConcurrentHashMap<String, Long> lastUsedAt() { return lastUsedAt; }
    public GpuCacheTrimCoordinator<ModelAssembly> gpuTrim() { return gpuTrim; }
    public KeyedRequestLeaseRegistry<String> cpuReloads() { return cpuReloads; }
    public Set<ModelAssembly> deferredReleases() { return deferredReleases; }

    public synchronized boolean shouldTrimAt(long nowMillis) {
        if (nowMillis - lastTrimMillis < 1_000L) return false;
        lastTrimMillis = nowMillis;
        return true;
    }

    public synchronized void resetTrimThrottle() {
        lastTrimMillis = 0L;
    }

    /** Returns the previously observed mode and resets trim throttling on an actual transition. */
    public synchronized Boolean updateLazyLoadingMode(boolean enabled) {
        Boolean previous = lastLazyLoadingMode;
        if (previous != null && previous == enabled) return previous;
        lastLazyLoadingMode = enabled;
        lastTrimMillis = 0L;
        return previous;
    }
}
