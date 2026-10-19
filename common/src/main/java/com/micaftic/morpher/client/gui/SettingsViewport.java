package com.micaftic.morpher.client.gui;

/** Pixel scroll shared by settings contents, input bounds and text fields. */
final class SettingsViewport {
    private double offset;
    private int top,bottom,contentBottom;
    void measure(int top,int bottom,int contentBottom){
        this.top=top;this.bottom=Math.max(top,bottom);this.contentBottom=Math.max(bottom,contentBottom);
        offset=Math.max(0,Math.min(offset,maxOffset()));
    }
    boolean scroll(double wheel){
        if(wheel==0||maxOffset()==0)return false;
        offset=Math.max(0,Math.min(maxOffset(),offset-wheel*24));return true;
    }
    int offset(){return (int)Math.round(offset);}
    int maxOffset(){return Math.max(0,contentBottom-bottom);}
    int screenY(int contentY){return contentY-offset();}
    boolean fullyVisible(int contentY,int height){int y=screenY(contentY);return y>=top&&y+height<=bottom;}
    static int rowHeight(int width){return width<560?26:width<800?28:30;}
}
