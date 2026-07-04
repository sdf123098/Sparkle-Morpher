package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;

import java.util.function.Consumer;

/**
 * bbmodel / figura 导入模型（{@code VANILLA_HUMANOID} 档位）的动作控制器装配。
 *
 * <p>历史上这里只装了 {@link ImportedVanillaPoseController} 一个程序化姿态控制器，
 * 导致 bbmodel 在世界里只能复刻原版玩家姿态，无法播放关键帧形式的丰富基础动作。</p>
 *
 * <p>现在改为「授权关键帧栈 + vanilla 兜底」的组合：
 * <ul>
 *   <li>{@link PlayerAnimationController#buildControllers} 提供完整的关键帧驱动栈
 *       （main 状态机、hold/use/swing、armor 等），用来播放 bbmodel 专属动作预设
 *       （见 {@code BuiltinBbmodelActionPreset}）中的 fly/elytra/swim/sneak/use 等动作；</li>
 *   <li>{@link ImportedVanillaPoseController}（{@code fallbackOnly=true}）作为兜底，
 *       只对没有被关键帧动画驱动的骨骼/状态补上原版姿态。</li>
 * </ul>
 * 手部定位与女仆逻辑仍按 {@code VANILLA_HUMANOID} 处理，不受影响。</p>
 */
public class VanillaHumanoidActionProvider implements PlayerActionProvider {

    @Override
    public Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        Consumer<CustomPlayerEntity> authored = PlayerAnimationController.buildControllers(modelBundle, resourceBundle);
        return entity -> {
            authored.accept(entity);
            entity.addAnimationController(new ImportedVanillaPoseController(
                    UnifiedPlayerActionController.VANILLA_FALLBACK_CONTROLLER_KEY, true));
        };
    }
}
