package com.micaftic.morpher.client.gui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

/** Two-pixel sampling, uploaded through Minecraft's own glyph atlas only when a glyph is baked. */
public final class SystemFontRasterizer {
    public static final float OVERSAMPLE=2;
    private static final FontRenderContext CONTEXT=new FontRenderContext(null,true,true);
    public record Metrics(float advance,int left,int top,int width,int height) {}
    private final Font font;
    public SystemFontRasterizer(Font font){this.font=font;}
    public boolean supports(int codepoint){return Character.isValidCodePoint(codepoint)&&font.canDisplay(codepoint);}
    private GlyphVector vector(int cp){return font.createGlyphVector(CONTEXT,new String(Character.toChars(cp)));}
    public Metrics metrics(int cp){
        var vector=vector(cp);var bounds=vector.getPixelBounds(CONTEXT,0,0);
        return new Metrics((float)vector.getGlyphPosition(vector.getNumGlyphs()).getX()/OVERSAMPLE,bounds.x,bounds.y,bounds.width,bounds.height);
    }
    public BufferedImage rasterize(int cp,Metrics m){
        var image=new BufferedImage(Math.max(1,m.width()),Math.max(1,m.height()),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();
        try {graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);graphics.setColor(Color.WHITE);graphics.drawGlyphVector(vector(cp),-m.left(),-m.top());}
        finally {graphics.dispose();}
        return image;
    }
}
