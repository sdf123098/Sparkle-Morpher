package com.micaftic.morpher.core.gui;

/**
 * Layout constants and the color palette for the unified animation
 * roulette. All four Sparkle Morpher subprojects ship an identical copy
 * of this class so visual tweaks stay in lockstep.
 */
public final class RouletteTheme {

    private RouletteTheme() {
    }

    // ---- Layout (all values are pixel offsets from the screen center) ----
    public static final float WHEEL_INNER_R = 22.0f;
    public static final float WHEEL_GEAR_R = 46.0f;
    public static final float WHEEL_OUTER_R = 100.0f;
    public static final float WHEEL_OUTLINE_R = 101.5f;
    public static final float GAP_ANGLE_PADDING = 0.02f;

    public static final float PAGE_BTN_OFFSET = 128.0f;
    public static final float PAGE_BTN_RADIUS = 16.0f;

    public static final int EDIT_BTN_WIDTH = 80;
    public static final int EDIT_BTN_HEIGHT = 18;
    public static final int EDIT_BTN_Y_OFFSET = 118;

    public static final int PATH_Y_OFFSET = -118;
    public static final int PAGE_INDICATOR_Y_OFFSET = 108;

    public static final int ICON_SIZE_GEAR = 14;
    public static final int ICON_SIZE_LOCK = 28;
    public static final int ICON_SIZE_ARROW = 14;

    // ---- Palette (ARGB) ----
    // Background veil
    public static final int BG_VEIL = 0x66050009;

    // Wheel slices — main animation ring (outer)
    public static final int SLICE_IDLE        = 0xCC2A2342;
    public static final int SLICE_HOVER       = 0xFFFF8AB4;
    public static final int SLICE_SUBMENU     = 0xCC6E2F66;
    public static final int SLICE_SUBMENU_HOV = 0xFFFFC857;
    public static final int SLICE_EMPTY       = 0x301B1530;
    public static final int SLICE_OUTLINE     = 0xFFFFE6B5;

    // Wheel inner ring (gear cluster)
    public static final int GEAR_IDLE         = 0xCC3D2D5E;
    public static final int GEAR_HOVER        = 0xFFFFC857;

    // Central lock pip
    public static final int CENTER_FILL       = 0xE61E1A2C;
    public static final int CENTER_RING       = 0xFFFFC857;
    public static final int LOCK_TINT         = 0xFFE94F88;
    public static final int UNLOCK_TINT       = 0xFF7BE0A4;

    // Page buttons
    public static final int PAGE_BTN_FILL          = 0xCC2D2240;
    public static final int PAGE_BTN_FILL_HOVER    = 0xFFFF8AB4;
    public static final int PAGE_BTN_FILL_DISABLED = 0x40555566;
    public static final int PAGE_BTN_RING          = 0xFFFFC857;
    public static final int PAGE_BTN_ICON          = 0xFFFFFFFF;
    public static final int PAGE_BTN_ICON_DISABLED = 0x88FFFFFF;

    // Edit button (uses FlatColorButton, so just text)
    public static final int EDIT_TEXT       = 0xFFFFFFFF;

    // Text
    public static final int TEXT_LABEL      = 0xFFFFE6B5;
    public static final int TEXT_LABEL_LINK = 0xFFFFC857;
    public static final int TEXT_KEYBIND    = 0xFFFFC857;
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
