package com.micaftic.morpher.client;

import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.core.model.lifecycle.GpuCacheTrimCoordinator;
import com.micaftic.morpher.core.model.lifecycle.KeyedRequestLeaseRegistry;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single owner for resident assemblies and the lifecycle markers that govern their release. */
public final class ClientModelResidency {
    private volatile Map<String, ModelAssembly> assemblies = Object2ReferenceMaps.emptyMap();
    private final ConcurrentHashMap<String, Long> lastUsedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> consumers = new ConcurrentHashMap<>();
    private final Set<RuntimeModelLease> leases = ConcurrentHashMap.newKeySet();

    public void revoke(String runtimeModelId) {
        String key = LocalModelCatalog.canonicalKey(runtimeModelId);
        leases.stream().filter(lease -> lease.runtimeModelId().equals(key)).toList().forEach(RuntimeModelLease::close);
    }
    private final ConcurrentHashMap<String, Runnable> pendingReleases = new ConcurrentHashMap<>();
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

    /** Reserves a consumer share so trimming and owner releases keep the assembly resident. */
    public RuntimeModelLease acquire(String runtimeModelId) {
        String key = Objects.requireNonNull(LocalModelCatalog.canonicalKey(runtimeModelId), "runtimeModelId");
        consumers.merge(key, 1, Integer::sum);
        lastUsedAt.put(key, System.currentTimeMillis());
        gpuTrim.clear(key);
        RuntimeModelLease[] holder = new RuntimeModelLease[1];
        holder[0] = new RuntimeModelLease(key, () -> { leases.remove(holder[0]); releaseConsumer(key); });
        leases.add(holder[0]);
        return holder[0];
    }

    public int consumerCount(String runtimeModelId) {
        String key = LocalModelCatalog.canonicalKey(runtimeModelId);
        return key == null ? 0 : consumers.getOrDefault(key, 0);
    }

    public Set<String> consumedModelIds() { return Set.copyOf(consumers.keySet()); }

    /**
     * Defers a retirement until the last consumer closes. Returns false when the caller
     * should run the retirement immediately; true means it is now owned by the lease.
     */
    public boolean deferWhileConsumed(String runtimeModelId, Runnable release) {
        String key = Objects.requireNonNull(LocalModelCatalog.canonicalKey(runtimeModelId), "runtimeModelId");
        AtomicBoolean deferred = new AtomicBoolean();
        consumers.computeIfPresent(key, (ignored, count) -> { deferred.set(true); return count; });
        if (!deferred.get()) return false;
        pendingReleases.put(key, release);
        AtomicBoolean consumed = new AtomicBoolean();
        consumers.computeIfPresent(key, (ignored, count) -> { consumed.set(true); return count; });
        if (!consumed.get() && pendingReleases.remove(key, release)) {
            release.run();
        }
        return true;
    }

    private void releaseConsumer(String key) {
        AtomicBoolean last = new AtomicBoolean();
        consumers.computeIfPresent(key, (ignored, count) -> {
            if (count <= 1) { last.set(true); return null; }
            return count - 1;
        });
        lastUsedAt.put(key, System.currentTimeMillis());
        gpuTrim.clear(key);
        if (!last.get()) return;
        Runnable pending = pendingReleases.remove(key);
        if (pending != null) pending.run();
    }

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
