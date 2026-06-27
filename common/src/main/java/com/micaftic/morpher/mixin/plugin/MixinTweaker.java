package com.micaftic.morpher.mixin.plugin;

import com.micaftic.morpher.util.obfuscate.Keep;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MixinTweaker implements IMixinConfigPlugin {

    @Keep
    public void onLoad(String str) {
    }

    @Keep
    public String getRefMapperConfig() {
        return null;
    }

    @Keep
    public boolean shouldApplyMixin(String str, String str2) {
        String simpleName = str2 == null ? "" : str2.substring(str2.lastIndexOf('.') + 1);
        String property = System.getProperty("sparkle_morpher.mixin." + simpleName);
        if (property == null) {
            property = System.getProperty("ysm.mixin." + simpleName);
        }
        if (property != null && property.equalsIgnoreCase("false")) {
            System.out.println("[Sparkle Morpher] Disabled mixin by property: " + str2);
            return false;
        }

        Set<String> disabled = disabledMixins();
        if (disabled.contains(simpleName.toLowerCase(Locale.ROOT)) || disabled.contains(str2.toLowerCase(Locale.ROOT))) {
            System.out.println("[Sparkle Morpher] Disabled mixin by disable list: " + str2);
            return false;
        }
        if (isHighRiskMixin(simpleName)) {
            System.out.println("[Sparkle Morpher] Applying high-risk migration mixin: " + str2);
        }
        return true;
    }

    @Keep
    public void acceptTargets(Set<String> set, Set<String> set2) {
    }

    @Keep
    public List<String> getMixins() {
        return null;
    }

    @Keep
    public void preApply(String str, ClassNode classNode, String str2, IMixinInfo iMixinInfo) {
    }

    @Keep
    public void postApply(String str, ClassNode classNode, String str2, IMixinInfo iMixinInfo) {
    }

    private static Set<String> disabledMixins() {
        String value = firstNonBlank(
                System.getProperty("sparkle_morpher.disableMixins"),
                System.getProperty("ysm.disableMixins"),
                System.getenv("SPARKLE_MORPHER_DISABLE_MIXINS"),
                System.getenv("YSM_DISABLE_MIXINS")
        );
        if (value == null) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isHighRiskMixin(String simpleName) {
        return switch (simpleName) {
            case "PlayerRendererMixin",
                    "GuiEntityRendererMixin",
                    "InventoryScreenMixin",
                    "EntityRenderDispatcherMixin",
                    "BufferSourceMixin",
                    "WorldRendererMixin",
                    "MinecraftAccessor" -> true;
            default -> false;
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
