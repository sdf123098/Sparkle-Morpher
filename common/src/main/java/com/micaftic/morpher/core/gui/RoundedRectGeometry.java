package com.micaftic.morpher.core.gui;

/** Emits the rounded edge rows and batches the straight interior into rectangles. */
public final class RoundedRectGeometry {
    @FunctionalInterface public interface Fill { void rect(int x,int y,int w,int h,int color); }
    private RoundedRectGeometry() {}
    private static int inset(int radius,int row){int edge=radius-row-1;return edge>0?(int)Math.ceil(radius-Math.sqrt(Math.max(0,radius*radius-edge*edge))):0;}
    public static void fill(Fill sink,int x,int y,int w,int h,int radius,int color){
        if(w<=0||h<=0)return;int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        if(h>2*r)sink.rect(x,y+r,w,h-2*r,color);
        for(int row=0;row<r;row++){int dx=inset(r,row);sink.rect(x+dx,y+row,w-2*dx,1,color);sink.rect(x+dx,y+h-1-row,w-2*dx,1,color);}
    }
    public static void outline(Fill sink,int x,int y,int w,int h,int radius,int color){
        if(w<=0||h<=0)return;int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        for(int row=0;row<r;row++){int dx=inset(r,row);if(row==0){sink.rect(x+dx,y,w-2*dx,1,color);if(h>1)sink.rect(x+dx,y+h-1,w-2*dx,1,color);}else{sink.rect(x+dx,y+row,1,1,color);sink.rect(x+w-dx-1,y+row,1,1,color);sink.rect(x+dx,y+h-1-row,1,1,color);sink.rect(x+w-dx-1,y+h-1-row,1,1,color);}}
        int start=r;if(r==0){sink.rect(x,y,w,1,color);if(h>1)sink.rect(x,y+h-1,w,1,color);start=1;}
        if(h>2*start){sink.rect(x,y+start,1,h-2*start,color);if(w>1)sink.rect(x+w-1,y+start,1,h-2*start,color);}
    }
}
