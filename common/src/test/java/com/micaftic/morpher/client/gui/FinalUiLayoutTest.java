package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FinalUiLayoutTest {
 @Test void fullHdGuiTwoAddsNinthColumnWithoutShrinkingCards(){
  var shell=ModelPanelLayout.create(960,540);assertTrue(shell.verticalTabs);
  assertEquals(8,shell.left);assertEquals(36,shell.railWidth);assertEquals(52,shell.contentLeft);
  assertEquals(896,shell.contentWidth);
  var cards=ModelPickerLayout.cards(761,428);assertEquals(9,cards.cols());assertEquals(3,cards.rows());
  assertEquals(81,cards.cellW());assertEquals(140,cards.cellH());assertEquals(428,cards.blockH());
 }
 @Test void narrowWindowsGiveReadableCardsTheWholeContentWidth(){
  var shell=ModelPanelLayout.create(480,270);assertTrue(shell.verticalTabs);
  assertEquals(6,shell.left);assertEquals(28,shell.railWidth);assertEquals(42,shell.contentLeft);
  assertEquals(428,shell.contentWidth);
  int listWidth=ModelPickerLayout.listWidth(shell.contentWidth,178);
  assertEquals(shell.contentWidth,listWidth);
  var cards=ModelPickerLayout.cards(listWidth,178);
  assertTrue(cards.cellW()>=ModelPickerLayout.CARD_MIN_W);
  assertTrue(cards.blockW()<=listWidth&&cards.blockH()<=178);
 }
 @Test void regularWindowsKeepNineColumnsAndRoomForTheAlignedPreview(){
  for(int height:new int[]{496,504,540}){
   var shell=ModelPanelLayout.create(960,height);
   int gridH=shell.footerTop-10-(shell.top+66);
   int listW=ModelPickerLayout.listWidth(shell.contentWidth,gridH);
   var cards=ModelPickerLayout.cards(listW,gridH);
   assertEquals(9,cards.cols(),"nine columns at GUI 2, height="+height);
   assertEquals(3,cards.rows());
   assertTrue(shell.contentWidth-cards.blockW()-4>=120,"preview remains readable");
   assertTrue(cards.blockH()<=gridH,"preview bottom aligns within the viewport");
  }
 }
 @Test void smallerLayoutsReduceColumnsBeforeShrinkingReadableCards(){
  for(int[] size:new int[][]{{581,252},{428,178},{323,178}}){
   var cards=ModelPickerLayout.cards(ModelPickerLayout.listWidth(size[0],size[1]),size[1]);
   assertTrue(cards.cellW()>=ModelPickerLayout.CARD_MIN_W);
   assertTrue(cards.blockH()<=size[1]);
  }
 }
}
