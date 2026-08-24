package com.micaftic.morpher.command.subcommands;

import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.event.CommandRegistry;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.model.format.ServerModelData;
import com.micaftic.morpher.util.PlayerDataSaveBridge;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.util.YSMMessageFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import com.micaftic.morpher.core.api.PlatformAPI;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ModelCommand {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create();

    private static final String MODEL_NAME = "model";

    private static final String LITERAL_RELOAD = "reload";

    private static final String SET_NAME = "set";

    private static final String DISABLE_NAME = "disable";

    private static final String TARGETS_NAME = "targets";

    private static final String MODEL_ID_NAME = "model_id";

    private static final String TEXTURE_ID_NAME = "texture_id";

    private static final String IGNORE_AUTH_NAME = "ignore_auth";

    private static final String PLAYERS_NAME = "players";

    private static final String ARG_VALUE = "value";

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        // /ysm model 对所有玩家可见：非管理员也能执行 disable 关闭自身模型展示（恢复原版）。
        // 管理子命令（reload / set）保留 OP 等级 2 门槛。
        LiteralArgumentBuilder<CommandSourceStack> model = Commands.literal(MODEL_NAME);
        model.then(Commands.literal(LITERAL_RELOAD).requires(commandSourceStack -> YSMMessageFormatter.hasCommandPermission(commandSourceStack, 2)).executes(ModelCommand::reloadAllPack));
        model.then(Commands.literal(DISABLE_NAME).then(Commands.argument(PLAYERS_NAME, EntityArgument.players()).then(Commands.argument(ARG_VALUE, BoolArgumentType.bool()).executes(ModelCommand::disableModel))));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal(SET_NAME).requires(commandSourceStack -> YSMMessageFormatter.hasCommandPermission(commandSourceStack, 2));
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets = Commands.argument(TARGETS_NAME, EntityArgument.players());
        RequiredArgumentBuilder<CommandSourceStack, String> modelId = Commands.argument(MODEL_ID_NAME, StringArgumentType.string()).suggests(CommandRegistry.MODEL_IDS);
        RequiredArgumentBuilder<CommandSourceStack, String> textureId = Commands.argument(TEXTURE_ID_NAME, StringArgumentType.string()).suggests(CommandRegistry.TEXTURE_IDS);
        RequiredArgumentBuilder<CommandSourceStack, Boolean> ignoreAuth = Commands.argument(IGNORE_AUTH_NAME, BoolArgumentType.bool());
        model.then(set.then(targets.then(modelId.then(textureId.executes(commandContext -> setModel(commandContext, false))))));
        model.then(set.then(targets.then(modelId.then(textureId.then(ignoreAuth.executes(ModelCommand::setModelIgnoreAuth))))));
        return model;
    }

    private static int setModelIgnoreAuth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setModel(context, BoolArgumentType.getBool(context, IGNORE_AUTH_NAME));
    }

    public static int setModel(CommandContext<CommandSourceStack> context, boolean ignoreAuth) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, TARGETS_NAME);
        String modelName = StringArgumentType.getString(context, MODEL_ID_NAME);
        String textureName = StringArgumentType.getString(context, TEXTURE_ID_NAME);
        ServerModelData info = ServerModelManager.getServerModelInfo().get(modelName);
        if (info == null) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.sparkle_morpher.export.not_exist", modelName), true);
            return Command.SINGLE_SUCCESS;
        }
        if (Objects.equals(textureName, "-")) {
            textureName = info.getLoadedModelData().getModelProperties().getDefaultTexture();
            if (StringUtils.isBlank(textureName) || !info.getModelInfo().getTextures().contains(textureName)) {
                textureName = info.getModelInfo().getTextures().get(0);
            }
        }
        if (ServerModelManager.getServerModelInfo().get(modelName).getModelInfo().getTextures().isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String finalTextureName = textureName;
        if (ignoreAuth) {
            targets.forEach(player -> ModelInfoCapability.get(player).ifPresent(cap -> {
                cap.setModelAndTexture(modelName, finalTextureName);
                cap.setMandatory(true);
                PlayerModelSelectionStore.saveCurrentSelection(player, cap);
                PlayerDataSaveBridge.save(player);
                context.getSource().sendSuccess(() -> Component.translatable("message.sparkle_morpher.model.set.success", modelName, player.getScoreboardName()), true);
            }));
            return Command.SINGLE_SUCCESS;
        }
        targets.forEach(player -> ModelInfoCapability.get(player).ifPresent(cap -> {
            AuthModelsCapability.get(player).ifPresent(authCap -> {
                if (!ServerModelManager.getAuthModels().contains(modelName) || authCap.containsModel(modelName)) {
                    cap.setModelAndTexture(modelName, finalTextureName);
                    cap.setMandatory(true);
                    PlayerModelSelectionStore.saveCurrentSelection(player, cap);
                    PlayerDataSaveBridge.save(player);
                    context.getSource().sendSuccess(() -> Component.translatable("message.sparkle_morpher.model.set.success", modelName, player.getScoreboardName()), true);
                    return;
                }
                context.getSource().sendSuccess(() -> Component.translatable("message.sparkle_morpher.model.set.need_auth", modelName, player.getScoreboardName()), true);
            });
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadAllPack(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("message.sparkle_morpher.model.reload.start"), true);
        StopWatch watch = StopWatch.createStarted();
        if (!ServerModelManager.loadModels(result -> {
            if (result.getErrorMessage() != null) {
                YSMMessageFormatter.sendServerMessage(context.getSource(), YSMMessageFormatter.withPrefix(result.getErrorMessage()), true);
            }
            if (result.isSuccess()) {
                YSMMessageFormatter.sendServerMessage(context.getSource(), Component.translatable("message.sparkle_morpher.model.reload.complete", Double.valueOf(watch.getTime(TimeUnit.MICROSECONDS) / 1000.0d)), true);
                watch.reset();
                watch.start();
            }
        }, data -> {
            watch.stop();
            if (!data.isEnabled()) {
                YSMMessageFormatter.sendServerMessage(context.getSource(), YSMMessageFormatter.withPrefix(data.getDisplayComponent()), true);
                return;
            }
            if (!data.getUuidComponentMap().isEmpty()) {
                for (Component component : data.getUuidComponentMap().values()) {
                    YSMMessageFormatter.sendServerMessage(context.getSource(), YSMMessageFormatter.withPrefix(component), true);
                }
                if (PlatformAPI.isServer()) {
                    YSMMessageFormatter.sendServerMessage(context.getSource(), Component.translatable("message.sparkle_morpher.model.sync.complete", Double.valueOf(watch.getTime(TimeUnit.MICROSECONDS) / 1000.0d)), true);
                }
            }
        })) {
            context.getSource().sendFailure(Component.translatable("message.sparkle_morpher.model.reload.in_progress"));
            return Command.SINGLE_SUCCESS;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int disableModel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String str;
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, PLAYERS_NAME);
        boolean bool = BoolArgumentType.getBool(context, ARG_VALUE);
        // 非管理员只能对自己的模型执行 disable（恢复原版）；操作其他玩家需要 OP 等级 2。
        boolean operator = YSMMessageFormatter.hasCommandPermission(context.getSource(), 2);
        if (!operator) {
            Entity sourceEntity = context.getSource().getEntity();
            UUID sourceUuid = sourceEntity != null ? sourceEntity.getUUID() : null;
            boolean selfOnly = targets.size() == 1 && sourceUuid != null && targets.iterator().next().getUUID().equals(sourceUuid);
            if (!selfOnly) {
                context.getSource().sendFailure(Component.translatable("commands.sparkle_morpher.model.disable.no_permission"));
                return Command.SINGLE_SUCCESS;
            }
        }
        if (bool) {
            str = "message.sparkle_morpher.model.disable.true";
        } else {
            str = "message.sparkle_morpher.model.disable.false";
        }
        String str2 = str;
        targets.forEach(player -> ModelInfoCapability.get(player).ifPresent(cap -> {
            cap.setDisabled(bool);
            PlayerModelSelectionStore.saveCurrentSelection(player, cap);
            PlayerDataSaveBridge.save(player);
            context.getSource().sendSuccess(() -> Component.translatable(str2, player.getScoreboardName()), operator);
        }));
        return Command.SINGLE_SUCCESS;
    }
}
