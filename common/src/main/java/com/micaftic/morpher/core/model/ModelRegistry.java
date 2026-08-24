package com.micaftic.morpher.core.model;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R5.3 ModelRegistry — 模型运行时注册表（审计文档 3.5/3.6 的 registry 组件）。
 *
 * <p>唯一负责模型 runtime 的注册与生命周期入口：</p>
 * <pre>
 *   register / replace / lookup / lease / evict
 * </pre>
 *
 * <p>UI/业务代码不直接持有内部 mutable map，一律经本类 API 操作（refs/snapshot
 * 返回不可变副本）。</p>
 *
 * <p>生命周期约定：</p>
 * <ul>
 *   <li><b>replace 不泄漏</b>：replace 无条件 {@code close()} 旧 runtime（模型更新场景，
 *       旧资源必须释放，验收项）。</li>
 *   <li><b>lease 防 evict</b>：lease 持有期间 evict 返回 false 且不 close 不移除
 *       （正在渲染/使用的模型不会被懒卸载），lease 归还后才可 evict。</li>
 *   <li>register 为 putIfAbsent 语义：ref 已存在时返回旧 runtime 且不替换，
 *       新 runtime 是否 close 由调用方决定（避免静默丢失调用方正在构建的资源）。</li>
 *   <li>并发：内部 ConcurrentHashMap；lease/evict 竞争为 best-effort（R5 骨架，
 *       R6/R7 拆 ClientModelManager 时如有需要可上 per-ref 锁收紧）。</li>
 * </ul>
 */
public final class ModelRegistry {

    private final ConcurrentHashMap<ModelRef, ModelRuntime> runtimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ModelRef, AtomicInteger> leaseCounts = new ConcurrentHashMap<>();

    /**
     * 注册 runtime（putIfAbsent 语义）。
     *
     * @return 已存在的旧 runtime（ref 冲突时）；未冲突返回 null
     */
    public ModelRuntime register(ModelRef ref, ModelRuntime runtime) {
        return runtimes.putIfAbsent(ref, runtime);
    }

    /**
     * 替换 runtime：无条件替换并 {@code close()} 旧 runtime（replace 不泄漏）。
     *
     * @return 被替换的旧 runtime（若存在），便于调用方记录
     */
    public ModelRuntime replace(ModelRef ref, ModelRuntime runtime) {
        ModelRuntime old = runtimes.put(ref, runtime);
        if (old != null && old != runtime) {
            old.close();
        }
        return old;
    }

    /** 查询 runtime（不转移所有权；未注册返回 null）。 */
    public ModelRuntime lookup(ModelRef ref) {
        return runtimes.get(ref);
    }

    /**
     * 持有模型：返回租约令牌（ref 未注册返回 null）。
     * 持有期间 {@link #evict} 不会释放该 runtime；{@link ModelLease#close()} 归还。
     */
    public ModelLease lease(ModelRef ref) {
        if (!runtimes.containsKey(ref)) {
            return null;
        }
        leaseCounts.computeIfAbsent(ref, k -> new AtomicInteger()).incrementAndGet();
        return new ModelLease(this, ref);
    }

    /**
     * 卸载模型（lazy unload）：无活跃 lease 时 close + 移除并返回 true；
     * 有活跃 lease 时不释放不移除，返回 false。
     */
    public boolean evict(ModelRef ref) {
        AtomicInteger leaseCount = leaseCounts.get(ref);
        if (leaseCount != null && leaseCount.get() > 0) {
            return false;
        }
        ModelRuntime removed = runtimes.remove(ref);
        if (removed != null) {
            removed.close();
            return true;
        }
        return false;
    }

    /** 释放租约（由 {@link ModelLease#close()} 调用；包内可见）。 */
    void releaseLease(ModelLease lease) {
        AtomicInteger count = leaseCounts.get(lease.ref());
        if (count != null && count.decrementAndGet() <= 0) {
            leaseCounts.remove(lease.ref(), count);
        }
    }

    /** 当前注册的 ref 集合（不可变副本）。 */
    public Set<ModelRef> refs() {
        return Set.copyOf(runtimes.keySet());
    }

    /** 当前全部 runtime 快照（不可变副本）。 */
    public Map<ModelRef, ModelRuntime> snapshot() {
        return Map.copyOf(runtimes);
    }

    public int size() {
        return runtimes.size();
    }

    /** 释放全部 runtime 并清空（游戏退出/世界卸载）。 */
    public void closeAll() {
        for (ModelRuntime runtime : runtimes.values()) {
            runtime.close();
        }
        runtimes.clear();
        leaseCounts.clear();
    }
}
