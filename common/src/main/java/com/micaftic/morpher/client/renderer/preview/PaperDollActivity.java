package com.micaftic.morpher.client.renderer.preview;

import com.micaftic.morpher.client.event.ClientTickEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;

/**
 * 经典 HUD 小人「动作」检测与空闲计时（路线图 §22.2 动作可见性 / auto-hide，1.2.6）。
 *
 * <p>动作集合与 {@code ControllerActionResolver} 的基础动作判定保持一致（不引入新语义）：
 * 移动、潜行、游泳、飞行 / 滑翔、攀爬、在水中、骑乘、使用物品、挥击、受击、睡眠。</p>
 *
 * <p>仅在客户端渲染线程访问，故空闲计时用普通静态字段；tick 计数取
 * {@link ClientTickEvent#getTickCount()}。</p>
 */
public final class PaperDollActivity {

    private static long lastActiveTick = Long.MIN_VALUE;

    private PaperDollActivity() {
    }

    /** 本地玩家当前是否处于任一「动作」中。 */
    public static boolean isActive(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.isUsingItem()
                || player.isShiftKeyDown()
                || player.isSprinting()
                || player.isSwimming()
                || player.isFallFlying()
                || player.onClimbable()
                || player.isInWater()
                || player.isPassenger()
                || player.swinging
                || player.hurtTime > 0) {
            return true;
        }
        if (player.getPose() == Pose.SLEEPING || player.getPose() == Pose.SWIMMING
                || player.getPose() == Pose.FALL_FLYING || player.getPose() == Pose.CROUCHING) {
            return true;
        }
        return player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
    }

    /** 记录一次「动作」发生（刷新空闲计时起点）。 */
    public static void markActive() {
        lastActiveTick = ClientTickEvent.getTickCount();
    }

    /** 距上次动作的秒数；从未有过动作时返回 {@link Double#MAX_VALUE}（立即视为空闲）。 */
    public static double idleSeconds() {
        if (lastActiveTick == Long.MIN_VALUE) {
            return Double.MAX_VALUE;
        }
        return (ClientTickEvent.getTickCount() - lastActiveTick) / 20.0d;
    }

    /** 模型切换 / 断线时重置空闲计时，避免陈旧状态。 */
    public static void reset() {
        lastActiveTick = Long.MIN_VALUE;
    }
}
