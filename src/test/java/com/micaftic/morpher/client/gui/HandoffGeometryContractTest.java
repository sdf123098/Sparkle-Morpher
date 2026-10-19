package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandoffGeometryContractTest {
    @Test void uploadToolbarKeepsTargetAndSearchOnTheReferenceRowAtBothGuiScales(){
        var normal=FinalUiLayout.scene("upload",960,540);
        assertEquals(new FinalUiLayout.Rect(134,44,220,22),normal.control(3));
        assertEquals(new FinalUiLayout.Rect(416,44,368,22),normal.control(6));
        var compact=FinalUiLayout.scene("upload",480,270);
        assertEquals(new FinalUiLayout.Rect(112,42,112,18),compact.control(3));
        assertEquals(new FinalUiLayout.Rect(278,42,104,18),compact.control(6));
    }
    @Test void accountAndInstanceControlsFollowTheFullContentReferenceRatherThanNestedColumns(){
        var normal=FinalUiLayout.scene("account",960,540);
        assertEquals(new FinalUiLayout.Rect(52,57,280,24),normal.control(0));
        assertEquals(new FinalUiLayout.Rect(798,367,150,24),normal.control(8));
        var compact=FinalUiLayout.scene("instances",480,270);
        assertEquals(new FinalUiLayout.Rect(172,72,298,144),compact.area("detailRect"));
        assertEquals(new FinalUiLayout.Rect(42,42,96,18),compact.control(0));
    }
    @Test void performanceControlsKeepTheNativeSizeAndAnchorToTheRightOnWiderWindows(){
        assertEquals(new FinalUiLayout.Rect(654,57,140,26),FinalUiLayout.scene("performance",960,540).control(0));
        assertEquals(new FinalUiLayout.Rect(284,49,88,26),FinalUiLayout.scene("performance",480,270).control(0));
        assertEquals(new FinalUiLayout.Rect(974,57,140,26),FinalUiLayout.scene("performance",1280,720).control(0));
    }
}
