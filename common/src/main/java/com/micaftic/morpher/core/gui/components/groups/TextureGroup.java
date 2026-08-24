package com.micaftic.morpher.core.gui.components.groups;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class TextureGroup extends CategoryGroup {
    public TextureGroup() {
        super("_textures");
    }

    @Override
    public Component getTitle() {
        String key = "gui.sparkle_morpher.animation.category._textures";
        if (I18n.exists(key)) return Component.translatable(key);
        return Component.literal("Textures");
    }
}
