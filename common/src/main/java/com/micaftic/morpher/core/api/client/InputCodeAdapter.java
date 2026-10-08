package com.micaftic.morpher.core.api.client;

/** Converts 26.3 SDL scancodes to the GLFW key numbers used by existing model scripts. */
public final class InputCodeAdapter {
    public static final int SDL_SCANCODE_B = 5;
    public static final int SDL_SCANCODE_G = 10;
    public static final int SDL_SCANCODE_L = 15;
    public static final int SDL_SCANCODE_Y = 28;
    public static final int SDL_SCANCODE_Z = 29;
    public static final int SDL_SCANCODE_RIGHT = 79;
    public static final int SDL_SCANCODE_LEFT = 80;
    public static final int SDL_SCANCODE_LEFT_SHIFT = 225;
    public static final int SDL_SCANCODE_RIGHT_SHIFT = 229;

    private InputCodeAdapter() {
    }

    public static int legacyKeyCodeFromSdlScancode(int scancode) {
        if (scancode >= 4 && scancode <= 29) {
            return 65 + scancode - 4;
        }
        if (scancode >= 30 && scancode <= 38) {
            return 49 + scancode - 30;
        }
        if (scancode == 39) {
            return 48;
        }
        if (scancode >= 58 && scancode <= 69) {
            return 290 + scancode - 58;
        }
        if (scancode >= 104 && scancode <= 115) {
            return 302 + scancode - 104;
        }
        return switch (scancode) {
            case 40 -> 257; // Enter
            case 41 -> 256; // Escape
            case 42 -> 259; // Backspace
            case 43 -> 258; // Tab
            case 44 -> 32;  // Space
            case 45 -> 45;  // Minus
            case 46 -> 61;  // Equal
            case 47 -> 91;  // Left bracket
            case 48 -> 93;  // Right bracket
            case 49, 50 -> 92; // Backslash
            case 51 -> 59;  // Semicolon
            case 52 -> 39;  // Apostrophe
            case 53 -> 96;  // Grave accent
            case 54 -> 44;  // Comma
            case 55 -> 46;  // Period
            case 56 -> 47;  // Slash
            case 57 -> 280; // Caps lock
            case 70 -> 283; // Print screen
            case 71 -> 281; // Scroll lock
            case 72 -> 284; // Pause
            case 73 -> 260; // Insert
            case 74 -> 268; // Home
            case 75 -> 266; // Page up
            case 76 -> 261; // Delete
            case 77 -> 269; // End
            case 78 -> 267; // Page down
            case 79 -> 262; // Right
            case 80 -> 263; // Left
            case 81 -> 264; // Down
            case 82 -> 265; // Up
            case 83 -> 282; // Num lock
            case 84 -> 331; // Keypad divide
            case 85 -> 332; // Keypad multiply
            case 86 -> 333; // Keypad subtract
            case 87 -> 334; // Keypad add
            case 88 -> 335; // Keypad enter
            case 89 -> 321; // Keypad 1
            case 90 -> 322; // Keypad 2
            case 91 -> 323; // Keypad 3
            case 92 -> 324; // Keypad 4
            case 93 -> 325; // Keypad 5
            case 94 -> 326; // Keypad 6
            case 95 -> 327; // Keypad 7
            case 96 -> 328; // Keypad 8
            case 97 -> 329; // Keypad 9
            case 98 -> 320; // Keypad 0
            case 99 -> 330; // Keypad decimal
            case 101 -> 348; // Menu
            case 224 -> 341; // Left control
            case 225 -> 340; // Left shift
            case 226 -> 342; // Left alt
            case 227 -> 343; // Left GUI
            case 228 -> 345; // Right control
            case 229 -> 344; // Right shift
            case 230 -> 346; // Right alt
            case 231 -> 347; // Right GUI
            default -> -1;
        };
    }

    public static int sdlScancodeFromLegacyKeyCode(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) {
            return 4 + keyCode - 65;
        }
        if (keyCode >= 49 && keyCode <= 57) {
            return 30 + keyCode - 49;
        }
        if (keyCode == 48) {
            return 39;
        }
        if (keyCode >= 290 && keyCode <= 301) {
            return 58 + keyCode - 290;
        }
        return switch (keyCode) {
            case 262 -> 79;
            case 263 -> 80;
            case 264 -> 81;
            case 265 -> 82;
            case 340 -> SDL_SCANCODE_LEFT_SHIFT;
            case 344 -> SDL_SCANCODE_RIGHT_SHIFT;
            default -> -1;
        };
    }

    public static int legacyMouseButtonFromSdlButton(int button) {
        if (button < 1 || button > 8) {
            return -1;
        }
        if (button == 1) {
            return 0;
        }
        if (button == 3) {
            return 1;
        }
        return button == 2 ? 2 : button - 1;
    }
}
