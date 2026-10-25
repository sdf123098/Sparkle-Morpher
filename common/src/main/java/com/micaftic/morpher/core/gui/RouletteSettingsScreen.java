package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.client.gui.CustomRouletteEditorScreen;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Reference 62; all values update existing persisted configuration. */
public final class RouletteSettingsScreen extends Screen {
    private final Screen parent;
    private final String modelId;
    private final ModelAssembly assembly;
    private final AnimatableEntity<?> target;
    private int rowOffset;
    private int visibleRows(){var c=IndependentPanelChrome.of(width,height);return Math.max(1,Math.min(5,(c.bottom()-30-(c.top()+76))/rowStride()));}
    public RouletteSettingsScreen(Screen parent,String id,ModelAssembly assembly,AnimatableEntity<?> target){super(Component.translatable("gui.sparkle_morpher.roulette.settings.title"));this.parent=parent;modelId=id;this.assembly=assembly;this.target=target;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        var c=IndependentPanelChrome.of(width,height);RoulettePanelStyle.glassPanel(g,c.left(),c.top(),c.right()-c.left(),c.bottom()-c.top());int x=c.contentLeft(),w=c.contentRight()-x,y=c.top()+42;
        IndependentPanelChrome.text(g,font,title,x,c.top()+15,1.1f,RoulettePanelStyle.TEXT);
        RoulettePanelStyle.Glyph[] icons={RoulettePanelStyle.Glyph.UP,RoulettePanelStyle.Glyph.EDIT,RoulettePanelStyle.Glyph.MODEL};for(int i=0;i<3;i++)IndependentPanelChrome.icon(g,mx,my,x+i*26,y,icons[i]);
        String[] labels={Component.translatable("gui.sparkle_morpher.roulette.settings.content_mode").getString(),Component.translatable("gui.sparkle_morpher.roulette.settings.message_sound").getString(),Component.translatable("gui.sparkle_morpher.roulette.settings.sound_volume").getString(),Component.translatable("gui.sparkle_morpher.roulette.settings.custom_layout").getString(),Component.translatable("gui.sparkle_morpher.roulette.settings.model_settings").getString()};String[] values={GeneralConfig.ROULETTE_CONTENT_MODE.get()==GeneralConfig.RouletteContentMode.CUSTOM?Component.translatable("gui.sparkle_morpher.roulette.settings.custom").getString()+" ▾":Component.translatable("gui.sparkle_morpher.roulette.settings.original").getString()+" ▾","",Math.round(GeneralConfig.SOUND_VOLUME.get())+"%",Component.translatable("gui.sparkle_morpher.roulette.settings.edit").getString()+" ›",Component.translatable("gui.sparkle_morpher.roulette.settings.open").getString()+" ›"};
        rowOffset=Math.max(0,Math.min(5-visibleRows(),rowOffset));int buttonW=Math.min(150,w/2-8);
        for(int i=rowOffset;i<Math.min(5,rowOffset+visibleRows());i++){int yy=y+34+(i-rowOffset)*rowStride();g.text(font,Component.literal(RoulettePanelStyle.trim(font,labels[i],w-buttonW-30)),x+10,yy+12,RoulettePanelStyle.TEXT,false);RoulettePanelStyle.fill(g,x,yy+rowStride()-1,w,1,0x14FFFFFF);if(i==1){int tx=x+w-38;boolean on=GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.get();RoulettePanelStyle.roundedFill(g,tx,yy+12,26,12,6,on?RoulettePanelStyle.RED:0x66444444);RoulettePanelStyle.roundedFill(g,tx+(on?15:2),yy+13,9,10,4,RoulettePanelStyle.TEXT);}else IndependentPanelChrome.button(g,font,mx,my,x+w-buttonW-10,yy+4,buttonW,Component.literal(values[i]),false);}
        if(visibleRows()<5){IndependentPanelChrome.button(g,font,mx,my,x,c.bottom()-24,28,Component.literal("‹"),false);IndependentPanelChrome.button(g,font,mx,my,x+82,c.bottom()-24,28,Component.literal("›"),false);g.text(font,Component.literal((rowOffset+1)+"–"+(rowOffset+visibleRows())+" / 5"),x+34,c.bottom()-18,RoulettePanelStyle.MUTED,false);}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean flag){
        if(e.button()!=0)return super.mouseClicked(e,flag);var c=IndependentPanelChrome.of(width,height);int x=c.contentLeft(),w=c.contentRight()-x,y=c.top()+42;
        if(IndependentPanelChrome.inside(e.x(),e.y(),x,y,78,22)){int i=(int)(e.x()-x)/26;if(i==0)onClose();else if(i==1)InputUtil.setScreen(new CustomRouletteEditorScreen(modelId,assembly,this));else InputUtil.setScreen(new ModelSettingsScreen(assembly,target,this,null));return true;}
        if(visibleRows()<5&&IndependentPanelChrome.inside(e.x(),e.y(),x,c.bottom()-24,110,22)){rowOffset=Math.max(0,Math.min(5-visibleRows(),rowOffset+(e.x()<x+55?-visibleRows():visibleRows())));return true;}
        int index=rowOffset+(int)(e.y()-y-34)/rowStride();if(e.y()<y+34||index<0||index>4||!IndependentPanelChrome.inside(e.x(),e.y(),x,y+34,w,visibleRows()*rowStride()))return super.mouseClicked(e,flag);
        switch(index){case 0->{GeneralConfig.ROULETTE_CONTENT_MODE.set(GeneralConfig.ROULETTE_CONTENT_MODE.get()==GeneralConfig.RouletteContentMode.CUSTOM?GeneralConfig.RouletteContentMode.ORIGINAL:GeneralConfig.RouletteContentMode.CUSTOM);GeneralConfig.ROULETTE_CONTENT_MODE.save();}
            case 1->{GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.set(!GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.get());GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.save();}
            case 2->{double current=GeneralConfig.SOUND_VOLUME.get();GeneralConfig.SOUND_VOLUME.set(current>=100?0:Math.min(100,current+5));GeneralConfig.SOUND_VOLUME.save();}
            case 3->InputUtil.setScreen(new CustomRouletteEditorScreen(modelId,assembly,this));case 4->InputUtil.setScreen(new ModelSettingsScreen(assembly,target,this,null));}
        return true;
    }
    private int rowStride(){return Math.max(26,Math.min(47,(height-100)/5));}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(sy!=0){rowOffset=Math.max(0,Math.min(5-visibleRows(),rowOffset+(sy>0?-1:1)));return true;}return super.mouseScrolled(mx,my,sx,sy);}
    @Override public void onClose(){InputUtil.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
