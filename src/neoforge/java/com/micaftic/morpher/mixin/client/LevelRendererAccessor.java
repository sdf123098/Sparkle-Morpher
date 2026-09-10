package com.micaftic.morpher.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 {@code LevelRenderer.targets}（帧图目标 bundle），供
 * {@code WorldRendererMixin} 把 GPU 蒙皮 pass 挂进 MC 的 framegraph。
 *
 * <p>{@code LevelTargetBundle.main} 本身是 public 字段，只有持有它的
 * {@code targets} 字段是 private，故只需这一个 accessor。</p>
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("targets")
    LevelTargetBundle sparkleMorpher$getTargets();
}
