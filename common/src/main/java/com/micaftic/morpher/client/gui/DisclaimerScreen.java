package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Actual first-use agreement; final reference 55 keeps the native font and application rail. */
public class DisclaimerScreen extends Screen {
    private int scroll;
    public DisclaimerScreen(){super(Component.literal("使用说明"));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        RoulettePanelStyle.applicationRail(g,width,height,0);var l=ModelPanelLayout.create(width,height);int x=l.contentLeft,w=l.contentWidth;
        RoulettePanelStyle.iconButton(g,mx,my,x,l.top+6,RoulettePanelStyle.Glyph.UP,true);g.text(font,title,x+32,l.top+10,RoulettePanelStyle.TEXT,false);RoulettePanelStyle.iconButton(g,mx,my,x+w-22,l.top+6,RoulettePanelStyle.Glyph.CLOSE,true);
        RoulettePanelStyle.fill(g,x,l.top+49,w,1,0x18FFFFFF);int top=l.top+62,bottom=Math.min(height-l.top-80,top+340);RoulettePanelStyle.roundedSurface(g,x,top,w,bottom-top,4,0x0AFFFFFF,0x22FFFFFF);
        g.text(font,Component.literal("阅读使用说明"),x+16,top+17,RoulettePanelStyle.TEXT,false);
        var lines=font.split(Component.translatable("gui.sparkle_morpher.disclaimer.text"),w-40);int room=bottom-top-60,max=Math.max(0,lines.size()*18-room);scroll=Math.max(0,Math.min(scroll,max));g.enableScissor(x+16,top+52,x+w-16,bottom-8);
        for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),x+16,top+52+i*18-scroll,RoulettePanelStyle.MUTED,false);g.disableScissor();
        if(max>0){int h=Math.max(16,room*room/(lines.size()*18));RoulettePanelStyle.fill(g,x+w-5,top+52+(room-h)*scroll/max,2,h,0xFF9A9A9A);}
        g.text(font,Component.literal("请阅读完整说明后继续。"),x,height-l.top-16,RoulettePanelStyle.MUTED,false);int bx=x+w-150,by=height-l.top-34;RoulettePanelStyle.roundedFill(g,bx,by,150,22,3,RoulettePanelStyle.RED);g.centeredText(font,Component.literal("已阅读，进入模型"),bx+75,by+7,RoulettePanelStyle.TEXT);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean flag){var l=ModelPanelLayout.create(width,height);if(e.button()!=0)return super.mouseClicked(e,flag);if(RoulettePanelStyle.inside(e.x(),e.y(),l.contentLeft+l.contentWidth-150,height-l.top-34,150,22)){GeneralConfig.DISCLAIMER_SHOW.set(false);GeneralConfig.DISCLAIMER_SHOW.save();InputUtil.setScreen(new ModernPlayerModelScreen());return true;}if(e.y()<l.top+36){onClose();return true;}return true;}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll+=(sy<0?1:-1)*36;return true;}
    @Override public void onClose(){InputUtil.setScreen(null);}
}
