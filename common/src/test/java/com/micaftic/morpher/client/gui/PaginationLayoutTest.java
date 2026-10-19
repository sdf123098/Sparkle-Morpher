package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaginationLayoutTest {
    @Test void statusNumbersAndArrowsCannotOverlapAtEitherGuiScale(){
        for(int width:new int[]{100,140,324,674,896})for(int textWidth:new int[]{24,40,90}){
            var r=PaginationLayout.measure(52,506,width,textWidth,22);
            assertTrue(r.status().right()<=r.previous().x());
            assertTrue(r.previous().right()<r.label().x());
            assertTrue(r.label().right()<r.next().x());
            assertEquals(52+width,r.next().right());
            assertTrue(r.previous().x()>=52);
        }
    }
}
