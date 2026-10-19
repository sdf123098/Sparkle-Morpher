package com.micaftic.morpher.client.gui;

import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.*;
import java.awt.Font;

public final class SystemFontGlyphProvider implements GlyphProvider {
    private final SystemFontRasterizer raster;
    private final GlyphSource fallback;
    public SystemFontGlyphProvider(Font font,GlyphSource fallback){raster=new SystemFontRasterizer(font);this.fallback=fallback;}
    // FontSet uses this set for obfuscated-text alternatives, not for normal glyph lookup.
    // Keeping it small avoids eagerly rasterizing tens of thousands of CJK glyphs on selection.
    @Override public IntSet getSupportedGlyphs(){return IntSets.fromTo(32,127);}
    @Override public UnbakedGlyph getGlyph(int codepoint){
        if(!raster.supports(codepoint)){
            var glyph=fallback.getGlyph(codepoint);
            return new UnbakedGlyph(){public GlyphInfo info(){return glyph.info();}public BakedGlyph bake(Stitcher s){return glyph;}};
        }
        var metrics=raster.metrics(codepoint);
        if(metrics.width()==0||metrics.height()==0)return new EmptyGlyph(metrics.advance());
        return new UnbakedGlyph(){
            public GlyphInfo info(){return GlyphInfo.simple(metrics.advance());}
            public BakedGlyph bake(Stitcher stitcher){return stitcher.stitch(info(),new GlyphBitmap(){
                public int getPixelWidth(){return metrics.width();}
                public int getPixelHeight(){return metrics.height();}
                public float getOversample(){return SystemFontRasterizer.OVERSAMPLE;}
                public float getBearingLeft(){return metrics.left()/getOversample();}
                public float getBearingTop(){return -metrics.top()/getOversample();}
                public boolean isColored(){return true;}
                public void upload(int x,int y,GpuTexture target){
                    var pixels=raster.rasterize(codepoint,metrics);
                    try(var image=new NativeImage(pixels.getWidth(),pixels.getHeight(),false)){
                        for(int yy=0;yy<pixels.getHeight();yy++)for(int xx=0;xx<pixels.getWidth();xx++)image.setPixel(xx,yy,pixels.getRGB(xx,yy));
                        RenderSystem.getDevice().createCommandEncoder().writeToTexture(target,image,0,0,x,y);
                    }
                }
            });}
        };
    }
}
