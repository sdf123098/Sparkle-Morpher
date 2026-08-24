package com.micaftic.morpher.core.model;

/**
 * R5.2 ModelRuntime — 模型运行时（重资源持有者）。
 *
 * <p>审计文档 3.27 拆分语义：runtime 持有 geometry / animation / textures / audio /
 * GPU resources 等重资源；与不可变的 {@link ModelDescriptor} 分离后，
 * lazy loading 会自然很多（descriptor 常驻、runtime 按需加载/卸载）。</p>
 *
 * <p>生命周期：{@link #close()} 释放全部资源（音频流、纹理、GPU 网格、native 句柄、
 * molang 表达式缓存等）。实现必须可重复 close（幂等）——{@link ModelRegistry}
 * 的 replace/evict 依赖此约定。</p>
 */
public interface ModelRuntime extends AutoCloseable {

    /** 该 runtime 对应的模型引用。 */
    ModelRef ref();

    /**
     * 是否已加载重资源。unload 后（close 或 lazy evict）返回 false，
     * 但 runtime 对象本身可能仍保留 descriptor 供 metadata 展示。
     */
    boolean isLoaded();

    /**
     * 释放全部重资源。幂等：多次调用安全；close 后 {@link #isLoaded()} 返回 false。
     */
    @Override
    void close();
}
