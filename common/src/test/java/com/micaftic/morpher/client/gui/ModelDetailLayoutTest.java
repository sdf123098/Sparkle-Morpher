package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModelDetailLayoutTest {
    @Test void previewMetadataAndActionsDoNotOverlapAtScalesTwoThreeAndFour(){
        for(int[] size:new int[][]{{213,428},{144,238},{100,156}})for(boolean editable:new boolean[]{false,true}){
            var d=ModelDetailLayout.measure(64,size[1],size[0],editable);
            assertTrue(d.previewY()+d.previewHeight()+4<=d.nameY());
            assertTrue(d.nameY()+9<d.metadataY());assertTrue(d.metadataY()+9<d.textureY());
            assertTrue(d.textureY()+d.controlHeight()<=64+size[1]-8);
            if(editable)assertTrue(d.textureY()+d.controlHeight()<d.actionsY());
        }
    }
}
