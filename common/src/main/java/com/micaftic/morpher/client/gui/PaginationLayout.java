package com.micaftic.morpher.client.gui;

/** One measured footer row, with a separate status region and space around the page label. */
final class PaginationLayout {
    record Rect(int x,int y,int w,int h) { int right(){return x+w;} }
    record Row(Rect status,Rect previous,Rect label,Rect next) {}
    private PaginationLayout() {}
    static Row measure(int x,int y,int width,int labelTextWidth,int buttonSize){
        int size=Math.max(1,Math.min(buttonSize,width/4)),gap=Math.min(8,Math.max(0,width-2*size)/8);
        int labelW=Math.min(Math.max(40,labelTextWidth+16),Math.max(1,width-2*size-2*gap));
        int group=2*size+2*gap+labelW,px=x+width-group;
        return new Row(new Rect(x,y,Math.max(0,px-x-10),size),new Rect(px,y,size,size),new Rect(px+size+gap,y,labelW,size),new Rect(x+width-size,y,size,size));
    }
}
