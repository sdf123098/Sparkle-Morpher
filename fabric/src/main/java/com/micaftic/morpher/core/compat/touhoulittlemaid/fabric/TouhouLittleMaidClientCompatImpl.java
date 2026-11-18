package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidAnimationController;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.util.InputUtil;
import dev.spm.core.scene.MolangValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Client-only Touhou Little Maid integration (Fabric platform implementation).
 */
public final class TouhouLittleMaidClientCompatImpl {

    private TouhouLittleMaidClientCompatImpl() {
    }

    public static void registerMaidAnimStates(HostMolangSink sink) {
        sink.var("is_begging", e -> MolangValue.bool(TouhouLittleMaidAccess.isBegging(e.getAnimatable().getEntity())));
        sink.var("is_sitting", e -> MolangValue.bool(TouhouLittleMaidAccess.isSitting(e.getAnimatable().getEntity())));
        sink.var("has_backpack", e -> MolangValue.bool(TouhouLittleMaidAccess.hasBackpack(e.getAnimatable().getEntity())));
        sink.var("favorability_point", e -> MolangValue.number(TouhouLittleMaidAccess.getFavorability(e.getAnimatable().getEntity())));
        sink.var("favorability_level", e -> MolangValue.number(TouhouLittleMaidAccess.getFavorabilityLevel(e.getAnimatable().getEntity())));
        sink.var("task_id", e -> MolangValue.text(TouhouLittleMaidAccess.getTaskId(e.getAnimatable().getEntity())));
        sink.var("schedule", e -> MolangValue.text(TouhouLittleMaidAccess.getSchedule(e.getAnimatable().getEntity())));
        sink.var("activity", e -> MolangValue.text(TouhouLittleMaidAccess.getActivity(e.getAnimatable().getEntity())));
        sink.var("gomoku_win_count", e -> MolangValue.number(TouhouLittleMaidAccess.getGomokuWinCount(e.getAnimatable().getEntity())));
        sink.var("gomoku_rank", e -> MolangValue.number(TouhouLittleMaidAccess.getGomokuRank(e.getAnimatable().getEntity())));
        sink.var("game_statue", e -> MolangValue.text(TouhouLittleMaidAccess.getGameState(e.getAnimatable().getEntity())));
        sink.var("backpack_type", e -> MolangValue.text(TouhouLittleMaidAccess.getBackpackType(e.getAnimatable().getEntity())));
        sink.var("is_entity", e -> MolangValue.bool(TouhouLittleMaidAccess.isRenderState(e.getAnimatable().getEntity(), "ENTITY")));
        sink.var("is_statue", e -> MolangValue.bool(TouhouLittleMaidAccess.isRenderState(e.getAnimatable().getEntity(), "STATUE")));
        sink.var("is_garage_kit", e -> MolangValue.bool(TouhouLittleMaidAccess.isRenderState(e.getAnimatable().getEntity(), "GARAGE_KIT")));
        sink.var("show_item", e -> MolangValue.text(TouhouLittleMaidAccess.getBackpackShowItem(e.getAnimatable().getEntity())));
    }

    public static HostPlayState.Decision handleMaidInteraction(HostActionEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        if (event.getAnimatable() instanceof IPreviewAnimatable) {
            return null;
        }
        if (TouhouLittleMaidAccess.isSit(entity)) {
            return switch (TouhouLittleMaidAccess.getJoyType(entity)) {
                case "Gomoku" -> IAnimationPredicate.playLoopAnimation(event, "gomoku");
                case "BookShelf" -> IAnimationPredicate.playLoopAnimation(event, "bookshelf");
                case "Computer" -> IAnimationPredicate.playLoopAnimation(event, "computer");
                case "Keyboard" -> IAnimationPredicate.playLoopAnimation(event, "keyboard");
                case "OnHomeMeal" -> IAnimationPredicate.playLoopAnimation(event, "picnic");
                default -> null;
            };
        }
        if (TouhouLittleMaidAccess.isChair(entity)) {
            return IAnimationPredicate.playLoopAnimation(event, "chair");
        }
        if (TouhouLittleMaidAccess.isBroom(entity)) {
            return IAnimationPredicate.playLoopAnimation(event, "broom");
        }
        return null;
    }

    public static boolean isMaidChatAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !TouhouLittleMaidAccess.isOwnedBy(hit.getEntity(), minecraft.player)) {
            return false;
        }
        return MaidCapability.get(hit.getEntity()).map(cap -> cap.isModelReady()
                && cap.getModelAssembly() != null
                && cap.getModelAssembly().getPresentationCapabilities().playerActionControlsAvailable()
                && cap.getModelAssembly().getModelData() != null
                && (!cap.getModelAssembly().getModelData().getModelProperties().getExtraAnimation().isEmpty()
                || !cap.getModelAssembly().getModelData().getModelProperties().getExtraAnimationClassify().isEmpty()))
                .orElse(false);
    }

    public static void openMaidChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof EntityHitResult hit)) return;
        MaidCapability.get(hit.getEntity()).ifPresent(cap -> {
            // MC 26.2: 当前屏幕状态移到 Minecraft.gui（Gui.screen()）
            if (minecraft.gui.screen() == null && cap.getModelAssembly() != null
                    && cap.getModelAssembly().getPresentationCapabilities().playerActionControlsAvailable()) {
                InputUtil.setScreen(new UnifiedRouletteScreen(cap.getModelId(), cap.getModelAssembly(), cap));
            } else if (minecraft.gui.screen() instanceof UnifiedRouletteScreen) {
                InputUtil.setScreen(null);
            }
        });
    }

    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return MaidAnimationController.buildControllers(modelBundle, resourceBundle);
    }

    public static void playMaidAnimation(Entity entity, String str) {
        MaidCapability.get(entity).ifPresent(capability -> capability.executeMolang(str));
    }
}
