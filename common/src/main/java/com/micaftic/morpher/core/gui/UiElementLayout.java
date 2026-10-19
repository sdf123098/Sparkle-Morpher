package com.micaftic.morpher.core.gui;

/** Native logical-pixel measurements shared by buttons, page headers and cover images. */
public final class UiElementLayout {
    public static final int CARD_RADIUS=5,BUTTON_RADIUS=4;
    private UiElementLayout() {}
    public static int centeredY(int y,int height,int textHeight){return y+Math.max(0,(height-textHeight)/2);}
    public record Header(int titleY,int subtitleY){}
    public static Header header(int buttonY,int buttonHeight,int lineHeight,boolean subtitle){
        int titleHeight=Math.round(lineHeight*1.25f),gap=3;
        int total=titleHeight+(subtitle?gap+lineHeight:0);
        int top=buttonY+(buttonHeight-total)/2;
        return new Header(top,top+titleHeight+gap);
    }
    public record Uv(float u0,float v0,float u1,float v1){}
    /** Cover scaling: fill the destination and crop excess source pixels equally on each side. */
    public static Uv cover(int sourceW,int sourceH,int targetW,int targetH){
        if(sourceW<=0||sourceH<=0||targetW<=0||targetH<=0)throw new IllegalArgumentException("Image dimensions");
        double scale=Math.max((double)targetW/sourceW,(double)targetH/sourceH);
        float u=(float)(targetW/(sourceW*scale)),v=(float)(targetH/(sourceH*scale));
        return new Uv((1-u)/2,(1-v)/2,(1+u)/2,(1+v)/2);
    }
    public static int mix(int a,int b,float weight){
        float t=Math.max(0,Math.min(1,weight));int c=0;
        for(int shift=0;shift<=24;shift+=8)c|=Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;
        return c;
    }
}
