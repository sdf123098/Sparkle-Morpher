package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.CloudScopeClient;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.core.gui.RoulettePanelStyle.Glyph;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Target directory and scope/target ACL controls for the selected Cloud scope. */
public final class CloudTargetManagementScreen extends Screen {
    private final Screen parent;
    private final CloudManagementController management;
    private EditBox targetId,targetKind,targetName,accountId,role;
    private boolean active,showingAcl,scopeAcl,createOpen;
    private long lifecycleGeneration;
    private int listPage;
    private String statusMessage;
    private ModelPanelLayout layout;
    private final List<Hit> hits=new ArrayList<>();
    private final List<Field> fields=new ArrayList<>();
    private record Hit(int x,int y,int w,int h,Runnable action){}
    private record Field(EditBox box,int x,int y,int w,int h){}

    CloudTargetManagementScreen(Screen parent,CloudManagementController management){
        super(Component.translatable("gui.sparkle_morpher.cloud.manage.target.title"));
        this.parent=parent;this.management=management;
    }
    @Override protected void init(){
        active=true;
        String account=accountId==null?"":accountId.getValue(),chosenRole=role==null?"EDITOR":role.getValue();
        String id=targetId==null?"":targetId.getValue(),kind=targetKind==null?"FAKE_PLAYER":targetKind.getValue(),name=targetName==null?"":targetName.getValue();
        clearWidgets();fields.clear();hits.clear();layout=ModelPanelLayout.create(width,height);
        int x=layout.contentLeft,w=layout.contentWidth,fy=formY();
        accountId=field(x,fy,(int)(w*.38),24,"Cloud 账号 ID",account);
        role=field(x+(int)(w*.40),fy,(int)(w*.24),24,"角色：OWNER / EDITOR / VIEWER",chosenRole);
        int dx=width/2-Math.min(440,w)/2,dw=Math.min(440,w),dy=height<400?32:86;
        targetId=field(dx+14,dy+40,dw-28,24,text("target.id"),id);
        targetKind=field(dx+14,dy+88,dw-28,24,text("target.kind"),kind);
        targetName=field(dx+14,dy+136,dw-28,24,text("target.name"),name);
        targetId.visible=targetKind.visible=targetName.visible=createOpen;
        accountId.visible=role.visible=!createOpen;
    }
    private int formY(){return height<400?Math.max(148,height-94):287;}
    private EditBox field(int x,int y,int w,int h,String hint,String value){
        EditBox box=new EditBox(font,x+8,y+(h-10)/2,Math.max(1,w-16),10,Component.literal(hint));
        box.setBordered(false);box.setMaxLength(256);box.setTextColor(RoulettePanelStyle.TEXT);box.setHint(Component.literal(hint));box.setValue(value);
        fields.add(new Field(box,x,y,w,h));addRenderableWidget(box);return box;
    }
    private void draw(GuiGraphicsExtractor g,String text,int x,int y,int color,int max){g.text(font,Component.literal(RoulettePanelStyle.trim(font,text,max)),x,y,color,false);}
    private void control(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,String label,Glyph glyph,boolean selected,boolean accent,Runnable action){
        boolean hover=RoulettePanelStyle.inside(mx,my,x,y,w,h);
        RoulettePanelStyle.roundedSurface(g,x,y,w,h,3,accent?RoulettePanelStyle.RED:hover?0x1FFFFFFF:0x0BFFFFFF,0x22FFFFFF);
        if(selected)RoulettePanelStyle.fill(g,x+8,y+h-1,w-16,1,RoulettePanelStyle.RED);
        if(glyph!=null)RoulettePanelStyle.drawIconScaled(g,glyph,x+(w-14)/2,y+(h-14)/2,14);
        else draw(g,label,x+Math.max(5,(w-font.width(label))/2),y+(h-9)/2,RoulettePanelStyle.TEXT,w-10);
        hits.add(new Hit(x,y,w,h,action));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        RoulettePanelStyle.applicationRail(g,width,height,3);hits.clear();
        int x=layout.contentLeft,w=layout.contentWidth,right=x+w;
        control(g,mx,my,x,14,22,22,"",Glyph.UP,false,false,this::onClose);
        draw(g,"目标与权限",x+32,18,RoulettePanelStyle.TEXT,w-100);
        var snap=management.snapshot();
        String instance=snap.profile()==null?"Cloud":CloudManagementScreen.displayName(snap.profile());
        String scope=snap.selectedScope()==null?"未选择范围":snap.selectedScope().name();
        String target=snap.selectedTarget()==null?"未选择目标":snap.selectedTarget().displayName();
        draw(g,instance+" · "+scope+" · "+target,x+32,38,RoulettePanelStyle.MUTED,w-80);

        RoulettePanelStyle.fill(g,x,57,w,1,0x26FFFFFF);
        int tabY=70,tabW=Math.min(92,Math.max(60,(w-66)/3));
        control(g,mx,my,x,tabY,tabW,22,"目标列表",null,!showingAcl,false,()->{showingAcl=false;listPage=0;init();});
        control(g,mx,my,x+tabW+6,tabY,tabW,22,"目标权限",null,showingAcl&&!scopeAcl,false,()->{scopeAcl=false;refreshTargetAcl();});
        control(g,mx,my,x+2*(tabW+6),tabY,tabW,22,"范围权限",null,showingAcl&&scopeAcl,false,()->{scopeAcl=true;refreshScopeAcl();});
        control(g,mx,my,right-50,tabY,22,22,"",Glyph.RELOAD,false,false,()->{if(!showingAcl)refreshTargets();else if(scopeAcl)refreshScopeAcl();else refreshTargetAcl();});
        control(g,mx,my,right-22,tabY,22,22,"",Glyph.PLUS,false,false,()->{createOpen=true;init();});
        int header=height<400?99:107,rowH=height<400?27:38,listY=header+25;
        RoulettePanelStyle.roundedFill(g,x,header,w,23,3,0x0DFFFFFF);
        String[] headers=showingAcl?new String[]{"Cloud 账号 ID","角色"}:new String[]{"显示名称","类型","目标 ID","权限"};
        double[] columns=showingAcl?new double[]{0,.55}:new double[]{0,.23,.39,.78};
        for(int col=0;col<headers.length;col++)draw(g,headers[col],x+10+(int)(w*columns[col]),header+8,0xFFB1B1B1,(int)(w*.4));
        int visible=Math.max(1,Math.min(3,(formY()-34-listY)/rowH));
        int size=showingAcl?snap.acl().size():snap.targets().size();
        var range=CloudScreenPagination.range(size,visible,listPage);listPage=range.page();
        for(int index=range.startInclusive();index<range.endExclusive();index++){
            int ry=listY+(index-range.startInclusive())*rowH;
            boolean selected=false;String[] values;Runnable choose;
            if(showingAcl){
                var acl=snap.acl().get(index);values=new String[]{acl.accountId(),acl.role()};
                choose=()->{accountId.setValue(acl.accountId());role.setValue(acl.role());setStatus(text("target.selected_acl",acl.accountId()));};
            }else{
                var item=snap.targets().get(index);selected=snap.selectedTarget()!=null&&snap.selectedTarget().targetId().equals(item.targetId());
                values=new String[]{item.displayName(),item.kind(),item.targetId(),"—"};
                choose=()->{try{management.selectTarget(item.targetId());targetId.setValue(item.targetId());setStatus(text("selected",item.displayName()));}catch(RuntimeException failure){setStatus(CloudManagementScreen.errorText(failure));}};
            }
            if(selected||RoulettePanelStyle.inside(mx,my,x,ry,w,rowH-2))RoulettePanelStyle.roundedFill(g,x,ry,w,rowH-2,3,selected?0x13FFFFFF:0x09FFFFFF);
            if(selected)RoulettePanelStyle.fill(g,x,ry+4,2,rowH-10,RoulettePanelStyle.RED);
            for(int col=0;col<values.length;col++){
                int start=x+10+(int)(w*columns[col]),end=col+1<columns.length?x+(int)(w*columns[col+1])-8:right-8;
                draw(g,values[col],start,ry+(rowH-9)/2,col==0?RoulettePanelStyle.TEXT:0xFFB1B1B1,Math.max(1,end-start));
            }
            RoulettePanelStyle.fill(g,x+8,ry+rowH-2,w-16,1,0x0DFFFFFF);
            hits.add(new Hit(x,ry,w,rowH-2,choose));
        }
        if(size==0)draw(g,"暂无记录，点击刷新获取当前范围的数据。",x+10,listY+12,RoulettePanelStyle.MUTED,w-20);
        int fy=formY();
        draw(g,"Cloud 账号 ID",x,fy-17,RoulettePanelStyle.MUTED,(int)(w*.38));
        draw(g,"角色",x+(int)(w*.40),fy-17,RoulettePanelStyle.MUTED,(int)(w*.24));
        control(g,mx,my,x+(int)(w*.68),fy,w-(int)(w*.68),24,scopeAcl&&showingAcl?"设置当前范围权限":"设置所选目标权限",null,false,true,()->{if(scopeAcl&&showingAcl)setScopeAcl();else setTargetAcl();});
        int vehicleY=height<400?fy+36:364;
        control(g,mx,my,x,vehicleY,Math.min(150,w),22,"载具绑定 ›",null,false,false,()->InputUtil.setScreen(new CloudVehicleBindingScreen(this,management)));
        if(height>=400)draw(g,"范围权限与目标权限分开编辑，当前对象始终显示。",x,410,RoulettePanelStyle.MUTED,w);
        if(range.pageCount()>1){
            control(g,mx,my,right-96,vehicleY,22,22,"‹",null,false,false,()->{listPage=Math.max(0,listPage-1);});
            draw(g,(listPage+1)+" / "+range.pageCount(),right-68,vehicleY+7,RoulettePanelStyle.MUTED,42);
            control(g,mx,my,right-22,vehicleY,22,22,"›",null,false,false,()->{listPage=Math.min(range.pageCount()-1,listPage+1);});
        }
        for(Field field:fields)if(field.box.visible&&!createOpen){RoulettePanelStyle.roundedSurface(g,field.x,field.y,field.w,field.h,3,0x0BFFFFFF,field.box.isFocused()?0xCCE05252:0x26FFFFFF);field.box.extractWidgetRenderState(g,mx,my,tick);}
        if(statusMessage!=null)draw(g,statusMessage,x,height-layout.left-16,RoulettePanelStyle.MUTED,w);
        if(createOpen)renderCreate(g,mx,my,tick);
    }
    private void renderCreate(GuiGraphicsExtractor g,int mx,int my,float tick){
        g.fill(0,0,width,height,0x88000000);hits.clear();
        int dw=Math.min(440,layout.contentWidth),dx=width/2-dw/2,dy=height<400?32:86;
        RoulettePanelStyle.secondaryGlassPanel(g,dx,dy,dw,218);
        draw(g,"添加目标",dx+14,dy+14,RoulettePanelStyle.TEXT,dw-28);
        String[] labels={text("target.id"),text("target.kind"),text("target.name")};
        EditBox[] boxes={targetId,targetKind,targetName};
        for(int i=0;i<3;i++){
            draw(g,labels[i],dx+14,dy+26+i*48,RoulettePanelStyle.MUTED,dw-28);
            EditBox box=boxes[i];
            Field field=fields.stream().filter(f->f.box==box).findFirst().orElseThrow();
            RoulettePanelStyle.roundedSurface(g,field.x,field.y,field.w,field.h,3,0x0BFFFFFF,field.box.isFocused()?0xCCE05252:0x26FFFFFF);field.box.extractWidgetRenderState(g,mx,my,tick);
        }
        control(g,mx,my,dx+dw-168,dy+184,70,22,"取消",null,false,false,()->{createOpen=false;init();});
        control(g,mx,my,dx+dw-90,dy+184,76,22,"添加",null,false,true,()->{createOpen=false;createTarget();init();});
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean flag){
        if(com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(e.button())!=0)return super.mouseClicked(e,flag);
        if(!createOpen&&RoulettePanelStyle.applicationRailClick(e.x(),e.y(),width,height,this::navigate))return true;
        for(Hit hit:List.copyOf(hits))if(RoulettePanelStyle.inside(e.x(),e.y(),hit.x,hit.y,hit.w,hit.h)){hit.action.run();return true;}
        return super.mouseClicked(e,flag);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        int header=height<400?99:107,rowH=height<400?27:38,listY=header+25;
        if(!createOpen&&sy!=0&&RoulettePanelStyle.inside(mx,my,layout.contentLeft,listY,layout.contentWidth,Math.max(1,formY()-listY))){
            int visible=Math.max(1,Math.min(3,(formY()-34-listY)/rowH));var snap=management.snapshot();int size=showingAcl?snap.acl().size():snap.targets().size();
            listPage=CloudScreenPagination.range(size,visible,listPage+(sy>0?-1:1)).page();return true;
        }
        return super.mouseScrolled(mx,my,sx,sy);
    }
    private void navigate(int page){switch(page){case 0->InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.MODEL));case 1->InputUtil.setScreen(ModernPlayerModelScreen.resourceStation());case 2->FakePlayerManagerScreen.open();case 3->InputUtil.setScreen(ModernPlayerModelScreen.settings());default->InputUtil.setScreen(null);}}
    private void createTarget() {
        try {
            String scope = management.snapshot().selectedScope().scopeId();
            run(management.createTarget(new CloudScopeClient.CloudTargetCreate(scope,
                    targetId.getValue().isBlank() ? null : targetId.getValue().trim(),
                    targetKind.getValue().trim(), targetName.getValue().trim())), text("target.created"));
        } catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshTargets() {
        try { this.showingAcl = false; this.listPage = 0; run(management.refreshTargets(), text("target.refreshed")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void selectTarget() {
        try { management.selectTarget(targetId.getValue().trim()); setStatus(text("target.selected")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshScopeAcl() {
        try { this.showingAcl = true; this.listPage = 0; run(management.refreshScopeAcl(), text("target.scope_acl_refreshed")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshTargetAcl() {
        try { this.showingAcl = true; this.listPage = 0; run(management.refreshTargetAcl(), text("target.target_acl_refreshed")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void setScopeAcl() {
        try { this.showingAcl = true; run(management.setScopeAcl(new CloudScopeClient.CloudAclUpdate(accountId.getValue().trim(), role.getValue().trim())
                ).thenCompose(ignored -> management.refreshScopeAcl()), text("target.scope_acl_updated")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void setTargetAcl() {
        try { this.showingAcl = true; run(management.setTargetAcl(new CloudScopeClient.CloudAclUpdate(accountId.getValue().trim(), role.getValue().trim())
                ).thenCompose(ignored -> management.refreshTargetAcl()), text("target.target_acl_updated")); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void run(CompletableFuture<?> future, String success) {
        long expectedGeneration = this.lifecycleGeneration;
        setStatus(text("working"));
        future.whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
            if (!this.active || expectedGeneration != this.lifecycleGeneration) return;
            setStatus(failure == null ? success : CloudManagementScreen.errorText(failure));
            if (failure == null) init();
        }));
    }

    @Override
    public void removed() {
        this.active = false;
        this.lifecycleGeneration++;
    }

    private void setStatus(String message) {
        this.statusMessage = message;

    }

    private static String text(String key, Object... args) {
        return CloudManagementScreen.text(key, args);
    }

    @Override
    public void onClose() {
        InputUtil.setScreen(this.parent);
    }
}
