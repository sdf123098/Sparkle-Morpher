package com.micaftic.morpher.core.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiElementLayoutTest {
    @Test void squareBackgroundFillsTallCardInsteadOfLeavingTopAndBottomGaps(){
        var uv=UiElementLayout.cover(256,256,79,104);
        assertEquals(0,uv.v0(),.0001);assertEquals(1,uv.v1(),.0001);
        assertTrue(uv.u0()>0&&uv.u1()<1);assertEquals(.5,(uv.u0()+uv.u1())/2,.0001);
        assertEquals(79.0/104,(uv.u1()-uv.u0())/(uv.v1()-uv.v0()),.0001);
    }
    @Test void wideAndTallBackgroundsCropWithEqualPixelScaleOnBothAxes(){
        for(int[] s:new int[][]{{1920,1080},{1080,1920},{1024,128},{256,256}}){
            var uv=UiElementLayout.cover(s[0],s[1],79,104);
            assertTrue(uv.u0()>=0&&uv.v0()>=0&&uv.u1()<=1&&uv.v1()<=1);
            assertEquals((uv.u1()-uv.u0())*s[0]/79,(uv.v1()-uv.v0())*s[1]/104,.0001);
        }
    }
    @Test void segmentedLabelsAndSingleLineHeadersAlignToButtonCenter(){
        for(int height:new int[]{18,22,26}){
            int buttonY=14,textY=UiElementLayout.centeredY(buttonY,height,9);
            assertTrue(Math.abs((textY+4.5)-(buttonY+height/2.0))<=.5);
            var h=UiElementLayout.header(buttonY,height,9,false);
            assertTrue(Math.abs(h.titleY()+5.5-(buttonY+height/2.0))<=.5);
        }
    }
}
