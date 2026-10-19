package com.micaftic.morpher.client.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NavigationHistoryTest {
    @Test void nestedCloudRoutesReturnToTheActualPreviousPageIncludingForms(){
        var history=new NavigationHistory<String>();
        for(String route:new String[]{"appearance","account","instances","edit instance","scopes"})history.push(route);
        for(String expected:new String[]{"scopes","edit instance","instances","account","appearance"})assertEquals(expected,history.back().orElseThrow());
        assertTrue(history.back().isEmpty());
    }
    @Test void repeatedVisitsDoNotTrapBackInTheSamePage(){
        var history=new NavigationHistory<String>();history.push("account");history.push("account");
        assertEquals("account",history.back().orElseThrow());assertTrue(history.back().isEmpty());
    }
}
