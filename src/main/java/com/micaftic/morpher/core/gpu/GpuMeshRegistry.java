package com.micaftic.morpher.core.gpu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R10.2 GPU mesh 租约注册表（纯 Java 簿记，无 GL 依赖）。
 *
 * <p>把"mesh ownership 与 model runtime 绑定"落成显式租约：
 * <ul>
 *   <li>每个 mesh 注册时绑定 owner（弱引用，通常为 {@code GeoModel}——它随 ModelAssembly
 *       runtime 存活）；</li>
 *   <li>owner 显式释放（{@link #releaseOwner}）时，其名下 mesh 从注册表移除并交还调用方 dispose；
 *       同一 owner 的 {@code gpuMeshHandle} 因 ref 已移除而失效，下次渲染走既有"ref 缺失重建"路径；
 *       </li>
 *   <li>owner 被 GC（弱引用失效）而未显式释放时，{@link #sweepOrphans} 兜底回收，防止
 *       模型装配被替换/异常路径导致 GPU mesh 泄漏；</li>
 *   <li>ref 单调递增不复用，避免悬垂句柄误命中其他 mesh。</li>
 * </ul>
 *
 * <p>本类不做任何 GL 调用——dispose 由调用方在渲染线程执行。泛型 T 便于 JVM 单测用假 mesh。
 */
public final class GpuMeshRegistry<T> {

    private final ConcurrentHashMap<Long, Entry<T>> entries = new ConcurrentHashMap<>();

    private final AtomicLong refCounter = new AtomicLong(1);

    private static final class Entry<T> {
        final T mesh;
        final WeakReference<Object> ownerRef;

        Entry(T mesh, Object owner) {
            this.mesh = mesh;
            this.ownerRef = owner == null ? null : new WeakReference<>(owner);
        }
    }

    /**
     * 注册一个 mesh 并返回其句柄（ref）。
     *
     * @param owner mesh 的归属对象（弱引用持有）；null 表示无归属，将立即成为孤儿由 sweep 回收。
     */
    public long register(Object owner, T mesh) {
        long ref = refCounter.getAndIncrement();
        entries.put(ref, new Entry<>(mesh, owner));
        return ref;
    }

    /** 按 ref 取 mesh；不存在或已被释放返回 null。 */
    public T get(long ref) {
        Entry<T> entry = entries.get(ref);
        return entry == null ? null : entry.mesh;
    }

    /** 按 ref 移除并返回（用于单模型释放）；不存在返回 null。调用方负责 dispose。 */
    public T remove(long ref) {
        Entry<T> entry = entries.remove(ref);
        return entry == null ? null : entry.mesh;
    }

    /**
     * 释放指定 owner 名下的全部 mesh（身份比较）：从注册表移除并返回，供调用方 dispose。
     * owner 为 null 时返回空列表。
     */
    public List<T> releaseOwner(Object owner) {
        List<T> released = new ArrayList<>();
        if (owner == null) {
            return released;
        }
        entries.forEach((ref, entry) -> {
            if (entry.ownerRef != null && entry.ownerRef.get() == owner) {
                Entry<T> removed = entries.remove(ref);
                if (removed != null && removed.mesh != null) {
                    released.add(removed.mesh);
                }
            }
        });
        return released;
    }

    /**
     * 回收孤儿 mesh：owner 弱引用已失效（GC）或注册时即无 owner 的条目。
     * 返回需 dispose 的 mesh 列表。这是 GC 兜底——正常路径由模型释放显式 releaseOwner。
     */
    public List<T> sweepOrphans() {
        List<T> orphans = new ArrayList<>();
        entries.forEach((ref, entry) -> {
            if (entry.ownerRef == null || entry.ownerRef.get() == null) {
                Entry<T> removed = entries.remove(ref);
                if (removed != null && removed.mesh != null) {
                    orphans.add(removed.mesh);
                }
            }
        });
        return orphans;
    }

    /** 清空注册表并返回全部 mesh（用于 disposeAllMeshes 全量释放）。 */
    public List<T> clearAll() {
        List<T> all = new ArrayList<>();
        entries.forEach((ref, entry) -> {
            Entry<T> removed = entries.remove(ref);
            if (removed != null && removed.mesh != null) {
                all.add(removed.mesh);
            }
        });
        return all;
    }

    /** 当前注册条数。 */
    public int size() {
        return entries.size();
    }
}
