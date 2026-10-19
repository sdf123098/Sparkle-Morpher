package com.micaftic.morpher.client.gui;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CardPreviewRefreshBudgetTest {
    @Test void aDensePageNeverRebuildsAllModelsInOneFrameAndEverySlotGetsATurn(){
        var budget=new CardPreviewRefreshBudget();
        Set<Integer> seen=new HashSet<>();
        for(int frame=0;frame<7;frame++){
            budget.beginFrame(frame*16_666_667L,27);
            int updates=0;
            for(int slot=0;slot<27;slot++)if(budget.mayRefresh(slot)){
                seen.add(slot);updates++;budget.recordRefresh(100_000);
            }
            assertTrue(updates<=4,"full-model work must be bounded independently of page size");
        }
        assertEquals(27,seen.size(),"cards at the end of the page must not starve");
    }
    @Test void anExpensiveModelStopsFurtherWorkButTheNextFrameCanContinue(){
        var budget=new CardPreviewRefreshBudget();budget.beginFrame(0,27);
        assertTrue(budget.mayRefresh(0));budget.recordRefresh(8_000_000);
        for(int slot=1;slot<27;slot++)assertFalse(budget.mayRefresh(slot));
        budget.beginFrame(16_666_667,27);assertTrue(budget.mayRefresh(4));
    }
    @Test void animationRevisionsAreStaggeredAndIndependentOfMainFrameRate(){
        long t=1_000_000_000L;
        assertEquals(CardPreviewRefreshBudget.animationRevision(t,0),CardPreviewRefreshBudget.animationRevision(t+1_000_000,0));
        assertNotEquals(CardPreviewRefreshBudget.animationRevision(t,0),CardPreviewRefreshBudget.animationRevision(t+100_000_000,0));
        Set<Long> revisions=new HashSet<>();
        for(int slot=0;slot<27;slot++)revisions.add(CardPreviewRefreshBudget.animationRevision(t,slot));
        assertTrue(revisions.size()>1,"animation refreshes must not arrive as one page-wide burst");
    }
}
