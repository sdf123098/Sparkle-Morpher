package com.micaftic.morpher.client.gui;
import java.awt.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SystemFontCatalogTest {
    @Test void installedFamiliesHavePersistentResourceSafeIds(){
        assertFalse(SystemFontCatalog.entries().isEmpty());
        for(var font:SystemFontCatalog.entries()){
            assertTrue(font.id().matches("[a-z0-9_]+:[a-z0-9_]+"));
            assertEquals(font.family(),SystemFontCatalog.family(font.id()).orElseThrow());
        }
        assertNotEquals(SystemFontCatalog.id("Microsoft YaHei"),SystemFontCatalog.id("微软雅黑"));
        assertTrue(SystemFontCatalog.family("spm_system_font:00").isEmpty());
    }
    @Test void rasterizedGlyphHasCorrectBoundsAndVisiblePixels(){
        var raster=new SystemFontRasterizer(new Font("Dialog",Font.PLAIN,18));
        var metrics=raster.metrics('A');var image=raster.rasterize('A',metrics);
        assertTrue(metrics.advance()>0);assertEquals(metrics.width(),image.getWidth());assertEquals(metrics.height(),image.getHeight());
        boolean visible=false;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)visible|=(image.getRGB(x,y)>>>24)>0;
        assertTrue(visible);assertEquals(0,raster.metrics(' ').width());
    }
}
