package com.micaftic.morpher.event.api;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class SpecialPlayerRenderEvent {
    public static final SpecialPlayerRenderEvent INSTANCE = new SpecialPlayerRenderEvent();
    private static final java.util.List<RenderHandler> HANDLERS = new java.util.ArrayList<>();
    public static void register(RenderHandler handler) { HANDLERS.add(handler); }

    @FunctionalInterface
    public interface RenderHandler {
        boolean onRender(SpecialPlayerRenderEvent event);
    }

    public static boolean post(SpecialPlayerRenderEvent event) {
        for (RenderHandler h : HANDLERS) { if (h.onRender(event)) return true; }
        return false;
    }

    private final Player player;
    private final CustomPlayerEntity customPlayer;
    private final String modelId;
    @Nullable private ResourceLocation textureLocation;

    public SpecialPlayerRenderEvent() { this.player = null; this.customPlayer = null; this.modelId = null; }
    public SpecialPlayerRenderEvent(Player player, CustomPlayerEntity customPlayer, String str) { this.player = player; this.customPlayer = customPlayer; this.modelId = str; }
    public Player getPlayer() { return player; }
    public CustomPlayerEntity getCustomPlayer() { return customPlayer; }
    public String getModelId() { return modelId; }
    @Nullable public ResourceLocation getTextureLocation() { return textureLocation; }
    public void setTextureLocation(@Nullable ResourceLocation loc) { this.textureLocation = loc; }
}