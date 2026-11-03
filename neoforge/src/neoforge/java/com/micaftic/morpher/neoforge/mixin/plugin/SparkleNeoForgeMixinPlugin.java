package com.micaftic.morpher.neoforge.mixin.plugin;

import com.micaftic.morpher.core.compat.punchy.PunchyBridgeVersions;
import com.micaftic.morpher.core.compat.punchy.PunchyModMatcher;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforgespi.language.IModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Punchy! arm bridge only when a Punchy! build with an inspected arm render path is
 * installed. Punchy! is optional, so loading the mixin unconditionally would crash startup on
 * every setup without it, and applying it to an uninspected build could fail injection at class
 * load. Either way the safest outcome is to leave Punchy!'s own arm untouched.
 */
public final class SparkleNeoForgeMixinPlugin implements IMixinConfigPlugin {

    private static final String PUNCHY_ARM_MIXIN =
            "com.micaftic.morpher.neoforge.mixin.compat.punchy.PunchyArmRendererMixin";

    private static Boolean punchyArmSupported;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!PUNCHY_ARM_MIXIN.equals(mixinClassName)) {
            return true;
        }
        boolean apply = FMLEnvironment.dist.isClient() && punchyArmSupported();
        System.out.println("[Sparkle Morpher] Punchy arm bridge mixin "
                + (apply ? "enabled" : "skipped (Punchy absent or unsupported version)"));
        return apply;
    }

    private static synchronized boolean punchyArmSupported() {
        if (punchyArmSupported != null) {
            return punchyArmSupported;
        }
        boolean supported = false;
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                for (IModInfo info : modList.getMods()) {
                    if (!PunchyModMatcher.matches(info.getModId(), info.getDisplayName())) {
                        continue;
                    }
                    supported = PunchyBridgeVersions.isSupported(info.getVersion().toString());
                    break;
                }
            }
        } catch (Throwable t) {
            System.out.println("[Sparkle Morpher] Punchy probe failed during mixin setup; arm bridge disabled: " + t);
        }
        punchyArmSupported = supported;
        return supported;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
