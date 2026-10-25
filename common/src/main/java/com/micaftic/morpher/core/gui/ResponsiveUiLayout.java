package com.micaftic.morpher.core.gui;

/** Shared measurements in Minecraft GUI coordinates, independent of display resolution and GUI scale. */
public final class ResponsiveUiLayout {
    private ResponsiveUiLayout() {}
    public record Rect(int x,int y,int width,int height) {
        public int right(){return x+width;}
        public int bottom(){return y+height;}
        public boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
    }
    public static int controlHeight(int width,int height){return width<560||height<300?20:22;}
    public static boolean inlinePreview(int width,int height){return width>=560&&height>=190;}
    public static int railStride(int height,int top,int bottomReserve){return Math.max(18,Math.min(30,(height-bottomReserve-top)/4));}
    public static int searchWidth(int available){return Math.max(40,Math.min(260,available/3));}
    public static Rect fit(int x,int y,int width,int height,int viewportWidth,int viewportHeight,int margin){
        int vw=Math.max(1,viewportWidth),vh=Math.max(1,viewportHeight);
        int mx=Math.min(Math.max(0,margin),Math.max(0,(vw-1)/2));
        int my=Math.min(Math.max(0,margin),Math.max(0,(vh-1)/2));
        int w=Math.max(1,Math.min(width,vw-2*mx)),h=Math.max(1,Math.min(height,vh-2*my));
        return new Rect(Math.max(mx,Math.min(x,vw-mx-w)),Math.max(my,Math.min(y,vh-my-h)),w,h);
    }
    public static Rect modal(int width,int height,int desiredWidth,int desiredHeight){
        return fit((width-desiredWidth)/2,(height-desiredHeight)/2,desiredWidth,desiredHeight,width,height,6);
    }
    public record Toolbar(int x,int y,int width,int searchWidth,int size,int stride,int visible,int count,boolean overflow) {
        public Rect button(int index){return new Rect(x+searchWidth+6+index*stride,y,size,size);}
        public int height(){return size;}
    }
    /** Keep one reachable row; excess actions move into a scrollable overflow menu. */
    public static Toolbar toolbar(int x,int y,int width,int viewportHeight,int count){
        int safeWidth=Math.max(1,width),size=controlHeight(safeWidth,viewportHeight),stride=size+4;
        int search=Math.min(searchWidth(safeWidth),Math.max(1,safeWidth-size-6));
        int slots=Math.max(1,(safeWidth-search-6+4)/stride);
        boolean overflow=count>slots;
        return new Toolbar(x,y,safeWidth,search,size,stride,Math.min(count,overflow?Math.max(0,slots-1):slots),count,overflow);
    }
    public record Menu(Rect bounds,int first,int visible,int maxScroll,int rowHeight){
        public Rect row(int index){return new Rect(bounds.x()+4,bounds.y()+4+index*rowHeight,bounds.width()-8,rowHeight-2);}
    }
    public static Menu menu(int x,int y,int width,int viewportWidth,int viewportHeight,int count,int requestedScroll){
        int row=22,capacity=Math.max(1,(viewportHeight-20)/row);
        boolean overflow=count>capacity;
        int visible=Math.max(1,Math.min(count,capacity-(overflow?1:0)));
        Rect box=fit(x,y,width,visible*row+8+(overflow?row:0),viewportWidth,viewportHeight,6);
        visible=Math.max(1,Math.min(count,(box.height()-8-(overflow?row:0))/row));
        int max=Math.max(0,count-visible),first=Math.max(0,Math.min(max,requestedScroll));
        return new Menu(box,first,visible,max,row);
    }
    /** Authored scenes shrink their coordinate system only when the live viewport is smaller. */
    public static int authoredPosition(double position,int reference,int available,double anchor){
        if(available<reference)return (int)Math.round(position*Math.max(1,available)/Math.max(1,reference));
        return (int)Math.round(position+(position>reference*anchor?available-reference:0));
    }
    public static int authoredSize(double size,int reference,int available){
        return Math.max(1,(int)Math.round(size*Math.min(1,Math.max(1,available)/(double)Math.max(1,reference))));
    }
    public record Wheel(int centerX,int centerY,float scale) {}
    /** Includes the page buttons at +/-160 with radius 18; the wheel radius is 151. */
    public static Wheel wheel(int left,int top,int right,int bottom){
        int w=Math.max(1,right-left),h=Math.max(1,bottom-top);
        float scale=Math.min(1f,Math.min(w/360f,h/304f));
        return new Wheel(left+w/2,top+h/2,scale);
    }
}
