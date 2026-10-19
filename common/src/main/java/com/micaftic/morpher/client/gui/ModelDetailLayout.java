package com.micaftic.morpher.client.gui;

/** Reserve metadata and controls first; the preview uses the remaining height. */
final class ModelDetailLayout {
    record Detail(int previewY,int previewHeight,int nameY,int metadataY,int textureY,int actionsY,int controlHeight) {}
    static Detail measure(int top,int height,int width,boolean editable) {
        int control = width < 130 ? 18 : width < 180 ? 20 : 22;
        int bottom=top+height-8;
        int actions=editable?bottom-control:-1;
        int texture=editable?actions-control-5:bottom-control;
        int metadata=texture-14, name=metadata-14;
        int previewY=top+22;
        return new Detail(previewY,Math.max(12,name-previewY-6),name,metadata,texture,actions,control);
    }
}
