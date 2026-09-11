package com.micaftic.morpher.client.renderer.preview;

/**
 * 预览投影 / 鼠标旋转纯数学（路线图 §22.1 拆分支持，1.2.6 Slice A）。
 *
 * <p>从 {@code ModelPreviewRenderer} 外提：鼠标跟随旋转、死区、模型偏移与
 * yaw→鼠标 X 反算。这里刻意不含任何 Minecraft 运行时状态（仅 {@link Math}），
 * 以便用纯 JVM 单测覆盖（{@code PreviewMathTest}）。</p>
 */
public final class PreviewMath {

    /** 鼠标偏离预览中心时的最大 yaw 偏移（度）。 */
    public static final float MODEL_PREVIEW_MOUSE_YAW_DEGREES = 25.0f;

    /** 鼠标偏离预览中心时的最大 pitch 偏移（度）。 */
    public static final float MODEL_PREVIEW_MOUSE_PITCH_DEGREES = 15.0f;

    /** 预览鼠标旋转死区（归一化值）。 */
    public static final float MODEL_PREVIEW_MOUSE_DEADZONE = 0.08f;

    /** 预览鼠标旋转（不可变）。 */
    public record MouseRotation(float yaw, float pitch) {
    }

    /** 无旋转（禁用预览旋转时的常量，避免每次分配）。 */
    public static final MouseRotation NO_MOUSE_ROTATION = new MouseRotation(0.0f, 0.0f);

    private PreviewMath() {
    }

    /**
     * 由预览框与鼠标位置求鼠标跟随旋转。任一无效应条件（禁用旋转 / 框退化 / 鼠标未知）
     * 均返回 {@link #NO_MOUSE_ROTATION}。
     */
    public static MouseRotation mouseRotation(int left, int top, int right, int bottom, int mouseX, int mouseY, boolean disablePreviewRotation) {
        if (disablePreviewRotation || right <= left || bottom <= top || mouseX == Integer.MIN_VALUE || mouseY == Integer.MIN_VALUE) {
            return NO_MOUSE_ROTATION;
        }
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        float halfWidth = Math.max(1.0f, (right - left) * 0.5f);
        float halfHeight = Math.max(1.0f, (bottom - top) * 0.5f);
        float normalizedYaw = applyDeadzone(clamp((centerX - mouseX) / halfWidth, -1.0f, 1.0f));
        float normalizedPitch = applyDeadzone(clamp((centerY - mouseY) / halfHeight, -1.0f, 1.0f));
        return new MouseRotation(
                normalizedYaw * MODEL_PREVIEW_MOUSE_YAW_DEGREES,
                normalizedPitch * MODEL_PREVIEW_MOUSE_PITCH_DEGREES
        );
    }

    /** 死区内归零，否则原值。 */
    public static float applyDeadzone(float value) {
        return Math.abs(value) < MODEL_PREVIEW_MOUSE_DEADZONE ? 0.0f : value;
    }

    /** 把预览框内的屏幕原点换算为模型空间偏移（相对框中心，按缩放归一）。 */
    public static float toModelOffset(float origin, int start, int end, float scale) {
        return (origin - ((start + end) * 0.5f)) / Math.max(1.0f, scale);
    }

    /** 由 yaw 反算等价的鼠标 X（供 vanilla 回退路径复用鼠标跟随语义）。 */
    public static int mouseXFromYaw(float originX, float yaw) {
        return Math.round(originX - ((float) Math.tan((yaw - 180.0f) / 20.0f) * 40.0f));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
