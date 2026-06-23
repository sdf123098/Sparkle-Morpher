package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import com.micaftic.morpher.client.animation.molang.TLMBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

public final class TouhouLittleMaidCompatImpl {

    private TouhouLittleMaidCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isMaidEntity(Entity entity) {
        return false;
    }

    public static boolean isMaidRideable(Entity entity) {
        return false;
    }

    public static boolean isSimplePlanesEntity(Entity entity) {
        return false;
    }

    public static boolean isImmersiveAircraftEntity(Entity entity) {
        return false;
    }

    public static boolean isMaidItem(Item item) {
        return false;
    }

    public static String getMaidEntityId(Entity entity) {
        return "";
    }

    public static boolean isMaidSitting(LivingEntity livingEntity) {
        return false;
    }

    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        tlmBinding.livingEntityVar("is_begging", ctx -> false);
        tlmBinding.livingEntityVar("is_sitting", ctx -> false);
        tlmBinding.livingEntityVar("has_backpack", ctx -> false);
        tlmBinding.livingEntityVar("favorability_point", ctx -> 0);
        tlmBinding.livingEntityVar("favorability_level", ctx -> 0);
        tlmBinding.livingEntityVar("task_id", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("schedule", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("activity", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("gomoku_win_count", ctx -> 0);
        tlmBinding.livingEntityVar("gomoku_rank", ctx -> 1);
        tlmBinding.livingEntityVar("game_statue", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("backpack_type", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("is_entity", ctx -> true);
        tlmBinding.livingEntityVar("is_statue", ctx -> false);
        tlmBinding.livingEntityVar("is_garage_kit", ctx -> false);
        tlmBinding.livingEntityVar("show_item", ctx -> StringPool.EMPTY);
    }

    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        return null;
    }

    public static boolean isMaidChatAvailable() {
        return false;
    }

    public static void openMaidChat() {
    }

    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return null;
    }
}
