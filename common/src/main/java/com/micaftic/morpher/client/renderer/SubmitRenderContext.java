package com.micaftic.morpher.client.renderer;

import net.minecraft.client.renderer.SubmitNodeCollector;

public final class SubmitRenderContext {

    private static final ThreadLocal<SubmitNodeCollector> CURRENT = new ThreadLocal<>();

    private SubmitRenderContext() {
    }

    public static void set(SubmitNodeCollector collector) {
        if (collector == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(collector);
        }
    }

    public static SubmitNodeCollector get() {
        return CURRENT.get();
    }
}
