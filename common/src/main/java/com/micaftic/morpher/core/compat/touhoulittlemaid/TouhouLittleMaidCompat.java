package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.molang.TLMBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

public final class TouhouLittleMaidCompat {

    private TouhouLittleMaidCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isLoaded();
    }

    public static boolean isMaidEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidEntity(entity);
    }

    public static boolean isMaidRideable(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidRideable(entity);
    }

    public static boolean isSimplePlanesEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isSimplePlanesEntity(entity);
    }

    public static boolean isImmersiveAircraftEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isImmersiveAircraftEntity(entity);
    }

    public static boolean isMaidItem(Item item) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidItem(item);
    }

    public static String getMaidEntityId(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.getMaidEntityId(entity);
    }

    public static boolean isMaidSitting(LivingEntity livingEntity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidSitting(livingEntity);
    }

    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.registerMaidAnimStates(tlmBinding);
    }

    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.handleMaidInteraction(event, livingEntity, entity);
    }

    public static boolean isMaidChatAvailable() {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.isMaidChatAvailable();
    }

    public static void openMaidChat() {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.openMaidChat();
    }

    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidCompatImpl.buildControllers(modelBundle, resourceBundle);
    }
}
