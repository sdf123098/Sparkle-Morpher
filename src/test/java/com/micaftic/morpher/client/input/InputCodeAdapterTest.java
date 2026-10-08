package com.micaftic.morpher.client.input;

import com.micaftic.morpher.core.api.client.InputCodeAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputCodeAdapterTest {

    @Test
    void mapsLetterScancodesToLegacyGlfwKeyNumbers() {
        assertEquals(66, InputCodeAdapter.legacyKeyCodeFromSdlScancode(5));
        assertEquals(71, InputCodeAdapter.legacyKeyCodeFromSdlScancode(10));
        assertEquals(76, InputCodeAdapter.legacyKeyCodeFromSdlScancode(15));
        assertEquals(89, InputCodeAdapter.legacyKeyCodeFromSdlScancode(28));
        assertEquals(90, InputCodeAdapter.legacyKeyCodeFromSdlScancode(29));
    }

    @Test
    void preservesArrowAndLeftRightShiftScriptCodes() {
        assertEquals(262, InputCodeAdapter.legacyKeyCodeFromSdlScancode(79));
        assertEquals(263, InputCodeAdapter.legacyKeyCodeFromSdlScancode(80));
        assertEquals(264, InputCodeAdapter.legacyKeyCodeFromSdlScancode(81));
        assertEquals(265, InputCodeAdapter.legacyKeyCodeFromSdlScancode(82));
        assertEquals(340, InputCodeAdapter.legacyKeyCodeFromSdlScancode(225));
        assertEquals(344, InputCodeAdapter.legacyKeyCodeFromSdlScancode(229));
    }

    @Test
    void preservesMouseButtonSemanticsAndRejectsUnknownValues() {
        assertEquals(0, InputCodeAdapter.legacyMouseButtonFromSdlButton(1));
        assertEquals(2, InputCodeAdapter.legacyMouseButtonFromSdlButton(2));
        assertEquals(1, InputCodeAdapter.legacyMouseButtonFromSdlButton(3));
        assertEquals(3, InputCodeAdapter.legacyMouseButtonFromSdlButton(4));
        assertEquals(-1, InputCodeAdapter.legacyMouseButtonFromSdlButton(0));
        assertEquals(-1, InputCodeAdapter.legacyMouseButtonFromSdlButton(9));
    }

    @Test
    void mapsLegacyDefaultKeysToNativeKeyboardCodes() {
        assertEquals(5, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(66));
        assertEquals(10, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(71));
        assertEquals(15, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(76));
        assertEquals(28, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(89));
        assertEquals(29, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(90));
        assertEquals(-1, InputCodeAdapter.sdlScancodeFromLegacyKeyCode(999));
    }
}
