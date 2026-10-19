package com.micaftic.morpher.client.gui;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import java.awt.Font;
import java.util.function.Function;
/** Uploads lazily rasterized system glyphs through the native 1.21.1 font atlas. */
public final class SystemFontGlyphProvider implements GlyphProvider {
    private final SystemFontRasterizer raster;private final FontSet fallback;
    public SystemFontGlyphProvider(Font font,FontSet fallback){this.raster=new SystemFontRasterizer(font);this.fallback=fallback;}
    public IntSet getSupportedGlyphs(){return IntSets.fromTo(32,127);}
    public GlyphInfo getGlyph(int codepoint){
        if(!raster.supports(codepoint))return fallback.getGlyphInfo(codepoint,false);
        var m=raster.metrics(codepoint);
        return new GlyphInfo(){
            public float getAdvance(){return m.advance();}
            public BakedGlyph bake(Function<SheetGlyphInfo,BakedGlyph> stitch){
                if(m.width()==0||m.height()==0)return fallback.getGlyph(32);
                return stitch.apply(new SheetGlyphInfo(){
                    public int getPixelWidth(){return m.width();}public int getPixelHeight(){return m.height();}
                    public float getOversample(){return SystemFontRasterizer.OVERSAMPLE;}
                    public float getBearingLeft(){return m.left()/getOversample();}public float getBearingTop(){return -m.top()/getOversample();}
                    public boolean isColored(){return true;}
                    public void upload(int x,int y){
                        var pixels=raster.rasterize(codepoint,m);
                        try(var image=new NativeImage(pixels.getWidth(),pixels.getHeight(),false)){
                            for(int yy=0;yy<pixels.getHeight();yy++)for(int xx=0;xx<pixels.getWidth();xx++)image.setPixelRGBA(xx,yy,pixels.getRGB(xx,yy));
                            image.upload(0,x,y,false);
                        }
                    }
                });
            }
        };
    }
}
