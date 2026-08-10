package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;

/**
 * R1.4 统一日志基建。
 *
 * 目标：逐步替代散落的 System.out / printStackTrace / 吞异常调用。
 * 不强制一次性迁移全部调用点（见 docs R1.4），新代码与迁移点统一走本类。
 *
 * 日志分类 tag：MODEL / NETWORK / CACHE / RESOURCE / AUDIO / GPU / COMPAT / SECURITY / RUNTIME。
 * 底层走 SLF4J（YesSteveModel.LOGGER），由游戏日志系统统一输出。
 */
public final class SmLog {

    private SmLog() {
    }

    public static void error(String tag, String message) {
        YesSteveModel.LOGGER.error("[{}] {}", tag, message);
    }

    public static void warn(String tag, String message) {
        YesSteveModel.LOGGER.warn("[{}] {}", tag, message);
    }

    public static void info(String tag, String message) {
        YesSteveModel.LOGGER.info("[{}] {}", tag, message);
    }

    public static void debug(String tag, String message) {
        YesSteveModel.LOGGER.debug("[{}] {}", tag, message);
    }
}
