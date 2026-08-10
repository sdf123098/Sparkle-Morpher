package com.micaftic.morpher.core.model.selection;

/**
 * R6.2 模型优先级（审计文档 3.6 推荐优先级）。
 *
 * <p>从高到低：</p>
 * <pre>
 *   1. SERVER_FORCED   服务器强制（隐私模式/localOnly 策略决定）
 *   2. LOCAL_PREVIEW   本地预览（仅自己的实体）
 *   3. CLOUD           云端模型
 *   4. LEGACY_SERVER   旧式服务器下发
 *   5. DEFAULT         默认
 * </pre>
 */
public enum ModelPriority {
    SERVER_FORCED,
    LOCAL_PREVIEW,
    CLOUD,
    LEGACY_SERVER,
    DEFAULT;

    /** 数值越小优先级越高（排名）。 */
    public int rank() {
        return ordinal();
    }
}