package com.micaftic.morpher.client.renderer.preview;

/**
 * 经典 HUD 小人布局解析（路线图 §22.2 anchor，1.2.6）。
 *
 * <p>把「锚点 + 相对偏移」解析为屏幕坐标，供 HUD 小人定位。{@code TOP_LEFT} + 偏移
 * 与历史「绝对像素位置」完全等价（默认值即历史行为）。偏移方向：{@code TOP}/{@code LEFT}
 * 为向内偏移量，{@code BOTTOM}/{@code RIGHT} 亦为距该边的向内距离。</p>
 *
 * <p>纯数学、无 Minecraft 依赖，便于单测。</p>
 */
public final class PaperDollLayout {

    /** 九宫格锚点。 */
    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    /** 解析后的屏幕坐标（小人包围盒左上角）。 */
    public record Position(int x, int y) {
    }

    private PaperDollLayout() {
    }

    /**
     * 解析锚点 + 偏移到屏幕坐标。
     *
     * @param anchor       锚点；null 视作 {@link Anchor#TOP_LEFT}（历史行为）
     * @param screenWidth  当前 GUI 缩放宽
     * @param screenHeight 当前 GUI 缩放高
     * @param offsetX      相对锚点的水平偏移
     * @param offsetY      相对锚点的垂直偏移
     * @param dollWidth    小人包围盒宽
     * @param dollHeight   小人包围盒高
     */
    public static Position resolve(Anchor anchor, int screenWidth, int screenHeight, int offsetX, int offsetY, int dollWidth, int dollHeight) {
        Anchor resolved = anchor == null ? Anchor.TOP_LEFT : anchor;
        int column = resolved.ordinal() % 3; // 0=left 1=center 2=right
        int row = resolved.ordinal() / 3;    // 0=top  1=middle 2=bottom
        int x;
        if (column == 0) {
            x = offsetX;
        } else if (column == 1) {
            x = (screenWidth - dollWidth) / 2 + offsetX;
        } else {
            x = screenWidth - dollWidth - offsetX;
        }
        int y;
        if (row == 0) {
            y = offsetY;
        } else if (row == 1) {
            y = (screenHeight - dollHeight) / 2 + offsetY;
        } else {
            y = screenHeight - dollHeight - offsetY;
        }
        return new Position(x, y);
    }

    /**
     * 由实际屏幕坐标反解析回相对锚点的偏移（供布局编辑器拖拽后写回配置）。
     * 与 {@link #resolve} 互为逆运算。
     */
    public static Position toOffset(Anchor anchor, int screenWidth, int screenHeight, int x, int y, int dollWidth, int dollHeight) {
        Anchor resolved = anchor == null ? Anchor.TOP_LEFT : anchor;
        int column = resolved.ordinal() % 3;
        int row = resolved.ordinal() / 3;
        int offsetX = switch (column) {
            case 1 -> x - (screenWidth - dollWidth) / 2;
            case 2 -> screenWidth - dollWidth - x;
            default -> x;
        };
        int offsetY = switch (row) {
            case 1 -> y - (screenHeight - dollHeight) / 2;
            case 2 -> screenHeight - dollHeight - y;
            default -> y;
        };
        return new Position(offsetX, offsetY);
    }
}
