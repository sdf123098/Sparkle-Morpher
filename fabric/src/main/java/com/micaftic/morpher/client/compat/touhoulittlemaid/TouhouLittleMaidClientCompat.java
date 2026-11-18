package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only Touhou Little Maid integration.
 *
 * <p>Everything that touches {@link net.minecraft.client.Minecraft} (and
 * therefore client-only classes such as {@code LocalPlayer}) must stay behind
 * the platform implementation.</p>
 */
public final class TouhouLittleMaidClientCompat {

    private TouhouLittleMaidClientCompat() {
    }

    public static void registerMaidAnimStates(HostMolangSink sink) {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl.registerMaidAnimStates(sink);
    }

    public static HostPlayState.Decision handleMaidInteraction(HostActionEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
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
