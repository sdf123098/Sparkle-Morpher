package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.renderer.preview.PreviewRenderBridge;
import com.micaftic.morpher.client.renderer.preview.NativeCardPreviewCache;
import com.micaftic.morpher.client.renderer.preview.StaticPreviewCache;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains vanilla's texture lifecycle and only opts static model cards into its reuse hook. */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
    @Unique private final StaticPreviewCache spm$cache=new StaticPreviewCache();
    @Unique private Object spm$incomingKey;
    @Inject(method="prepare",at=@At("HEAD"),cancellable=true)
    private void spm$begin(PictureInPictureRenderState state,GuiRenderState gui,FeatureRenderDispatcher dispatcher,int scale,CallbackInfo ci){
        if (!NativeCardPreviewCache.isDedicatedRenderer(this) && state instanceof GuiEntityRenderState entity
                && NativeCardPreviewCache.prepare(entity,gui,dispatcher,scale)) {
            ci.cancel();
            return;
        }
        spm$incomingKey=state instanceof GuiEntityRenderState entity?PreviewRenderBridge.staticKey(entity.renderState()):null;
    }
    @Inject(method="textureIsReadyToBlit",at=@At("HEAD"),cancellable=true)
    private void spm$reuse(PictureInPictureRenderState state,CallbackInfoReturnable<Boolean> cir){
        if(spm$cache.canReuse(spm$incomingKey))cir.setReturnValue(true);
    }
    @Inject(method="prepare",at=@At("RETURN"))
    private void spm$finished(PictureInPictureRenderState state,GuiRenderState gui,FeatureRenderDispatcher dispatcher,int scale,CallbackInfo ci){
        spm$cache.rendered(spm$incomingKey);spm$incomingKey=null;
        if(state instanceof GuiEntityRenderState entity)PreviewRenderBridge.finishGuiState(entity.renderState());
    }
}
