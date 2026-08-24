package com.micaftic.morpher.core.model;

/**
 * R5.1 模型来源类型（审计文档 3.5 新领域模型）。
 *
 * <p>模型 ID 的语义来源。当前代码大量使用裸 {@code String modelId}（如 {@code "cirno"}），
 * 无法区分本地模型、服务器下发模型、云端模型——未来实际至少存在五种来源，因此用
 * {@link ModelRef}（source + namespace + id）表达。</p>
 *
 * <p>运行时文本格式（token）：</p>
 * <pre>
 *   builtin:default            → BUILTIN
 *   local:cirno                → LOCAL
 *   server:cirno               → LEGACY_SERVER（服务器下发）
 *   server-forced:xxx          → SERVER_FORCED（服务器强制）
 *   cloud:&lt;uuid&gt;:&lt;asset-id&gt;  → CLOUD
 * </pre>
 */
public enum ModelSourceType {
    /** 内置示例/默认模型（随 mod 打包）。 */
    BUILTIN("builtin"),
    /** 本地模型（游戏目录 config/sparkle_morpher 下的 custom/builtin 等）。 */
    LOCAL("local"),
    /** 服务器下发的旧式模型（packet 传输，如 ServerModelContext）。 */
    LEGACY_SERVER("server"),
    /** 服务器强制模型（隐私模式/localOnly 等策略决定，优先级最高）。 */
    SERVER_FORCED("server-forced"),
    /** 云端模型（类 Figura Backend，R15 规划）。 */
    CLOUD("cloud");

    private final String token;

    ModelSourceType(String token) {
        this.token = token;
    }

    /** 运行时文本格式使用的 token（小写；LEGACY_SERVER → "server"）。 */
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
