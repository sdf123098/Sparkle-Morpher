package com.micaftic.morpher.client.gui;

/** Native logical geometry taken from the final handoff: 960x540 / 480x270. */
final class ModelPanelLayout {
    final int left,top,width,height,tabHeight,footerHeight,railWidth,contentLeft,contentTop,contentWidth,contentHeight,footerTop;
    final boolean verticalTabs=true;
    private ModelPanelLayout(int screenWidth,int screenHeight){
        boolean compact=screenWidth<560;
        int margin=compact?6:screenWidth<800?7:8;
        left=top=margin;width=Math.max(1,screenWidth-2*margin);height=Math.max(1,screenHeight-2*margin);
        railWidth=compact?28:screenWidth<800?30:36;tabHeight=0;footerHeight=24;
        contentLeft=margin+railWidth+8;contentTop=margin;
        contentWidth=Math.max(1,screenWidth-contentLeft-margin-4);
        footerTop=screenHeight-margin-footerHeight;contentHeight=footerTop-contentTop;
    }
    static ModelPanelLayout create(int screenWidth,int screenHeight){return new ModelPanelLayout(screenWidth,screenHeight);}
}
