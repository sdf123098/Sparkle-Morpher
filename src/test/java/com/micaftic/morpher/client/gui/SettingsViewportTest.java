package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SettingsViewportTest {
    @Test void wheelReachesEverySettingAndMovesItsClickTargetWithIt(){
        var view=new SettingsViewport();view.measure(55,242,55+24*26);
        assertFalse(view.fullyVisible(55+23*26,22));
        for(int i=0;i<100;i++)view.scroll(-1);
        assertEquals(view.maxOffset(),view.offset());
        assertTrue(view.fullyVisible(55+23*26,22));assertEquals(242-26,view.screenY(55+23*26));
        for(int i=0;i<100;i++)view.scroll(1);
        assertEquals(0,view.offset());assertTrue(view.fullyVisible(55,22));
    }
    @Test void fractionalWheelInputAccumulatesAndShrinkingContentClampsTheOffset(){
        var view=new SettingsViewport();view.measure(50,240,500);
        view.scroll(-.125);assertEquals(3,view.offset());view.scroll(-.125);assertEquals(6,view.offset());
        view.measure(50,240,180);assertEquals(0,view.offset());assertFalse(view.scroll(-1));
    }
    @Test void settingsRowsHaveFourToEightPixelsAroundNativeButtonsAtAllThreeScales(){
        for(int[] sizes:new int[][]{{480,18},{640,20},{960,22}}){
            int row=SettingsViewport.rowHeight(sizes[0]);assertEquals(8,row-sizes[1]);assertTrue(row<=30);
        }
    }
}
