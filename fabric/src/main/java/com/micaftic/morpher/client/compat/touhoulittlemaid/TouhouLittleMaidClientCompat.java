package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.molang.TLMBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;
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

    @ExpectPlatform
    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidChatAvailable() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void openMaidChat() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void playMaidAnimation(Entity entity, String str) {
        throw new AssertionError();
    }
}
