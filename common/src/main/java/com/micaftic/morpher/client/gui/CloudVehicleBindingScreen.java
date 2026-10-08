package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.CloudVehicleModelSync;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
        active=true;int left=Math.max(8,width/2-150),size=Math.min(300,width-16);
        entity=new EditBox(font,left,40,size,20,Component.translatable("gui.sparkle_morpher.cloud.vehicle.entity"));entity.setMaxLength(36);
        var client=Minecraft.getInstance();var chosen=client.player==null?null:client.player.getVehicle();
        if(chosen==null&&client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)chosen=hit.getEntity();
        if(chosen!=null)entity.setValue(chosen.getUUID().toString());addRenderableWidget(entity);
        target=new EditBox(font,left,66,size,20,Component.translatable("gui.sparkle_morpher.cloud.vehicle.target"));target.setMaxLength(128);
        var selected=management.snapshot().selectedTarget();if(selected!=null)target.setValue(selected.targetId());addRenderableWidget(target);
        status=addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.vehicle.help"),b->{}).bounds(left,92,size,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.vehicle.bind"),b->publish(false)).bounds(left,120,size/2-3,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.vehicle.unbind"),b->publish(true)).bounds(left+size/2+3,120,size/2-3,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width/2-50,height-27,100,20).build());
    }
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
