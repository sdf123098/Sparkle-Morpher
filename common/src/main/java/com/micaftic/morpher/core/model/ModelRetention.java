package com.micaftic.morpher.core.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * R7.3 ModelRetention — 跨会话模型装配保留筛选（从 ClientModelManager.resetClientState 抽取）。
 *
 * <p>断线/换服时，服务端模型装配（含纹理源 byte[] 与 GPU/native 资源）必须释放，
 * 否则会被 modelAssemblyMap 强引用跨会话累积（主要内存泄漏源）。保留规则：</p>
 * <ul>
 *   <li>localContext（默认模型装配，引用相等判定）</li>
 *   <li>localOnly id（本地导入/内置模型，reloadLocalModels 会重建）</li>
 *   <li>{@code "default"} id</li>
 * </ul>
 *
 * <p>其余装配进入 toRelease，由调用方释放。纯 Java（泛型 T 不依赖 ModelAssembly），
 * JVM 单测可跑真实判定。</p>
 */
public final class ModelRetention {

    private ModelRetention() {
    }

    /** 保留/释放分组：survivors 保留（含原 key），toRelease 为需释放的装配值。 */
    public record Split<T>(List<Map.Entry<String, T>> survivors, List<T> toRelease) {
    }

    /**
     * 把装配条目分成"跨会话保留"与"需释放"两组。
     *
     * @param entries      装配条目（key = model id，value = 装配；保持传入顺序）
     * @param isLocalOnly  model id → 是否仅本地模型
     * @param localContext 默认模型装配（引用相等判定；null 表示无）
     */
    public static <T> Split<T> partition(Iterable<? extends Map.Entry<String, T>> entries,
                                         Predicate<String> isLocalOnly,
                                         @Nullable T localContext) {
        List<Map.Entry<String, T>> survivors = new ArrayList<>();
        List<T> toRelease = new ArrayList<>();
        for (Map.Entry<String, T> entry : entries) {
            T assembly = entry.getValue();
            if (assembly == localContext || isLocalOnly.test(entry.getKey()) || "default".equals(entry.getKey())) {
                survivors.add(entry);
            } else {
                toRelease.add(assembly);
            }
        }
        return new Split<>(survivors, toRelease);
    }
}
