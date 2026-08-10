package com.micaftic.morpher.core.model;

import java.util.Objects;

/**
 * R5.2 ModelDescriptor — 模型描述（不可变、轻量）。
 *
 * <p>审计文档 3.27：ModelAssembly 在 unloadRuntime 后保留 metadata 但把
 * animationBundle/expressionCache/projectiles/vehicles 设 null，实际上同时代表
 * "Loaded Assembly" 与 "Unloaded Metadata Stub" 两种状态。拆分为：</p>
 * <pre>
 *   ModelDescriptor = metadata / identity / source / lightweight info
 *   ModelRuntime    = geometry / animation / textures / audio / GPU resources
 * </pre>
 *
 * <p>Descriptor 可在模型未加载时存在（列表、搜索、元数据展示），
 * 不持有任何重资源；equals/hashCode 基于身份字段，可用于去重与比较。</p>
 */
public final class ModelDescriptor {

    private final ModelRef ref;
    private final String displayName;
    private final String format;

    public ModelDescriptor(ModelRef ref, String displayName, String format) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.displayName = (displayName == null || displayName.isEmpty()) ? null : displayName;
        this.format = (format == null || format.isEmpty()) ? null : format;
    }

    /** 模型引用（来源语义）。 */
    public ModelRef ref() {
        return ref;
    }

    /** 展示名（可空：未提供时由 UI 回退到 ref.id）。 */
    public String displayName() {
        return displayName;
    }

    /** 来源格式（如 YSM_NATIVE / BBMODEL / FIGURA_BBMODEL；可空）。 */
    public String format() {
        return format;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelDescriptor other)) {
            return false;
        }
        return ref.equals(other.ref) && Objects.equals(displayName, other.displayName)
                && Objects.equals(format, other.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref, displayName, format);
    }

    @Override
    public String toString() {
        return "ModelDescriptor[" + ref + (displayName != null ? ", name=" + displayName : "") + "]";
    }
}
