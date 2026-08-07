package com.micaftic.morpher.mixin.plugin;

import com.micaftic.morpher.util.obfuscate.Keep;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

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
        if (isTouhouLittleMaidCompatMixin(simpleName) && !isTouhouLittleMaidPresent()) {
            System.out.println("[Sparkle Morpher] TouhouLittleMaid not installed, skipping compat mixin: " + str2);
            return false;
        }
        return true;
    }

    private static boolean isTouhouLittleMaidCompatMixin(String simpleName) {
        return "TouhouMaidEntityMixin".equals(simpleName) || "TouhouLittleMaidYsmCompatMixin".equals(simpleName);
    }

    private static boolean isTouhouLittleMaidPresent() {
        try {
            Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", false,
                    MixinTweaker.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
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
}
