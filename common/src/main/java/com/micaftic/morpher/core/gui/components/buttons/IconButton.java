package com.micaftic.morpher.core.gui.components.buttons;

import net.minecraft.network.chat.Component;

public final class IconButton {
    public final int x;
    public final int y;
    public final int size;
    public final int u;
    public final int v;
    public final Runnable onPress;
    public final Component tooltip;

    public IconButton(int x, int y, int size, int u, int v, Runnable onPress, Component tooltip) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.u = u;
        this.v = v;
        this.onPress = onPress;
        this.tooltip = tooltip;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }
}
