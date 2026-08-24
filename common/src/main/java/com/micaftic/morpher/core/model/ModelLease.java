package com.micaftic.morpher.core.model;

import java.util.Objects;

/**
 * R5.3 ModelLease — 模型运行时持有令牌。
 *
 * <p>{@link ModelRegistry#lease} 返回；持有期间 {@link ModelRegistry#evict} 不会释放
 * 对应 runtime（防止正在渲染/使用的模型被替换或卸载——"replace 不泄漏 / 不闪 default"
 * 验收的一部分）。{@link #close()} 归还持有，归还后 evict 可正常执行。</p>
 *
 * <p>线程安全：close 幂等，多次调用安全。</p>
 */
public final class ModelLease implements AutoCloseable {

    private final ModelRegistry registry;
    private final ModelRef ref;
    private volatile boolean closed;

    ModelLease(ModelRegistry registry, ModelRef ref) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ref = Objects.requireNonNull(ref, "ref");
    }

    /** 被持有的模型引用。 */
    public ModelRef ref() {
        return ref;
    }

    public boolean isClosed() {
        return closed;
    }

    /** 归还持有（幂等）。 */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            registry.releaseLease(this);
        }
    }
}
