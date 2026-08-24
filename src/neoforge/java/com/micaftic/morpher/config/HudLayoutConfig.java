package com.micaftic.morpher.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 单个 HUD 的无级布局配置（横向/纵向位置 + 缩放 + yaw 朝向）。
 *
 * <p>经典 HUD 与现代 HUD 各持有一份独立实例，互不干扰；通用布局编辑器
 * （{@code client.gui.HudLayoutScreen}）以本对象为读写目标，因此两边共享同一套
 * 编辑交互，只是落在各自的配置键上。
 */
public final class HudLayoutConfig {

    public final ModConfigSpec.IntValue posX;
    public final ModConfigSpec.IntValue posY;
    public final ModConfigSpec.DoubleValue scale;
    public final ModConfigSpec.DoubleValue yawOffset;

    private HudLayoutConfig(ModConfigSpec.IntValue posX, ModConfigSpec.IntValue posY,
                            ModConfigSpec.DoubleValue scale, ModConfigSpec.DoubleValue yawOffset) {
        this.posX = posX;
        this.posY = posY;
        this.scale = scale;
        this.yawOffset = yawOffset;
    }

    /**
     * 用统一键名前缀定义一组布局键。空前缀保持旧键名（PlayerPosX/...）不变，
     * 用于经典 HUD 以兼容既有配置文件；现代 HUD 用 "Modern" 前缀得到独立键名。
     */
    public static HudLayoutConfig define(ModConfigSpec.Builder builder, String keyPrefix,
                                         int defaultX, int defaultY, double defaultScale, double defaultYaw) {
        return new HudLayoutConfig(
                builder.defineInRange(keyPrefix + "PlayerPosX", defaultX, 0, Integer.MAX_VALUE),
                builder.defineInRange(keyPrefix + "PlayerPosY", defaultY, 0, Integer.MAX_VALUE),
                builder.defineInRange(keyPrefix + "PlayerScale", defaultScale, 8.0d, 360.0d),
                builder.defineInRange(keyPrefix + "PlayerYawOffset", defaultYaw, -Double.MAX_VALUE, Double.MAX_VALUE));
    }

    public int getX() {
        return posX.get();
    }

    public int getY() {
        return posY.get();
    }

    public float getScale() {
        return scale.get().floatValue();
    }

    public float getYaw() {
        return yawOffset.get().floatValue();
    }

    public void set(int x, int y, float scale, float yaw) {
        posX.set(x);
        posY.set(y);
        this.scale.set((double) scale);
        yawOffset.set((double) yaw);
    }

    public void save() {
        posX.save();
        posY.save();
        scale.save();
        yawOffset.save();
    }
}
