package com.micaftic.morpher.event.api;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class SpecialPlayerRenderEvent {

    public static final Event<RenderHandler> EVENT = EventFactory.createEventResult();

    @FunctionalInterface
    public interface RenderHandler {
        EventResult onRender(SpecialPlayerRenderEvent event);
    }

    public static EventResult post(SpecialPlayerRenderEvent event) {
        return EVENT.invoker().onRender(event);
    }

    private final Player player;

    private final CustomPlayerEntity customPlayer;

    private final String modelId;

    @Nullable
    private ResourceLocation textureLocation;

    public SpecialPlayerRenderEvent() {
        this.player = null;
        this.customPlayer = null;
        this.modelId = null;
    }

    public SpecialPlayerRenderEvent(Player player, CustomPlayerEntity customPlayer, String str) {
        this.player = player;
        this.customPlayer = customPlayer;
        this.modelId = str;
    }

    public Player getPlayer() {
        return this.player;
    }

    public CustomPlayerEntity getCustomPlayer() {
        return this.customPlayer;
    }

    public String getModelId() {
        return this.modelId;
    }

    @Nullable
    public ResourceLocation getTextureLocation() {
        return this.textureLocation;
    }

    public void setTextureLocation(@Nullable ResourceLocation resourceLocation) {
        this.textureLocation = resourceLocation;
    }
}