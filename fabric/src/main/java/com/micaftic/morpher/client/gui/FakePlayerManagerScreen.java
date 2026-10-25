package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.fakeplayer.*;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.core.gui.RoulettePanelStyle.Glyph;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** First-level fake-player manager; selecting its appearance never changes the local player. */
public final class FakePlayerManagerScreen extends Screen {
    private UUID selectedUuid;
    private String selectedName = "";
    private String query = "";
    private int offset, filter;
    private boolean modelSort, moreOpen;
    private EditBox search;
    private Layout layout;
    private String tooltip;
    private final java.util.List<Button> actionButtons=new java.util.ArrayList<>();
    private int refreshTicks;
    private boolean overflowOpen;
    private int overflowScroll;

    /** Logical geometry of handoff scene 53; shared by painting, hit testing and scrolling. */
    record Layout(int x,int y,int w,int toolbarY,int toolbarH,int searchW,int headerY,int listY,int rowH,int footerY,int visibleRows,int visibleActions) {
        static Layout create(int width,int height) {
            ModelPanelLayout shell=ModelPanelLayout.create(width,height);
            boolean compact=width<560;
            int toolbarY=shell.top+32;
            var tools=com.micaftic.morpher.core.gui.ResponsiveUiLayout.toolbar(shell.contentLeft,toolbarY,shell.contentWidth,height,9);
            int toolbarH=tools.size(),searchW=tools.searchWidth();
            int headerY=toolbarY+toolbarH+12,listY=headerY+25,rowH=compact?29:width<800?32:35;
            int footerY=height-shell.left-16;
            return new Layout(shell.contentLeft,shell.top,shell.contentWidth,toolbarY,toolbarH,searchW,headerY,listY,rowH,footerY,Math.max(0,(footerY-14-listY)/rowH),tools.visible());
        }
        int actionX(int index){return x+searchW+6+index*(toolbarH+4);}
        int columnX(int column){return x+(int)Math.round(w*switch(column){case 1->.23;case 2->.58;case 3->.82;default->0;})+10;}
    }

