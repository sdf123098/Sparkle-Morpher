package com.micaftic.morpher.command;

import com.micaftic.morpher.command.subcommands.client.CacheCommand;
import com.micaftic.morpher.util.YSMMessageFormatter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;


public class OpenYSMClientCommand {

    public static void registerClientCommands(CommandDispatcher<net.minecraft.commands.CommandSourceStack> commandDispatcher) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root = LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("openysm")
                .requires(source -> true);

        root.then(CacheCommand.register());

        commandDispatcher.register(root);
    }
}