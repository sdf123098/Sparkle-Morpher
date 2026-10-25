package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.core.gui.ResponsiveUiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResponsiveUiLayoutTest {
    private static final int[][] WINDOWS={{320,180},{360,203},{427,239},{480,270},{640,360},{854,480},{960,540},{1280,720},{320,568},{360,640},{560,240}};
    @Test void toolbarActionsAlwaysFitAndEveryHiddenActionRemainsReachable(){
        for(var size:WINDOWS){var shell=ModelPanelLayout.create(size[0],size[1]);
            for(int count:new int[]{1,7,11,12,20}){
                var bar=ResponsiveUiLayout.toolbar(shell.contentLeft,shell.top+32,shell.contentWidth,size[1],count);
                assertTrue(bar.searchWidth()>=40);
                for(int i=0;i<bar.visible()+(bar.overflow()?1:0);i++){
                    var button=bar.button(i);assertTrue(button.x()>=shell.contentLeft);assertTrue(button.right()<=shell.contentLeft+shell.contentWidth);
                    assertTrue(button.height()>=18);assertTrue(button.bottom()<=size[1]);
                }
                assertEquals(count,bar.visible()+(bar.overflow()?count-bar.visible():0));
            }
        }
    }
    @Test void screenshotSizedWindowsKeepReadableCardsAndGiveTheGridPriority(){
        for(var size:new int[][]{{427,239},{480,270},{320,568}}){
            var shell=ModelPanelLayout.create(size[0],size[1]);int listH=shell.footerTop-10-(shell.top+58);
            int listW=ModelPickerLayout.listWidth(shell.contentWidth,listH);
            assertEquals(shell.contentWidth,listW,"preview must open separately in a narrow viewport");
            var cards=ModelPickerLayout.cards(listW,listH);
            assertTrue(cards.cellW()>=ModelPickerLayout.CARD_MIN_W);
            assertTrue(cards.blockW()<=listW);assertTrue(cards.blockH()<=listH);
        }
    }
    @Test void menusModalsAndTouchPagingStayInsideSmallAndPortraitViewports(){
        for(var size:WINDOWS){
            var modal=ResponsiveUiLayout.modal(size[0],size[1],640,420);
            assertTrue(modal.x()>=0&&modal.y()>=0);assertTrue(modal.right()<=size[0]&&modal.bottom()<=size[1]);
            for(int count:new int[]{1,3,12,100}){
                int reached=0;
                for(int scroll=0;scroll<count;scroll++){
                    var menu=ResponsiveUiLayout.menu(size[0]-30,size[1]-10,260,size[0],size[1],count,scroll);
                    assertTrue(menu.bounds().right()<=size[0]&&menu.bounds().bottom()<=size[1]);
                    for(int i=0;i<menu.visible();i++){var row=menu.row(i);assertTrue(row.x()>=menu.bounds().x()&&row.right()<=menu.bounds().right());assertTrue(row.bottom()<=menu.bounds().bottom());}
                    if(menu.maxScroll()>0)assertTrue(menu.row(menu.visible()-1).bottom()+20<=menu.bounds().bottom(),"touch paging buttons need their own space");
                    reached=Math.max(reached,menu.first()+menu.visible());
                }
                assertEquals(count,reached);
            }
        }
    }
    @Test void authoredControlsScaleByActualLogicalSpaceAndLargeWindowAnchorsStayStable(){
        for(var size:WINDOWS){
            int referenceW=size[0]<560?480:960,referenceH=size[0]<560?270:540;
            int x=ResponsiveUiLayout.authoredPosition(420,referenceW,size[0],.65);
            int w=ResponsiveUiLayout.authoredSize(40,referenceW,size[0]);
            assertTrue(x>=0&&x+w<=size[0]);
            int y=ResponsiveUiLayout.authoredPosition(referenceH-30,referenceH,size[1],.88);
            int h=ResponsiveUiLayout.authoredSize(20,referenceH,size[1]);
            assertTrue(y>=0&&y+h<=size[1]);
        }
        assertEquals(40,ResponsiveUiLayout.authoredPosition(40,480,640,.65));
        assertEquals(580,ResponsiveUiLayout.authoredPosition(420,480,640,.65));
    }
    @Test void railNavigationDoesNotCollideWithTheBackButtonOnShortWindows(){
        for(var size:WINDOWS){var shell=ModelPanelLayout.create(size[0],size[1]);int top=shell.top+20+17;
            int stride=ResponsiveUiLayout.railStride(size[1],top,shell.top+32);
            assertTrue(top+3*stride+Math.min(24,stride)<=size[1]-shell.top-28);
        }
    }
}
