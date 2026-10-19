package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.client.gui.UiAppearancePreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class RoulettePanelStyle {
    public static final Identifier MODEL_PANEL_ICONS = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "texture/ui/lucide.png");

    public static final int BG = 0xC0202020;
    public static final int PANEL = 0x66303030;
    public static final int PANEL_HOVER = 0x88505050;
    public static final int PANEL_ACTIVE = 0x77404040;
    public static final int GLASS = 0x60303030;
    public static final int BORDER = 0x555F5F5F;
    public static final int RED = 0xFFE05252;
    public static final int RED_SOFT = 0x77E05252;
    public static final int TEXT = 0xFFF0F0F0;
    public static final int MUTED = 0xFF9A9A9A;
    public static final int ICON = 18;

    private RoulettePanelStyle() {
    }

    public enum Glyph {
        MODEL(0, 0),
        SETTINGS(32, 0),
        ROULETTE(80, 0),
        APPLY(0, 16),
        RELOAD(48, 16),
        UP(64, 16),
        ROOT(80, 16),
        CLEAR(48, 32),
        CANCEL(64, 32),
        CLOSE(80, 32),
        SAVE(96, 32),
        DELETE(112, 32),
        CREATE(0, 48),
        MOVE(16, 48),
        PLUS(32, 48),
        MINUS(48, 48),
        INFO(96, 48),
        CHECK(112, 48), RESOURCE(16,0), USERS(0,64), LOGOUT(80,64),
        GLOBE(96,64), MORE(16,80), EYE(64,80), FILTER(80,80), UNPLUG(96,80), EDIT(0,96);

        public final int u;
        public final int v;

        Glyph(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fillGradient(x, y, x + w, y + h, color, color);
    }

    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x, y, w, 1, color);
        fill(g, x, y + h - 1, w, 1, color);
        fill(g, x, y, 1, h, color);
        fill(g, x + w - 1, y, 1, h, color);
    }

    public static void glassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var appearance=UiAppearancePreferences.current();
        blurGlass(g,x,y,w,h,appearance.blurTint(),appearance.blurRadius());
        roundedSurface(g,x,y,w,h,4,appearance.backgroundColor(),0x2EFFFFFF);
    }

    public static void secondaryGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var appearance=UiAppearancePreferences.current();
        blurGlass(g,x,y,w,h,appearance.blurTint(),appearance.blurRadius());
        roundedSurface(g,x,y,w,h,5,appearance.backgroundColor(),0x33FFFFFF);
    }

    public static void blurGlass(GuiGraphicsExtractor g, int x, int y, int w, int h, int tint, float radius) {
        if (w <= 0 || h <= 0 || radius <= 0) {
            return;
        }
        BlurStack.pushBlur(x, y, w, h, 0.0f, radius, tint);
        BlurStack.flush(g);
    }

    public static void iconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, Glyph glyph, boolean active) {
        boolean hover = active && inside(mouseX, mouseY, x, y, ICON, ICON);
        roundedSurface(g,x,y,ICON,ICON,3,!active?0x08282828:hover?0x18FFFFFF:0x0CFFFFFF,0x22FFFFFF);
        drawIcon(g, glyph, x + 1, y + 1);
    }

    public static void drawIcon(GuiGraphicsExtractor g, Glyph glyph, int x, int y) {
        drawIconScaled(g,glyph,x,y,16);
    }

    public static void drawIconScaled(GuiGraphicsExtractor g, Glyph glyph, int x, int y, int size) {
        g.blit(MODEL_PANEL_ICONS, x, y, x + size, y + size,
                glyph.u / 128.0f, (glyph.u + 16) / 128.0f,
                glyph.v / 128.0f, (glyph.v + 16) / 128.0f);
    }

    public static void drawCentered(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static String trim(Font font, String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int keep = text.length();
        while (keep > 0 && font.width(text.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return text.substring(0, Math.max(0, keep)) + ellipsis;
    }

    public static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
    public static void roundedFill(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){
        RoundedRectGeometry.fill((rx,ry,rw,rh,c)->fill(g,rx,ry,rw,rh,c),x,y,w,h,radius,color);
    }
    public static void roundedSurface(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color,int outline){
        roundedFill(g,x,y,w,h,radius,color);
        RoundedRectGeometry.outline((rx,ry,rw,rh,c)->fill(g,rx,ry,rw,rh,c),x,y,w,h,radius,outline);
    }
    public static void roundedBorder(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){
        RoundedRectGeometry.outline((rx,ry,rw,rh,c)->fill(g,rx,ry,rw,rh,c),x,y,w,h,radius,color);
    }
    /** Clip the logo's textured quad to the same rounded shape on every application page. */
    public static void applicationLogo(GuiGraphicsExtractor g,int x,int y,int size){
        var logo=com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID,"texture/ui/spm.png");
        RoundedRectGeometry.fill((rx,ry,rw,rh,c)->g.blit(logo,rx,ry,rx+rw,ry+rh,
                (rx-x)/(float)size,(rx+rw-x)/(float)size,(ry-y)/(float)size,(ry+rh-y)/(float)size),
                x,y,size,size,Math.max(3,size/6),0);
    }
    public static int applicationMargin(int width){return width<560?6:width<800?7:8;}
    public static int applicationContentLeft(int width){return applicationMargin(width)+(width<560?28:width<800?30:36)+8;}
    public static void applicationRail(GuiGraphicsExtractor g,int width,int height,int selected){
        int p=applicationMargin(width),side=width<560?28:width<800?30:36,logoSize=width<560?20:width<800?22:26;
        glassPanel(g,p,p,width-2*p,height-2*p);
        roundedFill(g,p,p,side,height-2*p,5,0x59080808);
        fill(g,p+side,p,1,height-2*p,0x1AFFFFFF);
        applicationLogo(g,p+(side-logoSize)/2,p+6,logoSize);
        Glyph[] icons={Glyph.MODEL,Glyph.RESOURCE,Glyph.USERS,Glyph.SETTINGS};
        int top=p+logoSize+17;
        for(int i=0;i<4;i++){
            int y=top+i*30;
            if(i==selected){roundedFill(g,p+3,y,side-6,24,3,0x18FFFFFF);fill(g,p,y+4,2,16,RED);}
            drawIconScaled(g,icons[i],p+(side-12)/2,y+6,12);
        }
        drawIconScaled(g,Glyph.LOGOUT,p+(side-12)/2,height-p-24,12);
    }
    public static boolean applicationRailClick(double x,double y,int width,int height,java.util.function.IntConsumer navigate){
        int p=applicationMargin(width),side=width<560?28:width<800?30:36,top=p+(width<560?20:width<800?22:26)+17;
        if(x<p||x>=p+side)return false;
        for(int i=0;i<4;i++)if(y>=top+i*30&&y<top+i*30+24){navigate.accept(i);return true;}
        if(y>=height-p-28&&y<height-p-5){navigate.accept(-1);return true;}
        return false;
    }
}
