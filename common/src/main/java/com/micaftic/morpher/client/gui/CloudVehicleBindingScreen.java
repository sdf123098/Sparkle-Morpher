package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.CloudVehicleModelSync;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** User explicitly chooses a real vehicle and an existing authorized Cloud target. */
public final class CloudVehicleBindingScreen extends Screen {
    private final Screen parent;
    private final CloudManagementController management;
    private EditBox entity,target;
    private Button status;
    private boolean active;
    private long generation;
    public CloudVehicleBindingScreen(Screen parent,CloudManagementController management){super(Component.translatable("gui.sparkle_morpher.cloud.vehicle.bindings"));this.parent=parent;this.management=management;}
    @Override protected void init(){
        active=true;clearWidgets();var l=ModelPanelLayout.create(width,height);int x=l.contentLeft,w=Math.min(530,l.contentWidth),fy=entityY();
        String old=entity==null?null:entity.getValue();entity=new EditBox(font,x+6,fy+6,w-12,12,Component.translatable("gui.sparkle_morpher.cloud.vehicle.entity"));entity.setMaxLength(36);entity.setBordered(false);
        var client=Minecraft.getInstance();var chosen=client.player==null?null:client.player.getVehicle();if(chosen==null&&client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)chosen=hit.getEntity();if(old!=null)entity.setValue(old);else if(chosen!=null)entity.setValue(chosen.getUUID().toString());addWidget(entity);
        String previous=target==null?null:target.getValue();target=new EditBox(font,x+6,targetY()+6,w-12,12,Component.translatable("gui.sparkle_morpher.cloud.vehicle.target"));target.setMaxLength(128);target.setBordered(false);var selected=management.snapshot().selectedTarget();if(previous!=null)target.setValue(previous);else if(selected!=null)target.setValue(selected.targetId());addWidget(target);
        status=Button.builder(Component.translatable("gui.sparkle_morpher.cloud.vehicle.help"),b->{}).bounds(0,0,1,1).build();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        RoulettePanelStyle.applicationRail(g,width,height,3);var l=ModelPanelLayout.create(width,height);int x=l.contentLeft,w=l.contentWidth,fw=Math.min(530,w);g.text(font,title,x+32,l.top+10,RoulettePanelStyle.TEXT,false);RoulettePanelStyle.iconButton(g,mx,my,x,l.top+6,RoulettePanelStyle.Glyph.UP,true);RoulettePanelStyle.fill(g,x,l.top+49,w,1,0x18FFFFFF);
        g.text(font,Component.translatable("gui.sparkle_morpher.cloud.vehicle.entity"),x,entityY()-17,RoulettePanelStyle.TEXT,false);RoulettePanelStyle.roundedSurface(g,x,entityY(),fw,24,3,0x0CFFFFFF,0x22FFFFFF);entity.extractWidgetRenderState(g,mx,my,pt);g.text(font,Component.translatable("gui.sparkle_morpher.cloud.vehicle.target"),x,targetY()-17,RoulettePanelStyle.TEXT,false);RoulettePanelStyle.roundedSurface(g,x,targetY(),fw,24,3,0x0CFFFFFF,0x22FFFFFF);target.extractWidgetRenderState(g,mx,my,pt);g.text(font,Component.literal(RoulettePanelStyle.trim(font,status.getMessage().getString(),w)),x,targetY()+42,RoulettePanelStyle.MUTED,false);
        int by=height-l.top-34;button(g,x+w-184,by,76,Component.translatable("gui.cancel"),false);button(g,x+w-100,by,100,Component.translatable("gui.sparkle_morpher.cloud.vehicle.bind"),true);button(g,x,by,100,Component.translatable("gui.sparkle_morpher.cloud.vehicle.unbind"),false);
    }
    private int entityY(){return height<320?64:87;}
    private int targetY(){return height<320?112:161;}
    private void button(GuiGraphicsExtractor g,int x,int y,int w,Component text,boolean primary){RoulettePanelStyle.roundedFill(g,x,y,w,22,3,primary?RoulettePanelStyle.RED:0x18FFFFFF);g.centeredText(font,text,x+w/2,y+7,RoulettePanelStyle.TEXT);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean flag){var l=ModelPanelLayout.create(width,height);int x=l.contentLeft,w=l.contentWidth,by=height-l.top-34;if(com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(e.button())!=0)return super.mouseClicked(e,flag);if(RoulettePanelStyle.applicationRailClick(e.x(),e.y(),width,height,this::navigateFromRail))return true;if(e.y()<l.top+36&&e.x()>=x){onClose();return true;}if(RoulettePanelStyle.inside(e.x(),e.y(),x+w-100,by,100,22)){publish(false);return true;}if(RoulettePanelStyle.inside(e.x(),e.y(),x+w-184,by,76,22)){onClose();return true;}if(RoulettePanelStyle.inside(e.x(),e.y(),x,by,100,22)){publish(true);return true;}return super.mouseClicked(e,flag);}
    private void navigateFromRail(int page){switch(page){case 0->InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.MODEL));case 1->InputUtil.setScreen(ModernPlayerModelScreen.resourceStation());case 2->FakePlayerManagerScreen.open();case 3->InputUtil.setScreen(ModernPlayerModelScreen.settings());default->onClose();}}
    private void publish(boolean remove){
        try {long token=generation;status.setMessage(Component.translatable("gui.sparkle_morpher.cloud.vehicle.pending"));
            CloudVehicleModelSync.bind(UUID.fromString(entity.getValue().trim()),target.getValue().trim(),remove).whenComplete((value,error)->Minecraft.getInstance().execute(()->{
                if(!active||token!=generation)return;
                status.setMessage(error==null?Component.translatable("gui.sparkle_morpher.cloud.vehicle.saved"):Component.literal(CloudManagementScreen.errorText(error)));
            }));
        }catch(RuntimeException error){status.setMessage(Component.literal(CloudManagementScreen.errorText(error)));}
    }
    @Override public void removed(){active=false;generation++;}
    @Override public void onClose(){InputUtil.setScreen(parent);}
}
