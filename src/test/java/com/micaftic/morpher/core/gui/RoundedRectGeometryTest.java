package com.micaftic.morpher.core.gui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RoundedRectGeometryTest {
    @Test void cardCoverAndNameBandCannotPaintOutsideRoundedCardCorners(){
        int w=81,h=140;boolean[] card=new boolean[w*h];
        RoundedRectGeometry.fill((x,y,rw,rh,c)->{for(int yy=y;yy<y+rh;yy++)for(int xx=x;xx<x+rw;xx++)card[yy*w+xx]=true;},0,0,w,h,UiElementLayout.CARD_RADIUS,0);
        assertFalse(card[0]);assertFalse(card[w-1]);assertFalse(card[(h-1)*w]);assertFalse(card[w*h-1]);
        RoundedRectGeometry.fill((x,y,rw,rh,c)->{for(int yy=y;yy<y+rh;yy++)for(int xx=x;xx<x+rw;xx++)assertTrue(card[yy*w+xx],"cover escaped rounded card");},1,1,w-2,106,UiElementLayout.CARD_RADIUS-1,0);
        assertTrue(card[70*w+40]);
    }
    @Test void batchedRectanglesMatchLegacyPixelsWithoutOverdrawingAlpha(){
        for(int w:new int[]{1,2,7,22,94})for(int h:new int[]{1,2,9,22,162,524})for(int radius:new int[]{0,2,5}){
            int[] actual=new int[w*h],expected=new int[w*h];
            RoundedRectGeometry.Fill sink=(x,y,rw,rh,c)->{for(int yy=y;yy<y+rh;yy++)for(int xx=x;xx<x+rw;xx++)actual[yy*w+xx]++;};
            RoundedRectGeometry.fill(sink,0,0,w,h,radius,1);
            int r=Math.min(radius,Math.min(w,h)/2);
            for(int row=0;row<h;row++){int edge=row<r?r-row-1:row>=h-r?row-(h-r):0;int dx=edge>0?(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-edge*edge))):0;for(int col=dx;col<w-dx;col++)expected[row*w+col]++;}
            assertArrayEquals(expected,actual,"fill "+w+"x"+h+" r"+r);
            Arrays.fill(actual,0);Arrays.fill(expected,0);RoundedRectGeometry.outline(sink,0,0,w,h,radius,1);
            for(int row=0;row<h;row++){int edge=row<r?r-row-1:row>=h-r?row-(h-r):0;int dx=edge>0?(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-edge*edge))):0;if(row==0||row==h-1)for(int col=dx;col<w-dx;col++)expected[row*w+col]=1;else{expected[row*w+dx]=1;expected[row*w+w-dx-1]=1;}}
            // A one-pixel-wide border must not blend the same pixel twice.
            for(int i=0;i<actual.length;i++)actual[i]=Math.min(1,actual[i]);
            assertArrayEquals(expected,actual,"outline "+w+"x"+h+" r"+r);
        }
    }
    @Test void fullHeightPanelNeedsAtMost32DrawCommands(){
        int[] count={0};RoundedRectGeometry.Fill sink=(x,y,w,h,c)->count[0]++;
        RoundedRectGeometry.fill(sink,8,8,944,524,5,0xB8171717);RoundedRectGeometry.outline(sink,8,8,944,524,5,0x2EFFFFFF);
        assertTrue(count[0]<=32,"Draw count was "+count[0]);
    }
}
