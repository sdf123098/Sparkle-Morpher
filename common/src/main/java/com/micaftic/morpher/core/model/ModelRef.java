package com.micaftic.morpher.core.model;

import java.util.Objects;

/**
 * R5.1 模型引用（审计文档 3.5 新领域模型）：来源语义的模型 ID。
 *
 * <p>替代裸 {@code String modelId} 表达模型身份——String 无法稳定区分：</p>
 * <pre>
 *   local:cirno          （本地模型）
 *   server:cirno         （服务器下发模型）
 *   cloud:&lt;uuid&gt;:&lt;asset&gt;（云端模型）
 * </pre>
 *
 * <p>不要让 GUI ID、文件 ID、服务器 ID、Runtime Key 共用一个 String 语义。</p>
 *
 * <p>文本格式：{@code source:id}（两段，namespace 为空）或
 * {@code source:namespace:id}（三段，cloud 等场景）。parse/toString 往返稳定。</p>
 */
public record ModelRef(ModelSourceType source, String namespace, String id) {

    public ModelRef {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(id, "id");
        // namespace 空串归一化为 null（两段格式）
        namespace = (namespace == null || namespace.isEmpty()) ? null : namespace;
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ModelRef id must not be empty");
        }
    }

    /** 便捷构造：无 namespace（两段格式，如 {@code local:cirno}、{@code server:cirno}）。 */
    public static ModelRef of(ModelSourceType source, String id) {
        return new ModelRef(source, null, id);
    }

    /** 便捷构造：带 namespace（三段格式，如 {@code cloud:<uuid>:<asset-id>}）。 */
    public static ModelRef of(ModelSourceType source, String namespace, String id) {
        return new ModelRef(source, namespace, id);
    }

    /** 运行时文本格式（与 {@link #parse} 往返）。 */
    @Override
    public String toString() {
        if (namespace == null) {
            return source.token() + ":" + id;
        }
        return source.token() + ":" + namespace + ":" + id;
    }

    /**
     * 从运行时文本格式反解析（token 大小写不敏感）。
     *
     * @return 解析后的 ModelRef
     * @throws IllegalArgumentException 格式非法（空、缺 source、未知 source token、缺 id）
     */
    public static ModelRef parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("ModelRef text must not be empty");
        }
        String[] parts = text.split(":", -1);
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid ModelRef format (expected source:id or source:namespace:id): " + text);
        }
        ModelSourceType source = ModelSourceType.fromToken(parts[0]);
        if (source == null) {
            throw new IllegalArgumentException("Unknown model source token: " + parts[0]);
        }
        String id = parts[parts.length - 1];
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ModelRef id must not be empty: " + text);
        }
        if (parts.length == 2) {
            return new ModelRef(source, null, id);
        }
        String namespace = parts[1];
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("ModelRef namespace must not be empty (use source:id form): " + text);
        }
        return new ModelRef(source, namespace, id);
    }
}
