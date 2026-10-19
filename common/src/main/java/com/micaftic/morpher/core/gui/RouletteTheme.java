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
    public static final float WHEEL_INNER_R = 56.0f;
    public static final float WHEEL_GEAR_R = 76.0f;
    public static final float WHEEL_OUTER_R = 151.0f;
    public static final float WHEEL_OUTLINE_R = 151.0f;
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
    public static final int BG_VEIL = 0x5A171717;

    // Wheel slices - translucent frosted-glass plates.
    public static final int SLICE_IDLE        = 0x78363636;
    public static final int SLICE_HOVER       = 0xB85A5A5A;
    public static final int SLICE_SUBMENU     = 0x80404040;
    public static final int SLICE_SUBMENU_HOV = 0xC8585858;
    public static final int SLICE_EMPTY       = 0x22333333;
    public static final int SLICE_OUTLINE     = 0xA8FFFFFF;
    public static final int SLICE_INNER_GLOW  = 0x36FFFFFF;
    public static final int SLICE_SHADOW      = 0x3A000000;

    // Wheel inner ring (gear cluster)
    public static final int GEAR_IDLE         = 0x805A5A5A;
    public static final int GEAR_HOVER        = 0xD8FFFFFF;

    // Central lock pip
    public static final int CENTER_FILL       = 0xA02B2B2B;
    public static final int CENTER_RING       = 0xB8FFFFFF;
    public static final int LOCK_TINT         = 0xFFE05252;
    public static final int UNLOCK_TINT       = 0xFFF0F0F0;

    // Page buttons
    public static final int PAGE_BTN_FILL          = 0x80444444;
    public static final int PAGE_BTN_FILL_HOVER    = 0xC85A5A5A;
    public static final int PAGE_BTN_FILL_DISABLED = 0x40333333;
    public static final int PAGE_BTN_RING          = 0xA8FFFFFF;
    public static final int PAGE_BTN_ICON          = 0xFFFFFFFF;
    public static final int PAGE_BTN_ICON_DISABLED = 0x88FFFFFF;

    // Edit button
    public static final int EDIT_TEXT       = 0xFFFFFFFF;

    // Text
    public static final int TEXT_LABEL      = 0xFFFFFFFF;
    public static final int TEXT_LABEL_LINK = 0xFFF0F0F0;
    public static final int TEXT_KEYBIND    = 0xFFBBBBBB;
    public static final int TEXT_PATH_DIM   = 0xFF9A9A9A;
    public static final int TEXT_PATH_CUR   = 0xFFE05252;
    public static final int TEXT_PAGE       = 0xFFE05252;
    public static final int TEXT_STOP       = 0xFFF0F0F0;
    public static final int TEXT_SEPARATOR  = 0xFF777777;

    // ---- Helpers ----
    /**
     * Multiply an ARGB color by a hover tint when {@code hover} is true,
     * otherwise return the base color. Used for sliced backgrounds.
     */
    public static int hoverize(int base, int hover, boolean active) {
        return active ? hover : base;
    }
}
