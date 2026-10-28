package com.micaftic.morpher.client.renderer.preview;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import net.minecraft.client.gui.GuiGraphics;
/** 1.21.1 uses immediate selected-model previews and bounded GPU snapshots for grid cards. */
public final class GuiModelRenderer {
    private static final LegacyCardPreviewCache CARD_PREVIEWS = new LegacyCardPreviewCache();
    /** Legacy snapshots are submitted immediately; there is no deferred frame queue to drain. */
    public static void beginCardPreviewFrame() {}
    public static void clearCardPreviews() { CARD_PREVIEWS.clear(); }
    public record DollOptions(float headYawOffset,float headYawOffsetO,boolean alignWithVehicle){
        public static final DollOptions GUI_PREVIEW=new DollOptions(0,0,false);
    }
    @SuppressWarnings("unchecked") // Renderer and entity originate from the same preview request.
    public static void enqueueLivingPreview(GuiGraphics g,int left,int top,int right,int bottom,float ox,float oy,float scale,float tick,LivingAnimatable<?> entity,GeoReplacedEntityRenderer renderer,boolean fixed,boolean hide,float yaw,float pitch,boolean extra,DollOptions options){
        g.enableScissor(left,top,right,bottom);
        try{ModelPreviewRenderer.renderLivingEntityPreview(ox,oy,scale,tick,entity,renderer,fixed,hide,yaw);}
        finally{g.disableScissor();}
    }
    public static void enqueueStaticLivingPreview(GuiGraphics g,int left,int top,int right,int bottom,float ox,float oy,float scale,float tick,LivingAnimatable<?> entity,GeoReplacedEntityRenderer renderer,boolean fixed,boolean hide,float yaw,float pitch,boolean extra,DollOptions options,Object key){
        if (key == null) enqueueLivingPreview(g,left,top,right,bottom,ox,oy,scale,tick,entity,renderer,fixed,hide,yaw,pitch,extra,options);
        else enqueueCachedLivingPreview(g,left,top,right,bottom,ox,oy,scale,tick,entity,renderer,fixed,hide,yaw,pitch,extra,options,key,0,true);
    }
    public static boolean enqueueCachedLivingPreview(GuiGraphics g,int left,int top,int right,int bottom,float ox,float oy,float scale,float tick,LivingAnimatable<?> entity,GeoReplacedEntityRenderer renderer,boolean fixed,boolean hide,float yaw,float pitch,boolean extra,DollOptions options,Object key,long revision,boolean refreshAllowed){
        return CARD_PREVIEWS.draw(g,left,top,right,bottom,ox,oy,scale,tick,entity,renderer,fixed,hide,yaw,key,revision,refreshAllowed);
    }
}
