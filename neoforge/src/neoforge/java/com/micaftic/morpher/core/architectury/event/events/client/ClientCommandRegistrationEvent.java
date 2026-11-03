package com.micaftic.morpher.core.architectury.event.events.client;

import com.mojang.brigadier.CommandDispatcher;
import com.micaftic.morpher.core.architectury.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * NeoForge-compatible subset of Architectury's client command registration event.
 */
public class ClientCommandRegistrationEvent {
    public static final Event<ClientCommandRegistration> EVENT = new Event<>();

    public static void fire(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        EVENT.fire(handler -> handler.register(castDispatcher(dispatcher), context));
    }

    @FunctionalInterface
    public interface ClientCommandRegistration {
        void register(CommandDispatcher<ClientCommandSourceStack> dispatcher, CommandBuildContext context);
    }

    public interface ClientCommandSourceStack {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandDispatcher<ClientCommandSourceStack> castDispatcher(CommandDispatcher<CommandSourceStack> dispatcher) {
        return (CommandDispatcher) dispatcher;
    }
}