    private FakePlayerManagerScreen(){super(Component.literal("假人外观管理"));}
    public static void open(){
        FakePlayerListCache.replace(List.of());
        InputUtil.setScreen(new FakePlayerManagerScreen());
        com.micaftic.morpher.cloud.client.CloudEntityModelSync.refreshFakeTargets();
    }
    @Override public void tick(){if(++refreshTicks%20==0)com.micaftic.morpher.cloud.client.CloudEntityModelSync.refreshFakeTargets();updateActionButtons();}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        layout=Layout.create(width,height);overflowOpen=false;moreOpen=false;
        clearWidgets();
        search=new EditBox(font,layout.x+8,layout.toolbarY+(layout.toolbarH-10)/2,Math.max(1,layout.searchW-16),10,Component.literal("搜索假人"));
        search.setBordered(false);
        search.setTextColor(RoulettePanelStyle.TEXT);
        search.setMaxLength(128);
        search.setHint(Component.literal(RoulettePanelStyle.trim(font,"搜索名称、UUID、模型或提供者…",Math.max(1,search.getWidth()))));
        search.setValue(query);
        search.setResponder(value->{query=value;offset=0;});
        addRenderableWidget(search);
        actionButtons.clear();
        for(int i=0;i<layout.visibleActions;i++){int action=i;actionButtons.add(addWidget(new HitButton(layout.actionX(i),layout.toolbarY,layout.toolbarH,layout.toolbarH,Component.literal(actionLabel(i)),()->activateAction(action))));}
        if(layout.visibleActions<9)addWidget(new HitButton(layout.actionX(layout.visibleActions),layout.toolbarY,layout.toolbarH,layout.toolbarH,Component.literal("更多工具"),()->{overflowOpen=!overflowOpen;moreOpen=false;overflowScroll=0;}));
        int p=RoulettePanelStyle.applicationMargin(width),side=width<560?28:width<800?30:36,top=p+(width<560?20:width<800?22:26)+17;
        for(int i=0;i<4;i++){int page=i;addWidget(new HitButton(p,top+i*com.micaftic.morpher.core.gui.ResponsiveUiLayout.railStride(height,top,p+32),side,Math.min(24,com.micaftic.morpher.core.gui.ResponsiveUiLayout.railStride(height,top,p+32)),Component.literal(new String[]{"模型管理","资源站","假人外观管理","设置"}[i]),()->navigateFromRail(page)));}
        addWidget(new HitButton(p,height-p-28,side,23,Component.translatable("gui.back"),this::onClose));
        for(int i=0;i<layout.visibleRows;i++){int row=i;addWidget(new HitButton(layout.x,layout.listY+i*layout.rowH,layout.w,layout.rowH,Component.literal("选择假人"),()->selectRow(row)));}
        updateActionButtons();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        RoulettePanelStyle.applicationRail(g,width,height,2);
        tooltip=null;
        draw(g,"假人外观管理",layout.x,layout.y+10,RoulettePanelStyle.TEXT);
        RoulettePanelStyle.roundedSurface(g,layout.x,layout.toolbarY,layout.searchW,layout.toolbarH,3,0x0BFFFFFF,search.isFocused()?0xCCE05252:0x26FFFFFF);
        search.extractWidgetRenderState(g,mouseX,mouseY,partialTick);
        Glyph[] actions={Glyph.RESOURCE,Glyph.EYE,Glyph.MODEL,Glyph.GLOBE,Glyph.FILTER,Glyph.RELOAD,Glyph.MODEL,Glyph.UNPLUG,Glyph.MORE};
        String[] labels={"全部假人","在线假人","已配置模型","按维度筛选","假人排序","刷新假人","为所选假人选择模型","解除外观绑定","假人更多操作"};
        boolean selection=selectedEntry()!=null;
        for(int i=0;i<layout.visibleActions;i++){
            int ax=layout.actionX(i),size=layout.toolbarH;
            boolean enabled=i!=6&&i!=7||selection;
            boolean hover=enabled&&inside(mouseX,mouseY,ax,layout.toolbarY,size,size);
            boolean active=i<4&&filter==i;
            RoulettePanelStyle.roundedSurface(g,ax,layout.toolbarY,size,size,3,active?0x21FFFFFF:hover?0x18FFFFFF:0x0BFFFFFF,active?0xCCE05252:0x22FFFFFF);
            RoulettePanelStyle.drawIconScaled(g,actions[i],ax+(size-14)/2,layout.toolbarY+(size-14)/2,14);
            if(!enabled)RoulettePanelStyle.roundedFill(g,ax,layout.toolbarY,size,size,3,0x99000000);
            if(hover)tooltip=labels[i];
        }
        if(layout.visibleActions<9){int ax=layout.actionX(layout.visibleActions),size=layout.toolbarH;
            RoulettePanelStyle.roundedSurface(g,ax,layout.toolbarY,size,size,3,0x0BFFFFFF,0x22FFFFFF);
            RoulettePanelStyle.drawIconScaled(g,Glyph.MORE,ax+(size-14)/2,layout.toolbarY+(size-14)/2,14);
            if(inside(mouseX,mouseY,ax,layout.toolbarY,size,size))tooltip="更多工具";
        }
        RoulettePanelStyle.roundedFill(g,layout.x,layout.headerY,layout.w,23,3,0x0DFFFFFF);
        String[] headers={"假人","模型 / 提供者","维度","状态"};
        for(int i=0;i<headers.length;i++)draw(g,headers[i],layout.columnX(i),layout.headerY+8,0xFFB1B1B1);
        List<FakePlayerListEntry> rows=rows();
        clampOffset(rows.size());
        for(int i=0;i<layout.visibleRows&&offset+i<rows.size();i++){
            var entry=rows.get(offset+i);
            int ry=layout.listY+i*layout.rowH;
            boolean selected=entry.uuid().equals(selectedUuid),hover=inside(mouseX,mouseY,layout.x,ry,layout.w,layout.rowH-2);
            if(selected||hover)RoulettePanelStyle.roundedFill(g,layout.x,ry,layout.w,layout.rowH-2,3,selected?0x13FFFFFF:0x09FFFFFF);
            if(selected)RoulettePanelStyle.fill(g,layout.x,ry+4,2,layout.rowH-10,RoulettePanelStyle.RED);
            String[] values={displayName(entry),(entry.modelId().isBlank()?"未设置模型":entry.modelId())+" · "+entry.providerId(),entry.dimensionId(),entry.online()?"在线":"离线"};
            for(int col=0;col<values.length;col++){
                int cx=layout.columnX(col),end=col==3?layout.x+layout.w-10:layout.columnX(col+1)-12;
                draw(g,RoulettePanelStyle.trim(font,values[col],Math.max(1,end-cx)),cx,ry+(layout.rowH-9)/2,col==0?RoulettePanelStyle.TEXT:0xFFB1B1B1);
            }
            RoulettePanelStyle.fill(g,layout.x+8,ry+layout.rowH-2,layout.w-16,1,0x0DFFFFFF);
        }
        if(rows.isEmpty())draw(g,RoulettePanelStyle.trim(font,FakePlayerListCache.entries().isEmpty()?Component.translatable("gui.sparkle_morpher.cloud.entity.fake_empty").getString():"没有匹配的假人。清空搜索或调整筛选。",layout.w-20),layout.x+10,layout.listY+13,RoulettePanelStyle.MUTED);
        draw(g,rows.size()+" 个假人"+(selection?" · 已选择："+selectedName:""),layout.x,layout.footerY,0xFFB1B1B1);
        if(moreOpen)renderMore(g);
        if(overflowOpen)renderOverflow(g);
        if(tooltip!=null){
            int tw=Math.min(width-12,font.width(tooltip)+12),tx=Math.min(mouseX+8,width-tw-6),ty=Math.min(mouseY+13,height-22);
            RoulettePanelStyle.roundedSurface(g,tx,ty,tw,18,3,0xF0171717,0x44FFFFFF);
            draw(g,RoulettePanelStyle.trim(font,tooltip,tw-10),tx+5,ty+5,RoulettePanelStyle.TEXT);
        }
    }
    private com.micaftic.morpher.core.gui.ResponsiveUiLayout.Menu overflowMenu(){return com.micaftic.morpher.core.gui.ResponsiveUiLayout.menu(layout.x+layout.w-180,layout.toolbarY+layout.toolbarH+4,180,width,height,9-layout.visibleActions,overflowScroll);}
    private void renderOverflow(GuiGraphicsExtractor g){
        var menu=overflowMenu();overflowScroll=menu.first();var box=menu.bounds();
        RoulettePanelStyle.roundedSurface(g,box.x(),box.y(),box.width(),box.height(),4,0xFF242424,0x55FFFFFF);
        for(int i=0;i<menu.visible();i++){int action=layout.visibleActions+menu.first()+i;var row=menu.row(i);boolean enabled=action!=6&&action!=7||selectedEntry()!=null;draw(g,RoulettePanelStyle.trim(font,actionLabel(action),row.width()-12),row.x()+6,row.y()+6,enabled?RoulettePanelStyle.TEXT:RoulettePanelStyle.MUTED);}
        if(menu.maxScroll()>0){draw(g,"‹",box.x()+8,box.bottom()-19,RoulettePanelStyle.TEXT);draw(g,"›",box.right()-16,box.bottom()-19,RoulettePanelStyle.TEXT);}
    }
    private boolean overflowClick(double mx,double my){
        var menu=overflowMenu();var box=menu.bounds();
        if(menu.maxScroll()>0&&inside(mx,my,box.x(),box.bottom()-24,box.width(),24)){overflowScroll=Math.max(0,Math.min(menu.maxScroll(),menu.first()+(mx<box.x()+box.width()/2?-menu.visible():menu.visible())));return true;}
        for(int i=0;i<menu.visible();i++){var row=menu.row(i);if(row.contains(mx,my)){int action=layout.visibleActions+menu.first()+i;overflowOpen=false;if(action!=6&&action!=7||selectedEntry()!=null)activateAction(action);return true;}}
        overflowOpen=false;return true;
    }
    private void renderMore(GuiGraphicsExtractor g){
        int x=Math.min(layout.actionX(8),width-154),y=layout.toolbarY+layout.toolbarH+4;
        RoulettePanelStyle.roundedSurface(g,x,y,148,48,4,0xF0171717,0x44FFFFFF);
        draw(g,"复制所选假人 UUID",x+8,y+8,selectedEntry()==null?RoulettePanelStyle.MUTED:RoulettePanelStyle.TEXT);
        draw(g,"清空搜索与筛选",x+8,y+29,RoulettePanelStyle.TEXT);
    }
    private List<FakePlayerListEntry> rows(){
        String q=query.toLowerCase(Locale.ROOT).trim();
        return FakePlayerListCache.entries().stream().filter(e->filter!=1||e.online()).filter(e->filter!=2||!e.modelId().isBlank()).filter(e->filter!=3||!e.dimensionId().isBlank())
                .filter(e->q.isBlank()||match(e.name(),q)||match(e.displayName(),q)||match(e.uuid().toString(),q)||match(e.modelId(),q)||match(e.providerId(),q))
                .sorted(modelSort?Comparator.comparing(FakePlayerListEntry::modelId,String.CASE_INSENSITIVE_ORDER).thenComparing(FakePlayerListEntry::name,String.CASE_INSENSITIVE_ORDER):Comparator.comparing(FakePlayerListEntry::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private void clampOffset(int count){offset=Math.max(0,Math.min(offset,Math.max(0,count-layout.visibleRows)));}
    private FakePlayerListEntry selectedEntry(){return FakePlayerListCache.entries().stream().filter(e->e.uuid().equals(selectedUuid)).findFirst().orElse(null);}
    private static String displayName(FakePlayerListEntry e){return e.displayName().isBlank()?e.name():e.displayName();}
    private static boolean match(String value,String q){return value!=null&&value.toLowerCase(Locale.ROOT).contains(q);}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return RoulettePanelStyle.inside(mx,my,x,y,w,h);}
    private void draw(GuiGraphicsExtractor g,String text,int x,int y,int color){g.text(font,Component.literal(text),x,y,color,false);}

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean flag){
        if(com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button())!=0)return super.mouseClicked(event,flag);
        if(overflowOpen)return overflowClick(event.x(),event.y());
        if(moreOpen){
            int x=Math.min(layout.actionX(8),width-154),y=layout.toolbarY+layout.toolbarH+4;
            if(inside(event.x(),event.y(),x,y,148,48)){
                if(event.y()<y+24){if(selectedEntry()!=null)minecraft.keyboardHandler.setClipboard(selectedUuid.toString());}
                else{search.setValue("");filter=0;offset=0;}
                moreOpen=false;return true;
            }
            moreOpen=false;
        }
        return super.mouseClicked(event,flag);
    }
    private String actionLabel(int i){return new String[]{"全部假人","在线假人","已配置模型","按维度筛选","假人排序","刷新假人","为所选假人选择模型","解除外观绑定","假人更多操作"}[i];}
    private void updateActionButtons(){boolean selected=selectedEntry()!=null;for(int i=0;i<actionButtons.size();i++)actionButtons.get(i).active=i!=6&&i!=7||selected;}
    private void selectRow(int row){var rows=rows();clampOffset(rows.size());int index=offset+row;if(index<rows.size()){var entry=rows.get(index);selectedUuid=entry.uuid();selectedName=displayName(entry);search.setFocused(false);updateActionButtons();}}
    private void activateAction(int action){
        if(action<4){filter=action;offset=0;return;}
        switch(action){case 4->{modelSort=!modelSort;offset=0;}case 5->com.micaftic.morpher.cloud.client.CloudEntityModelSync.refreshFakeTargets();case 6->openModelSelection();case 7->{if(selectedEntry()!=null)com.micaftic.morpher.cloud.client.CloudEntityModelSync.applySelection(com.micaftic.morpher.cloud.client.CloudEntityProvider.Kind.FAKE_PLAYER,selectedUuid,selectedName,"default","default");}case 8->moreOpen=!moreOpen;}
    }
    private static final class HitButton extends Button {
        HitButton(int x,int y,int w,int h,Component title,Runnable action){super(x,y,w,h,title,b->action.run(),DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float pt){}
    }
    private void openModelSelection(){
        if(selectedEntry()==null)return;
        UUID target=selectedUuid;String targetName=selectedName;
        InputUtil.setScreen(new ModernPlayerModelScreen(this,(modelId,textureId)->com.micaftic.morpher.cloud.client.CloudEntityModelSync.applySelection(com.micaftic.morpher.cloud.client.CloudEntityProvider.Kind.FAKE_PLAYER,target,targetName,modelId,textureId),"fake:"+target));
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        if(overflowOpen&&sy!=0){overflowScroll=Math.max(0,overflowScroll+(sy>0?-1:1));return true;}
        if(sy!=0&&inside(mx,my,layout.x,layout.listY,layout.w,Math.max(0,layout.footerY-layout.listY))){offset-=(int)Math.signum(sy);clampOffset(rows().size());return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if((event.key()==257||event.key()==335)&&!search.isFocused()&&!(getFocused() instanceof HitButton)){openModelSelection();return true;}
        if(event.key()==256&&(moreOpen||overflowOpen)){moreOpen=false;overflowOpen=false;return true;}
        return super.keyPressed(event);
    }
    private void navigateFromRail(int page){
        switch(page){
            case 0->InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.MODEL));
            case 1->InputUtil.setScreen(ModernPlayerModelScreen.resourceStation());
            case 2->{}
            case 3->InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.SETTINGS));
            default->onClose();
        }
    }
}
