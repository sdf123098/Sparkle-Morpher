package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.molang.TLMBinding;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidAnimationController;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Client-only Touhou Little Maid integration (Fabric platform implementation).
 *
 * <p>This class may reference {@link Minecraft} freely: it is only reachable
 * from {@code TouhouLittleMaidClientCompat}, which the server-side
 * interaction path never loads.</p>
 */
public final class TouhouLittleMaidClientCompatImpl {

    private TouhouLittleMaidClientCompatImpl() {
    }

    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        tlmBinding.livingEntityVar("is_begging", ctx -> TouhouLittleMaidAccess.isBegging(ctx.entity()));
        tlmBinding.livingEntityVar("is_sitting", ctx -> TouhouLittleMaidAccess.isSitting(ctx.entity()));
        tlmBinding.livingEntityVar("has_backpack", ctx -> TouhouLittleMaidAccess.hasBackpack(ctx.entity()));
        tlmBinding.livingEntityVar("favorability_point", ctx -> TouhouLittleMaidAccess.getFavorability(ctx.entity()));
        tlmBinding.livingEntityVar("favorability_level", ctx -> TouhouLittleMaidAccess.getFavorabilityLevel(ctx.entity()));
        tlmBinding.livingEntityVar("task_id", ctx -> TouhouLittleMaidAccess.getTaskId(ctx.entity()));
        tlmBinding.livingEntityVar("schedule", ctx -> TouhouLittleMaidAccess.getSchedule(ctx.entity()));
        tlmBinding.livingEntityVar("activity", ctx -> TouhouLittleMaidAccess.getActivity(ctx.entity()));
        tlmBinding.livingEntityVar("gomoku_win_count", ctx -> TouhouLittleMaidAccess.getGomokuWinCount(ctx.entity()));
        tlmBinding.livingEntityVar("gomoku_rank", ctx -> TouhouLittleMaidAccess.getGomokuRank(ctx.entity()));
        tlmBinding.livingEntityVar("game_statue", ctx -> TouhouLittleMaidAccess.getGameState(ctx.entity()));
        tlmBinding.livingEntityVar("backpack_type", ctx -> TouhouLittleMaidAccess.getBackpackType(ctx.entity()));
        tlmBinding.livingEntityVar("is_entity", ctx -> TouhouLittleMaidAccess.isRenderState(ctx.entity(), "ENTITY"));
        tlmBinding.livingEntityVar("is_statue", ctx -> TouhouLittleMaidAccess.isRenderState(ctx.entity(), "STATUE"));
        tlmBinding.livingEntityVar("is_garage_kit", ctx -> TouhouLittleMaidAccess.isRenderState(ctx.entity(), "GARAGE_KIT"));
        tlmBinding.livingEntityVar("show_item", ctx -> TouhouLittleMaidAccess.getBackpackShowItem(ctx.entity()));
    }

    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
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
                && (!cap.getModelAssembly().getModelData().getModelProperties().getExtraAnimation().isEmpty()
                || !cap.getModelAssembly().getModelData().getModelProperties().getExtraAnimationClassify().isEmpty()))
                .orElse(false);
    }

    public static void openMaidChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof EntityHitResult hit)) return;
        MaidCapability.get(hit.getEntity()).ifPresent(cap -> {
            if (minecraft.screen == null && cap.getModelAssembly() != null) {
                InputUtil.setScreen(new UnifiedRouletteScreen(cap.getModelId(), cap.getModelAssembly(), cap));
            } else if (minecraft.screen instanceof UnifiedRouletteScreen) {
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
