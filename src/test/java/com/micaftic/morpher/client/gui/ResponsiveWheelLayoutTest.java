package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.core.gui.ResponsiveUiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResponsiveWheelLayoutTest {
    @Test void wheelAndPageButtonsFitBetweenToolbarFooterAndRailAcrossAspectRatios(){
        for(int[] viewport:new int[][]{{320,180},{320,240},{427,239},{480,270},{640,360},{960,540},{360,640},{1280,720}}){
            int left=42,top=78,right=viewport[0]-10,bottom=viewport[1]-38;
            var wheel=ResponsiveUiLayout.wheel(left,top,right,bottom);
            assertTrue(wheel.scale()>0&&wheel.scale()<=1);
            assertTrue(wheel.centerX()-178*wheel.scale()>=left-1);
            assertTrue(wheel.centerX()+178*wheel.scale()<=right+1);
            assertTrue(wheel.centerY()-151*wheel.scale()>=top-1);
            assertTrue(wheel.centerY()+151*wheel.scale()<=bottom+1);
        }
    }
    @Test void aShortViewportUsesAllAvailableHeightWithoutAFixedMinimumScale(){
        var shortWheel=ResponsiveUiLayout.wheel(42,78,310,142);
        assertTrue(shortWheel.scale()<.25f);
        assertEquals(176,shortWheel.centerX());assertEquals(110,shortWheel.centerY());
        assertEquals(1f,ResponsiveUiLayout.wheel(60,90,1200,700).scale());
    }
}
