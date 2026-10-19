package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.gui.SystemFontCatalog;
import com.micaftic.morpher.client.gui.SystemFontGlyphProvider;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Register selected system fonts in the native font lifecycle, including resource-reload cleanup. */
@Mixin(FontManager.class)
public abstract class SystemFontManagerMixin {
    @Shadow @Final private Map<Identifier,FontSet> fontSets;
    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private FontSet missingFontSet;
    @Inject(method="getFontSetRaw",at=@At("HEAD"),cancellable=true)
    private void spm$systemFont(Identifier id,CallbackInfoReturnable<FontSet> cir){
        if(!id.getNamespace().equals(SystemFontCatalog.NAMESPACE))return;
        FontSet result=fontSets.get(id);
        if(result==null){
            var family=SystemFontCatalog.font(id.toString());
            if(family.isEmpty()){cir.setReturnValue(fontSets.getOrDefault(Identifier.fromNamespaceAndPath("minecraft","default"),missingFontSet));return;}
            var fallback=fontSets.getOrDefault(Identifier.fromNamespaceAndPath("minecraft","default"),missingFontSet).source(false);
            result=new FontSet(new GlyphStitcher(textureManager,id));
            result.reload(List.of(new GlyphProvider.Conditional(new SystemFontGlyphProvider(family.get(),fallback),FontOption.Filter.ALWAYS_PASS)),Set.of());
            fontSets.put(id,result);
        }
        cir.setReturnValue(result);
    }
}
