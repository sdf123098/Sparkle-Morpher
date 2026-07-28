package com.micaftic.morpher.command.subcommands.client;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.util.YSMMessageFormatter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CacheCommand {

    public static <S> LiteralArgumentBuilder<S> register() {
        return LiteralArgumentBuilder.<S>literal("cache")
                .then(LiteralArgumentBuilder.<S>literal("dump")
                        .executes(CacheCommand::dumpCache));
    }

    private static <S> int dumpCache(CommandContext<S> context) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }

        player.sendSystemMessage(YSMMessageFormatter.withPrefix(Component.literal("开始解析并导出客户端缓存模型...")));

        ClientModelManager.exportAllCachedModels(null, exportResult -> {
            if (exportResult.getMessage() != null) {
                player.sendSystemMessage(YSMMessageFormatter.withPrefix(exportResult.getMessage()));
            }
            if (exportResult.isSuccess()) {
                player.sendSystemMessage(Component.translatable("commands.sparkle_morpher.export.success", exportResult.getFilePath()));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
