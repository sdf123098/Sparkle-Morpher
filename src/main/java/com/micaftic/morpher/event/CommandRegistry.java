package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.command.*;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.model.ServerModelManager;
import com.google.common.collect.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.*;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.commands.*;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.micaftic.morpher.core.api.PlatformAPI;
import java.util.*;
import java.util.stream.Collectors;

public final class CommandRegistry {
    private CommandRegistry() {}
    public static final SuggestionProvider<CommandSourceStack> MODEL_IDS = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "models"), (ctx, b) -> {
        if (ctx.getSource() instanceof SharedSuggestionProvider) {
            if (PlatformAPI.isServer()) return SharedSuggestionProvider.suggest(ServerModelManager.getServerModelInfo().keySet().stream().map(CommandRegistry::esc).toList(), b);
            return SharedSuggestionProvider.suggest(ClientModelManager.getModelAssemblyMap().keySet().stream().map(CommandRegistry::esc).toList(), b);
        }
        return Suggestions.empty();
    });
    public static final SuggestionProvider<CommandSourceStack> ANIMATION_NAMES = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "animations"), (ctx, b) -> {
        if (ctx.getSource() instanceof SharedSuggestionProvider) {
            if (PlatformAPI.isServer()) return Suggestions.empty();
            Object2ReferenceMap<String, Animation> m = ClientModelManager.getLocalModelContext().getAnimationBundle().getMainAnimations();
            HashSet<String> s = Sets.newHashSet(m.keySet().stream().map(CommandRegistry::esc).toList()); s.add("stop");
            return SharedSuggestionProvider.suggest(s, b);
        }
        return Suggestions.empty();
    });
    public static final SuggestionProvider<CommandSourceStack> TEXTURE_IDS = SuggestionProviders.register(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "textures"), (ctx, b) -> {
        if (ctx.getSource() instanceof SharedSuggestionProvider) {
            String str = ctx.getArgument("model_id", String.class);
            if (PlatformAPI.isServer()) {
                if (ServerModelManager.getServerModelInfo().containsKey(str)) { List<String> l = ServerModelManager.getServerModelInfo().get(str).getModelInfo().getTextures().stream().map(CommandRegistry::esc).collect(Collectors.toList()); l.add(0, "-"); return SharedSuggestionProvider.suggest(l, b); }
            } else if (ClientModelManager.getModelAssemblyMap().containsKey(str)) {
                List<String> l2 = ClientModelManager.getModelContext(str).map(c -> c.getAnimationBundle().getTextures().getKeys().stream().map(CommandRegistry::esc).collect(Collectors.toList())).orElseGet(Lists::newArrayList); l2.add(0, "-"); return SharedSuggestionProvider.suggest(l2, b);
            }
        }
        return Suggestions.empty();
    });
    public static void register() { NeoForge.EVENT_BUS.addListener(CommandRegistry::onCmd); }
    private static void onCmd(RegisterCommandsEvent event) {
        if (!YesSteveModel.isAvailable()) { RootCommand.registerFallbackCommands(event.getDispatcher()); return; }
        RootCommand.registerCommands(event.getDispatcher());
        if (!PlatformAPI.isServer()) RootClientCommand.registerClientCommands(event.getDispatcher());
    }
    private static String esc(String s) { return s.chars().allMatch(i -> StringReader.isAllowedInUnquotedString((char) i)) ? s : String.format("\"%s\"", s.replace("\"", "\\\"").replace("'", "\\'")); }
}