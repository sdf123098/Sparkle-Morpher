package com.micaftic.morpher.core.gui;

/**
 * Layout constants and the color palette for the unified animation
 * roulette. All four Sparkle Morpher subprojects ship an identical copy
 * of this class so visual tweaks stay in lockstep.
 */
public final class RouletteTheme {

    private RouletteTheme() {
    }

    // ---- Adaptive layout footprint ----
    // The roulette is laid out with fixed pixel offsets from the screen
    // center. These two values describe the total footprint that layout
    // occupies (page buttons at +/-160+radius horizontally, path label at
    // -150 and page indicator at +136 vertically, plus margins). When the
    // GUI-scaled screen is smaller than this footprint the whole wheel is
    // uniformly scaled down so it never overflows at high GUI scale / small
    // resolutions. See UnifiedRouletteScreen#layoutScale.
    public static final int DESIGN_WIDTH = 380;
    public static final int DESIGN_HEIGHT = 340;

    // ---- Layout (all values are pixel offsets from the screen center) ----
    public static final float WHEEL_INNER_R = 30.0f;
    public static final float WHEEL_GEAR_R = 56.0f;
    public static final float WHEEL_OUTER_R = 128.0f;
    public static final float WHEEL_OUTLINE_R = 130.0f;
    public static final float GAP_ANGLE_PADDING = 0.02f;

    public static final float PAGE_BTN_OFFSET = 160.0f;
    public static final float PAGE_BTN_RADIUS = 18.0f;

    public static final float EDIT_BTN_RADIUS = 18.0f;
    public static final int EDIT_BTN_BOTTOM_MARGIN = 24;

    public static final int PATH_Y_OFFSET = -150;
    public static final int PAGE_INDICATOR_Y_OFFSET = 136;

    public static final int ICON_SIZE_GEAR = 14;
    public static final int ICON_SIZE_LOCK = 28;
    public static final int ICON_SIZE_ARROW = 14;
    public static final int ICON_SIZE_OPTION = 16;
    public static final int ICON_SIZE_EDIT = 14;

    // ---- Palette (ARGB) ----
    // Background veil
    public static final int BG_VEIL = 0x7A05070A;

    // Wheel slices - translucent frosted-glass plates.
    public static final int SLICE_IDLE        = 0x785F6B7A;
    public static final int SLICE_HOVER       = 0xB8DCEBFF;
    public static final int SLICE_SUBMENU     = 0x806B7488;
    public static final int SLICE_SUBMENU_HOV = 0xC8FFE3A3;
    public static final int SLICE_EMPTY       = 0x22384552;
    public static final int SLICE_OUTLINE     = 0xA8FFFFFF;
    public static final int SLICE_INNER_GLOW  = 0x36FFFFFF;
    public static final int SLICE_SHADOW      = 0x3A000000;

    // Wheel inner ring (gear cluster)
    public static final int GEAR_IDLE         = 0x809AA8BA;
    public static final int GEAR_HOVER        = 0xD8FFFFFF;

    // Central lock pip
    public static final int CENTER_FILL       = 0xA02B3440;
    public static final int CENTER_RING       = 0xB8FFFFFF;
    public static final int LOCK_TINT         = 0xFFE94F88;
    public static final int UNLOCK_TINT       = 0xFF7BE0A4;

    // Page buttons
    public static final int PAGE_BTN_FILL          = 0x80616B78;
    public static final int PAGE_BTN_FILL_HOVER    = 0xC8DCEBFF;
    public static final int PAGE_BTN_FILL_DISABLED = 0x40555566;
    public static final int PAGE_BTN_RING          = 0xA8FFFFFF;
    public static final int PAGE_BTN_ICON          = 0xFFFFFFFF;
    public static final int PAGE_BTN_ICON_DISABLED = 0x88FFFFFF;

    // Edit button
    public static final int EDIT_TEXT       = 0xFFFFFFFF;

    // Text
    public static final int TEXT_LABEL      = 0xFFFFFFFF;
    public static final int TEXT_LABEL_LINK = 0xFFFFE3A3;
    public static final int TEXT_KEYBIND    = 0xFFDCEBFF;
    public static final int TEXT_PATH_DIM   = 0xFFB7AFC9;
    public static final int TEXT_PATH_CUR   = 0xFFFFC857;
    public static final int TEXT_PAGE       = 0xFFFF8AB4;
    public static final int TEXT_STOP       = 0xFFFFE6B5;
    public static final int TEXT_SEPARATOR  = 0xFF7E6A9E;

    // ---- Helpers ----
    /**
     * Multiply an ARGB color by a hover tint when {@code hover} is true,
     * otherwise return the base color. Used for sliced backgrounds.
     */
    public static int hoverize(int base, int hover, boolean active) {
        return active ? hover : base;
    }
}
