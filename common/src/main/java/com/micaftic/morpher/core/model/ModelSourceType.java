package com.micaftic.morpher.core.model;

/**
 * 模型来源类型；旧来源 token 继续保留以兼容既有运行时标识。
 *
 * <p>模型 ID 的语义来源。当前代码大量使用裸 {@code String modelId}（如 {@code "cirno"}），
 * 用于区分本地模型、历史来源和云端模型，因此用
 * {@link ModelRef}（source + namespace + id）表达。</p>
 *
 * <p>运行时文本格式（token）：</p>
 * <pre>
 *   builtin:default            → BUILTIN
 *   local:cirno                → LOCAL
 *   server:cirno               → LEGACY_SERVER（兼容 token）
 *   server-forced:xxx          → SERVER_FORCED（兼容 token）
 *   cloud:&lt;uuid&gt;:&lt;asset-id&gt;  → CLOUD
 * </pre>
 */
public enum ModelSourceType {
    /** 内置示例/默认模型（随 mod 打包）。 */
    BUILTIN("builtin"),
    /** 本地模型（游戏目录 config/sparkle_morpher 下的 custom/builtin 等）。 */
    LOCAL("local"),
    /** 历史来源 token，保留用于读取兼容模型标识。 */
    LEGACY_SERVER("server"),
    /** 历史强制来源 token，保留用于读取兼容模型标识。 */
    SERVER_FORCED("server-forced"),
    /** 云端模型（类 Figura Backend，R15 规划）。 */
    CLOUD("cloud");

    private final String token;

    ModelSourceType(String token) {
        this.token = token;
    }

    /** 运行时标识格式使用的小写 token。 */
    public String token() {
        return token;
    }

    /** 按 token 反查（大小写不敏感）；未知 token 返回 null。 */
    public static ModelSourceType fromToken(String token) {
        if (token == null) {
            return null;
        }
        for (ModelSourceType type : values()) {
            if (type.token.equalsIgnoreCase(token)) {
                return type;
            }
        }
        return null;
    }
}
