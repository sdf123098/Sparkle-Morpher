package com.micaftic.morpher.client.renderer.preview;

/** A native PIP renderer owns one texture: reuse it only for the same static card. */
public final class StaticPreviewCache {
    private Object renderedKey;
    public boolean canReuse(Object key){return key!=null&&key.equals(renderedKey);}
    public void rendered(Object key){renderedKey=key;}
}
