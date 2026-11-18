package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Loader-neutral facade. The gate lives in {@link SlashBladeModState} and all
 * SlashBlade class references live in {@link SlashBladeBridge}; neither is
 * classloaded with SlashBlade absent.
 */
public final class SlashBladeCompat {

    private SlashBladeCompat() {
    }

    public static boolean isLoaded() {
        return SlashBladeModState.LOADED;
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return SlashBladeModState.LOADED && SlashBladeBridge.isSlashBladeItem(itemStack);
    }

    public static String getComboAnimName(HostActionEvent<? extends LivingAnimatable<?>> event) {
        if (!SlashBladeModState.LOADED || event == null || event.getAnimatable() == null) {
            return "";
        }
        return SlashBladeBridge.getComboAnimationName(event.getAnimatable().getEntity());
    }

    @Nullable
    public static HostPlayState.Decision handleSlashBladeAnim(LivingEntity livingEntity, HostActionEvent<? extends LivingAnimatable<?>> event, String str, CodedLoopMode loopType) {
        if (!SlashBladeModState.LOADED) {
            return null;
        }
        return SlashBladeBridge.handleSlashBladeAnim(livingEntity, event, str, loopType);
    }

    public static void registerControllerFunctions(HostMolangSink sink) {
        if (SlashBladeModState.LOADED) {
            SlashBladeBridge.registerBindings(sink);
        } else {
            sink.var("slashblade_animation", event -> MolangValue.text(""));
        }
    }

    public static boolean hasNewApi() {
        return SlashBladeModState.LOADED;
    }
}
