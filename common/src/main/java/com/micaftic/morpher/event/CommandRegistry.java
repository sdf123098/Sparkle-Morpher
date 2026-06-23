package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.command.OpenYSMClientCommand;
import com.micaftic.morpher.command.RootClientCommand;
import com.micaftic.morpher.command.RootCommand;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.model.ServerModelManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.ResourceLocation;
import com.micaftic.morpher.core.api.PlatformAPI;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandRegistry {

    private CommandRegistry() {
    }

    public static final SuggestionProvider<CommandSourceStack> MODEL_IDS = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "models"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            if (PlatformAPI.isServer()) {
                return SharedSuggestionProvider.suggest(ServerModelManager.getServerModelInfo().keySet().stream().map(CommandRegistry::escapeIfRequired).toList(), suggestionsBuilder);
            }
            return SharedSuggestionProvider.suggest(ClientModelManager.getModelAssemblyMap().keySet().stream().map(CommandRegistry::escapeIfRequired).toList(), suggestionsBuilder);
        }
        return Suggestions.empty();
    });

    public static final SuggestionProvider<CommandSourceStack> ANIMATION_NAMES = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "animations"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            if (PlatformAPI.isServer()) {
                return Suggestions.empty();
            }
            Object2ReferenceMap<String, Animation> map = ClientModelManager.getLocalModelContext().getAnimationBundle().getMainAnimations();
            HashSet<String> set = Sets.newHashSet();
            set.addAll(map.keySet().stream().map(CommandRegistry::escapeIfRequired).toList());
            set.add("stop");
            return SharedSuggestionProvider.suggest(set, suggestionsBuilder);
        }
        return Suggestions.empty();
    });

    public static final SuggestionProvider<CommandSourceStack> TEXTURE_IDS = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "textures"), (commandContext, suggestionsBuilder) -> {
        if (commandContext.getSource() instanceof SharedSuggestionProvider) {
            String str = commandContext.getArgument("model_id", String.class);
            if (PlatformAPI.isServer()) {
                if (ServerModelManager.getServerModelInfo().containsKey(str)) {
                    List<String> list = ServerModelManager.getServerModelInfo().get(str).getModelInfo().getTextures().stream().map(CommandRegistry::escapeIfRequired).collect(Collectors.toList());
                    list.add(0, "-");
                    return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
                }
            } else if (ClientModelManager.getModelAssemblyMap().containsKey(str)) {
                List<String> list2 = ClientModelManager.getModelContext(str).map(context -> context.getAnimationBundle().getTextures().getKeys().stream().map(CommandRegistry::escapeIfRequired).collect(Collectors.toList())).orElseGet(Lists::newArrayList);
                list2.add(0, "-");
                return SharedSuggestionProvider.suggest(list2, suggestionsBuilder);
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
            if (!PlatformAPI.isServer()) {
                RootClientCommand.registerClientCommands(dispatcher);
            }
        });
    }

    private static String escapeIfRequired(String str) {
        if (str.chars().allMatch(i -> StringReader.isAllowedInUnquotedString((char) i))) {
            return str;
        }
        return String.format("\"%s\"", str.replace("\"", "\\\"").replace("'", "\\'"));
    }
}