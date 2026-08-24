package com.micaftic.morpher.core.architectury.event.events.common;

import com.micaftic.morpher.core.architectury.event.Event;
import com.micaftic.morpher.core.architectury.event.EventResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.function.BiFunction;

/**
 * MC 26.x: Fabric-backed EntityEvent.
 */
public class EntityEvent {
    public static final Event<BiFunction<Entity, Level, EventResult>> ADD = new Event<>();

    // Server side
    static {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) ->
            ADD.fire(h -> h.apply(entity, world))
        );
    }
}
