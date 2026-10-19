package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakePlayerManagerLayoutTest {
    @Test void regularManagerMatchesScene53ToolbarAndTableGeometry(){
        var layout=FakePlayerManagerScreen.Layout.create(960,540);
        assertEquals(52,layout.x());
        assertEquals(896,layout.w());
        assertEquals(49,layout.toolbarY());
        assertEquals(22,layout.toolbarH());
        assertEquals(310,layout.searchW());
        assertEquals(368,layout.actionX(0));
        assertEquals(480,layout.actionX(4));
        assertEquals(584,layout.actionX(8));
        assertEquals(86,layout.headerY());
        assertEquals(111,layout.listY());
        assertEquals(35,layout.rowH());
        assertEquals(516,layout.footerY());
    }

    @Test void compactManagerKeepsEveryActionAndRowInsideMainShell(){
        var layout=FakePlayerManagerScreen.Layout.create(480,270);
        var shell=ModelPanelLayout.create(480,270);
        assertEquals(shell.contentLeft,layout.x());
        assertEquals(shell.contentWidth,layout.w());
        assertTrue(layout.searchW()>0);
        for(int index=0;index<9;index++){
            assertTrue(layout.actionX(index)>=layout.x()+layout.searchW());
            assertTrue(layout.actionX(index)+layout.toolbarH()<=layout.x()+layout.w());
        }
        assertTrue(layout.visibleRows()>0);
        assertTrue(layout.listY()+layout.visibleRows()*layout.rowH()<layout.footerY());
        for(int column=1;column<4;column++)assertTrue(layout.columnX(column)>layout.columnX(column-1));
    }
}
