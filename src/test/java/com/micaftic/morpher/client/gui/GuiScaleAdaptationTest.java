package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiScaleAdaptationTest {
    @Test void scaleFourKeepsTwoCompleteCardRowsIncludingAShorterWindow(){
        for(int height:new int[]{250,270,288}){
            var shell=ModelPanelLayout.create(480,height);
            int listW=Math.round(shell.contentWidth*.76f)-2;
            int available=shell.footerTop-10-(shell.top+58);
            var cards=ModelPickerLayout.cards(listW,available);
            assertEquals(2,cards.rows());assertTrue(cards.blockH()<=available);
            assertTrue(cards.cellW()>=ModelPickerLayout.FORCED_MIN_CARD_W);
        }
    }
    @Test void scaleThreeUsesItsOwnIntermediateReference(){
        var large=FinalUiLayout.scene("performance",960,540);
        var middle=FinalUiLayout.scene("performance",640,360);
        var small=FinalUiLayout.scene("performance",480,270);
        assertEquals("performance-gui3",middle.name());assertEquals(640,middle.referenceWidth());assertEquals(360,middle.referenceHeight());
        assertTrue(middle.control(0).w()>small.control(0).w());assertTrue(middle.control(0).w()<large.control(0).w());
        assertTrue(ModelPanelLayout.create(640,360).railWidth>ModelPanelLayout.create(480,270).railWidth);
        assertTrue(ModelPanelLayout.create(640,360).railWidth<ModelPanelLayout.create(960,540).railWidth);
    }
    @Test void cardSizesIncreaseBetweenFourThreeAndTwo(){
        var small=ModelPickerLayout.cards(323,166);var middle=ModelPickerLayout.cards(445,238);var large=ModelPickerLayout.cards(681,428);
        assertTrue(middle.cellW()>small.cellW());assertTrue(middle.cellW()<large.cellW());
        assertEquals(2,middle.rows());assertEquals(3,large.rows());
    }
}
