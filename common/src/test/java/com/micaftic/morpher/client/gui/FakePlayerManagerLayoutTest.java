package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakePlayerManagerLayoutTest {
    @Test void regularManagerAlignsToolbarAndTableWithTheSharedShell(){
        var layout=FakePlayerManagerScreen.Layout.create(960,540);
        assertEquals(52,layout.x());
        assertEquals(896,layout.w());
        assertEquals(ModelPanelLayout.create(960,540).top+32,layout.toolbarY());
        assertEquals(22,layout.toolbarH());
        assertEquals(260,layout.searchW());
        assertEquals(318,layout.actionX(0));
        assertEquals(422,layout.actionX(4));
        assertEquals(526,layout.actionX(8));
        assertEquals(74,layout.headerY());
        assertEquals(99,layout.listY());
        assertEquals(35,layout.rowH());
        assertEquals(516,layout.footerY());
    }

    @Test void compactManagerKeepsEveryActionAndRowInsideMainShell(){
        for(int[] viewport:new int[][]{{320,180},{320,240},{427,239},{480,270},{360,640}}){
        var layout=FakePlayerManagerScreen.Layout.create(viewport[0],viewport[1]);
        var shell=ModelPanelLayout.create(viewport[0],viewport[1]);
        assertEquals(shell.contentLeft,layout.x());
        assertEquals(shell.contentWidth,layout.w());
        assertTrue(layout.searchW()>0);
        for(int index=0;index<layout.visibleActions();index++){
            assertTrue(layout.actionX(index)>=layout.x()+layout.searchW());
            assertTrue(layout.actionX(index)+layout.toolbarH()<=layout.x()+layout.w());
        }
        if(layout.visibleActions()<9)assertTrue(layout.actionX(layout.visibleActions())+layout.toolbarH()<=layout.x()+layout.w());
        assertTrue(layout.visibleRows()>0);
        assertTrue(layout.listY()+layout.visibleRows()*layout.rowH()<layout.footerY());
        for(int column=1;column<4;column++)assertTrue(layout.columnX(column)>layout.columnX(column-1));
        }
    }
}
