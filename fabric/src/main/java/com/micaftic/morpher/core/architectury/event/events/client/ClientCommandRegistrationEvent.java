package com.micaftic.morpher.core.architectury.event.events.client;

import com.mojang.brigadier.CommandDispatcher;
import com.micaftic.morpher.core.architectury.event.Event;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

/**
 * Fabric-backed subset of Architectury's client command registration event.
 */
public class ClientCommandRegistrationEvent {
    public static final Event<ClientCommandRegistration> EVENT = new Event<>();

    static {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                EVENT.fire(handler -> handler.register(castDispatcher(dispatcher), context)));
    }

    @FunctionalInterface
    public interface ClientCommandRegistration {
        void register(CommandDispatcher<ClientCommandSourceStack> dispatcher, CommandBuildContext context);
    }

    public interface ClientCommandSourceStack extends FabricClientCommandSource {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandDispatcher<ClientCommandSourceStack> castDispatcher(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        return (CommandDispatcher) dispatcher;
    }
}
