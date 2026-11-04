package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.molang.MolangWatchRegistry;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapabilityBridge;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.client.entity.GeoEntity;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.micaftic.morpher.core.api.client.HudOverlay;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class AnimationDebugOverlay {

    private static final MolangWatchRegistry MOLANG_WATCH = new MolangWatchRegistry();
    private static final ReferenceArrayList<String> DEBUG_LINES = new ReferenceArrayList<>();
    private static WeakReference<GeoEntity<?>> activeModel = null;

    private static boolean enabled = false;
    public static void toggle() { enabled = !enabled; }
    public static boolean isEnabled() { return enabled; }
    public static HudOverlay createOverlay() {
        return (guiGraphics, font, partialTick, screenWidth, screenHeight) -> {
            renderOverlay(font, guiGraphics, screenWidth, screenHeight);
        };
    }

    public static MolangWatchRegistry getMolangWatch() {
        return MOLANG_WATCH;
    }

    public static boolean isDebugActive() {
        return getActiveModel() != null;
    }

    public static boolean tryUpdateFromHitResult() {
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (hitResult instanceof EntityHitResult) {
            return tryUpdateFromEntity(((EntityHitResult) hitResult).getEntity());
        }
        return tryUpdateFromLocalPlayer();
    }

    public static boolean tryUpdateFromLocalPlayer() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent((cap) -> setActiveModel(cap));
            return true;
        }
        clearActiveModel();
        return false;
    }

    public static boolean tryUpdateFromEntity(Entity entity) {
        Optional<?> capability;
        if (entity instanceof Player) {
            capability = PlayerCapability.get(entity);
        } else if (TouhouLittleMaidCompat.isMaidEntity(entity)) {
            capability = MaidCapabilityBridge.get(entity);
        } else if (entity instanceof Projectile) {
            capability = ProjectileCapability.get(entity);
        } else {
            capability = VehicleCapability.get(entity);
        }
        return capability.map(cap -> {
            setActiveModel((GeoEntity<?>) cap);
            return true;
        }).orElseGet(() -> {
            clearActiveModel();
            return false;
        });
    }

    public static void setActiveModel(GeoEntity<?> geoEntity) {
        clearActiveModel();
        activeModel = new WeakReference<>(geoEntity);
        geoEntity.setBoneLookup(MOLANG_WATCH);
        Entity entity = geoEntity.getEntity();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            MutableComponent mutableComponentAppend = Component.translatable("message.sparkle_morpher.model.debug_animation.true").append(" -> ");
            Component customName = entity.getCustomName();
            Objects.requireNonNull(entity);
            localPlayer.sendSystemMessage(mutableComponentAppend.append(Objects.requireNonNullElseGet(customName, entity::getDisplayName)));
        }
    }

    public static void clearActiveModel() {
        if (activeModel != null) {
            GeoEntity<?> geoEntity = activeModel.get();
            if (geoEntity != null) {
                geoEntity.setBoneLookup(null);
            }
            activeModel = null;
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) {
                localPlayer.sendSystemMessage(Component.translatable("message.sparkle_morpher.model.debug_animation.false"));
            }
        }
    }

    public static void addDebugLine(String str) {
        DEBUG_LINES.add(0, str);
    }

    public static void clearDebugLines() {
        DEBUG_LINES.clear();
    }

    @Nullable
    public static GeoEntity<?> getActiveModel() {
        if (activeModel != null) {
            GeoEntity<?> geoEntity = activeModel.get();
            if (geoEntity != null && geoEntity.isDebugMode()) {
                return geoEntity;
            }
            clearActiveModel();
            return null;
        }
        return null;
    }

    public static void renderOverlay(Font font, GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        GeoEntity<?> geoEntity = getActiveModel();
        if (geoEntity == null) {
            return;
        }
        int[] currentY = {5};
        MOLANG_WATCH.forEachEntry((molangKey, molangValue) -> {
            renderDebugOverlay(font, guiGraphics, currentY, molangKey, molangValue, screenWidth, screenHeight);
        });
        DEBUG_LINES.forEach(str3 -> {
            IAnimationController controller = geoEntity.getAnimationData().getAnimationControllerByName(str3);
            renderDebugOverlay(font, guiGraphics, currentY, str3, controller != null ? controller.getCurrentAnimation() : "(N/A)", screenWidth, screenHeight);
        });
    }

    public static void renderDebugOverlay(Font font, GuiGraphics guiGraphics, int[] currentY, String key, String value, int screenWidth, int screenHeight) {
        if ((currentY[0] - 5) % 20 == 0) {
            guiGraphics.fill(2, currentY[0] - 1, screenWidth, currentY[0] + 9, -1068478384);
        } else {
            guiGraphics.fill(2, currentY[0] - 1, screenWidth, currentY[0] + 9, -1068474288);
        }
        guiGraphics.drawString(font, key, 5, currentY[0], 16777215);
        guiGraphics.drawString(font, value, screenWidth / 2, currentY[0], 16777215);
        currentY[0] = currentY[0] + 10;
    }
}
