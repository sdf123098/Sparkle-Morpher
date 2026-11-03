package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.molang.TLMBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only Touhou Little Maid integration.
 *
 * <p>Everything that touches {@link net.minecraft.client.Minecraft} (and
 * therefore client-only classes such as {@code LocalPlayer}) must stay behind
 * the platform implementation. The shared compat classes are reachable from
 * server-side mixins ({@code MaidModelSync.handleInteraction}) and loading
 * them on a dedicated server must never resolve client classes — otherwise
 * the server crashes with {@code NoClassDefFoundError} during class
 * verification.</p>
 */
public final class TouhouLittleMaidClientCompat {

    private TouhouLittleMaidClientCompat() {
    }

    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.registerMaidAnimStates(tlmBinding);
    }

    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.handleMaidInteraction(event, livingEntity, entity);
    }

    public static boolean isMaidChatAvailable() {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.isMaidChatAvailable();
    }

    public static void openMaidChat() {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.openMaidChat();
    }

    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.buildControllers(modelBundle, resourceBundle);
    }

    public static void playMaidAnimation(Entity entity, String str) {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.playMaidAnimation(entity, str);
    }
}
