package com.micaftic.morpher.core.gui;

/** Final 51 uses eight straight-edged sectors, shared by rasterization and hit testing. */
public final class RouletteGeometry {
    public static final double INNER=56, OUTER=151;
    private RouletteGeometry() {}
    public static double[][] sector(int index,double inner,double outer) {
        double a=-Math.PI/2+index*Math.PI/4,b=a+Math.PI/4;
        return new double[][]{{inner*Math.cos(a),inner*Math.sin(a)},{outer*Math.cos(a),outer*Math.sin(a)},
                {outer*Math.cos(b),outer*Math.sin(b)},{inner*Math.cos(b),inner*Math.sin(b)}};
    }
    public static boolean contains(int index,double x,double y) {
        var p=sector(index,INNER,OUTER);double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(int i=0;i<4;i++){var a=p[i];var b=p[(i+1)%4];if((a[1]<=y&&b[1]>y)||(b[1]<=y&&a[1]>y)){double edge=a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]);lo=Math.min(lo,edge);hi=Math.max(hi,edge);}}
        return Double.isFinite(lo)&&x>=lo&&x<hi;
    }
    public static int[] span(double[][] p,int y) {
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY,scan=y+.5;
        for(int i=0;i<4;i++){var a=p[i];var b=p[(i+1)%4];if((a[1]<=scan&&b[1]>scan)||(b[1]<=scan&&a[1]>scan)){double x=a[0]+(scan-a[1])*(b[0]-a[0])/(b[1]-a[1]);lo=Math.min(lo,x);hi=Math.max(hi,x);}}
        return Double.isFinite(lo)?new int[]{(int)Math.ceil(lo-.5),(int)Math.ceil(hi-.5)}:null;
    }
}
