package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiScaleAdaptationTest {
    @Test void shortWindowsKeepReadableCardsWithinTheAvailableArea(){
        for(int height:new int[]{250,270,288}){
            var shell=ModelPanelLayout.create(480,height);
            int listW=ModelPickerLayout.listWidth(shell.contentWidth,shell.footerTop-10-(shell.top+58));
            int available=shell.footerTop-10-(shell.top+58);
            var cards=ModelPickerLayout.cards(listW,available);
            assertTrue(cards.rows()>=1);assertTrue(cards.blockH()<=available);assertTrue(cards.blockW()<=listW);
            assertTrue(cards.cellW()>=ModelPickerLayout.CARD_MIN_W);
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
    @Test void largerViewportsIncreaseCapacityWhileKeepingCardsReadable(){
        var small=ModelPickerLayout.cards(323,166);var middle=ModelPickerLayout.cards(445,238);var large=ModelPickerLayout.cards(681,428);
        assertTrue(small.cellW()>=ModelPickerLayout.CARD_MIN_W);assertTrue(middle.cellW()>=ModelPickerLayout.CARD_MIN_W);assertTrue(large.cellW()>=ModelPickerLayout.CARD_MIN_W);
        assertTrue(middle.capacity()>small.capacity());assertTrue(large.capacity()>middle.capacity());
        assertEquals(2,middle.rows());assertEquals(3,large.rows());
    }
}
