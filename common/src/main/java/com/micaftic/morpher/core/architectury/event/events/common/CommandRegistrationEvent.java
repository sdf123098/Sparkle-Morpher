package com.micaftic.morpher.core.architectury.event.events.common;

import com.mojang.brigadier.CommandDispatcher;
import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Fabric-backed subset of Architectury's command registration event.
 */
public class CommandRegistrationEvent {
    public static final Event<CommandRegistration> EVENT = new Event<>();

    static {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                EVENT.fire(handler -> handler.register(dispatcher, context, selection)));
    }

    @FunctionalInterface
    public interface CommandRegistration {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection);
    }
}
