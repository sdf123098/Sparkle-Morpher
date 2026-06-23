package net.minecraft.client.input;
public class MouseButtonEvent implements InputWithModifiers {
    public double mouseX;
    public double mouseY;
    public int button;
    public MouseButtonEvent(double x, double y, int button) {
        this.mouseX = x; this.mouseY = y; this.button = button;
    }
    public MouseButtonEvent(double x, double y, net.minecraft.client.input.MouseButtonInfo info) {
        this.mouseX = x; this.mouseY = y; this.button = info.button();
    }
    public double x() { return mouseX; }
    public double y() { return mouseY; }
    public int button() { return button; }
    public int modifiers() { return 0; }
    public int input() { return button; }
    public net.minecraft.client.input.MouseButtonInfo buttonInfo() { return new net.minecraft.client.input.MouseButtonInfo(button, 0); }
}
