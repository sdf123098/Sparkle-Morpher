package com.micaftic.morpher.mixin.client;
import com.micaftic.morpher.client.gui.SystemFontCatalog;
import com.micaftic.morpher.client.gui.SystemFontGlyphProvider;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
@Mixin(FontManager.class)
public abstract class SystemFontManagerMixin {
    @Shadow @Final private Map<ResourceLocation,FontSet> fontSets;
    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private FontSet missingFontSet;
    @Inject(method="getFontSetRaw",at=@At("HEAD"),cancellable=true)
    private void spm$systemFont(ResourceLocation id,CallbackInfoReturnable<FontSet> cir){
        if(!id.getNamespace().equals(SystemFontCatalog.NAMESPACE))return;
        FontSet result=fontSets.get(id);
        if(result==null){
            var family=SystemFontCatalog.font(id.toString());
            var fallback=fontSets.getOrDefault(ResourceLocation.fromNamespaceAndPath("minecraft","default"),missingFontSet);
            if(family.isEmpty()){cir.setReturnValue(fallback);return;}
            result=new FontSet(textureManager,id);
            result.reload(List.of(new GlyphProvider.Conditional(new SystemFontGlyphProvider(family.get(),fallback),FontOption.Filter.ALWAYS_PASS)),Set.of());
            fontSets.put(id,result);
        }
        cir.setReturnValue(result);
    }
}
