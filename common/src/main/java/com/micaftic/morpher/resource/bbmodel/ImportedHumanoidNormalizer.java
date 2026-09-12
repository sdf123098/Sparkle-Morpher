package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.pojo.RawYsmModel;

/**
 * 1.2.7 §24.3：从 {@code BBToRawConverter} 外提的人形归一化职责（行为等价，纯搬运）。
 *
 * <p>属于 <b>SPM 导入策略</b>而非格式转换：把导入模型补成人形玩家可用的默认形态——
 * 统一玩家缩放、写入导入来源标记与 footer 版本，并给出默认动作轮盘条目，
 * 让内置 bbmodel 表情在轮盘/热键里可达。
 *
 * <p>{@link #putImportedRouletteDefaults} 使用 {@code putIfAbsent}，不会覆盖模型自带的轮盘配置。
 */
public final class ImportedHumanoidNormalizer {

    private ImportedHumanoidNormalizer() {
    }

    /** 导入模型的统一玩家缩放。 */
    static final float IMPORTED_PLAYER_SCALE = 1.0f;
    /** footer 的 extra 标记，用于识别这是 bbmodel 导入产物。 */
    static final String IMPORT_SOURCE_EXTRA = "sparkle_morpher:bbmodel_import";
    /** 导入产物使用的 footer 版本。 */
    public static final int IMPORT_FOOTER_VERSION = 32;

    // Default action-roulette entries for imported bbmodel/figura players.
    // key = animation name (played by index), value = display label (fallback when the
    // model has no localized "properties.extra_animation.<key>" entry).
    // Kept in sync with the bbmodel emote preset (builtin/bbmodel/animations/extra.animation.json).
    private static final String[][] IMPORTED_ROULETTE_ENTRIES = {
            {"extra0", "Wave"},
            {"extra1", "Sit"},
            {"extra2", "Cheer"},
            {"extra3", "Point"}
    };

    /** 写入导入来源标记、footer 版本与统一玩家缩放。 */
    public static void applyImportedPlayerDefaults(RawYsmModel raw) {
        raw.properties.widthScale = IMPORTED_PLAYER_SCALE;
        raw.properties.heightScale = IMPORTED_PLAYER_SCALE;
        raw.footer.version = IMPORT_FOOTER_VERSION;
        raw.footer.unkInt1 = 1;
        raw.footer.extra = IMPORT_SOURCE_EXTRA;
    }

    /**
     * 给导入模型补默认表情轮盘，使其内置 bbmodel 表情可通过轮盘 / 热键触发。
     * 使用 {@code putIfAbsent}，保留模型作者自带的条目。
     */
    public static void putImportedRouletteDefaults(RawYsmModel raw) {
        if (raw.properties == null || raw.properties.extraAnimations == null) {
            return;
        }
        for (String[] entry : IMPORTED_ROULETTE_ENTRIES) {
            raw.properties.extraAnimations.putIfAbsent(entry[0], entry[1]);
        }
    }
}
