package com.micaftic.morpher.command;

import com.micaftic.morpher.command.subcommands.client.CacheCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;


public class OpenYSMClientCommand {

    public static <S> void registerClientCommands(CommandDispatcher<S> commandDispatcher) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("openysm");

        root.then(CacheCommand.register());

        commandDispatcher.register(root);
    }
}
