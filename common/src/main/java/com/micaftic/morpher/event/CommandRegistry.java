package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.command.OpenYSMClientCommand;
import com.micaftic.morpher.command.RootCommand;
import com.micaftic.morpher.core.architectury.event.events.client.ClientCommandRegistrationEvent;
import com.micaftic.morpher.model.ServerModelManager;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.micaftic.morpher.core.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.Identifier;
import com.micaftic.morpher.core.api.PlatformAPI;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandRegistry {

    private CommandRegistry() {
    }

    public static final SuggestionProvider<CommandSourceStack> MODEL_IDS = SuggestionProviders.register(com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "models"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            return SharedSuggestionProvider.suggest(ServerModelManager.getServerModelInfo().keySet().stream().map(CommandRegistry::escapeIfRequired).toList(), suggestionsBuilder);
        }
        return Suggestions.empty();
    });

    public static final SuggestionProvider<CommandSourceStack> ANIMATION_NAMES = SuggestionProviders.register(com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "animations"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            HashSet<String> set = new HashSet<>();
            set.add("stop");
            return SharedSuggestionProvider.suggest(set, suggestionsBuilder);
        }
        return Suggestions.empty();
    });

    public static final SuggestionProvider<CommandSourceStack> TEXTURE_IDS = SuggestionProviders.register(com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "textures"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            String str = commandContext.getArgument("model_id", String.class);
            if (ServerModelManager.getServerModelInfo().containsKey(str)) {
                List<String> list = ServerModelManager.getServerModelInfo().get(str).getModelInfo().getTextures().stream().map(CommandRegistry::escapeIfRequired).collect(Collectors.toList());
                list.add(0, "-");
                return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
            }
        }
        return Suggestions.empty();
    });

    public static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            OpenYSMClientCommand.registerClientCommands(dispatcher);
        });
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            if (!YesSteveModel.isAvailable()) {
                RootCommand.registerFallbackCommands(dispatcher);
                return;
            }
            RootCommand.registerCommands(dispatcher);
        });
    }

    private static String escapeIfRequired(String str) {
        if (str.chars().allMatch(i -> StringReader.isAllowedInUnquotedString((char) i))) {
            return str;
        }
        return String.format("\"%s\"", str.replace("\"", "\\\"").replace("'", "\\'"));
    }
}
