package com.micaftic.morpher.event.api;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.core.architectury.event.Event;
import com.micaftic.morpher.core.architectury.event.EventFactory;
import com.micaftic.morpher.core.architectury.event.EventResult;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class SpecialPlayerRenderEvent {

    public static final Event<RenderHandler> EVENT = EventFactory.createEventResult();

    @FunctionalInterface
    public interface RenderHandler {
        EventResult onRender(SpecialPlayerRenderEvent event);
    }

    public static EventResult post(SpecialPlayerRenderEvent event) {
        return EVENT.fireEventResult(handler -> handler.onRender(event));
    }

    private final Player player;

    private final CustomPlayerEntity customPlayer;

    private final String modelId;

    @Nullable
    private Identifier textureLocation;

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
    public Identifier getTextureLocation() {
        return this.textureLocation;
    }

    public void setTextureLocation(@Nullable Identifier Identifier) {
        this.textureLocation = Identifier;
    }
}
