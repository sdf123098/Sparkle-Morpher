package com.micaftic.morpher.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
/** Maps normalized, end-coordinate GUI quads onto the 1.21.1 pixel-UV API. */
public final class LegacyGuiBlit {
    public static void blit(GuiGraphics g,ResourceLocation id,int x0,int y0,int x1,int y1,float u0,float u1,float v0,float v1){
        int size=65536;
        g.blit(id,x0,y0,x1-x0,y1-y0,u0*size,v0*size,Math.round((u1-u0)*size),Math.round((v1-v0)*size),size,size);
    }
}
