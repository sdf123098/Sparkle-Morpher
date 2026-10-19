package com.micaftic.morpher.client.gui;
/** Compatibility entry points all use the final top-level manager. */
public final class FakePlayerModelScreen {
    private FakePlayerModelScreen(){}
    public static void open(){FakePlayerManagerScreen.open();}
    public static void openFromCrosshair(){FakePlayerManagerScreen.open();}
}
