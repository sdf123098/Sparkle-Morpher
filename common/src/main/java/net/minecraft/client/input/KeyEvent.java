package net.minecraft.client.input;
public class KeyEvent implements InputWithModifiers {
    public int keyCode;
    public int scanCode;
    public int modifiers;
    public int modifiers() { return modifiers; }
    public int input() { return keyCode; }
    public KeyEvent(int key, int scancode, int mods) {
        this.keyCode = key; this.scanCode = scancode; this.modifiers = mods;
    }
    public int key() { return keyCode; }
    public int scancode() { return scanCode; }
}
