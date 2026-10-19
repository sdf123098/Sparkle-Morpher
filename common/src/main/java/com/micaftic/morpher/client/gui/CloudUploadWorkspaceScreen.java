package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.core.gui.RoulettePanelStyle;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.picker.FilePickerCoordinator;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.util.ClientUiUtil;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.ModelIdUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Unified local model upload workspace, shared by all entry points. */
public class CloudUploadWorkspaceScreen extends Screen implements ModelUploadSession.Listener {
    private static final int TEXT = RoulettePanelStyle.TEXT, MUTED = RoulettePanelStyle.MUTED,
            ACCENT = RoulettePanelStyle.BORDER, ERROR = RoulettePanelStyle.RED;
    private final Screen parent;
    private final Map<String, Path> sources = new LinkedHashMap<>();
    private final Map<String, PickedUpload> localFiles = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, String> states = new HashMap<>();
    private final Map<String, Component> failures = new HashMap<>();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final List<FlatColorButton> settings = new ArrayList<>();
    private final List<FlatColorButton> controls = new ArrayList<>();
    private String instanceId, uploadInstanceId, activeId, activeUploadModelId;
    private ModelUploadSession activeSession, handledSession;
    private CompletableFuture<PreparedUpload> preparation;
    private boolean publicVisibility, uploading, listening, refreshing;
    private UploadTab activeTab = UploadTab.CLOUD_MODELS;
    private long nextPickedFileId;
    private EditBox search;
    private boolean showingFailures;
    private final Map<String,Long> sourceSizes=new java.util.concurrent.ConcurrentHashMap<>();
    private PanelButton primary, failureDetails;
    private int scroll;
    private Component status = Component.empty();

    public CloudUploadWorkspaceScreen(Screen parent, Collection<String> modelIds) {
        super(Component.translatable("gui.sparkle_morpher.cloud_upload.title"));
        this.parent = parent;
        reloadSnapshot();
        for (String id : modelIds) if (sources.containsKey(id)) selected.add(id);
        instanceId = CloudManagementScreen.management().registry().selected()
                .map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
    }

    private void reloadSnapshot() {
        sources.clear();
        ClientModelManager.snapshotLocalCustomSources().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> sources.put(entry.getKey(), entry.getValue()));
        Set<String> knownIds = new HashSet<>(sources.keySet());
        knownIds.addAll(localFiles.keySet());
        selected.retainAll(knownIds);
        states.keySet().retainAll(knownIds);
        failures.keySet().retainAll(knownIds);
        sources.keySet().forEach(id -> states.putIfAbsent(id, "waiting"));
        scroll = 0;
        var snapshot=Map.copyOf(sources);java.util.concurrent.CompletableFuture.runAsync(()->snapshot.forEach((id,path)->{try{if(Files.isRegularFile(path))sourceSizes.put(id,Files.size(path));}catch(IOException ignored){}}),com.micaftic.morpher.util.SmExecutors.pool(com.micaftic.morpher.util.SmExecutors.Pool.MODEL_IO));
    }

    private int panelW() { return Math.max(1,width-panelX()-RoulettePanelStyle.applicationMargin(width)-4); }
    private int panelH() { return Math.max(1,height-2*RoulettePanelStyle.applicationMargin(width)); }
    private int panelX() { return RoulettePanelStyle.applicationContentLeft(width); }
    private int panelY() { return RoulettePanelStyle.applicationMargin(width); }
    private boolean compact() { return panelH() < 300; }
    private int tabY(){return compact()?42:44;}
    private int targetY(){return tabY();}
    private int searchY(){return tabY();}
    private int listShellY(){return compact()?74:104;}
    private int listY(){return compact()?93:128;}
    private int listH(){return Math.max(0,height-panelY()-38-listY());}
    private int rowH(){return compact()?19:30;}
    private int rows() { return Math.max(0, listH() / rowH()); }
    private Component fit(Component text, int width) {
        return font.width(text) <= width ? text : Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(0, width - font.width("…"))) + "…");
    }
    private Component label(String key) { return Component.translatable("gui.sparkle_morpher.cloud_upload." + key); }
    private Component targetLabel() {
        String name = CloudManagementScreen.management().registry().find(instanceId)
                .map(CloudManagementScreen::displayName).orElse("—");
        return Component.translatable("gui.sparkle_morpher.cloud_upload.target", name);
    }
    private Component visibilityLabel() {
        return Component.translatable("gui.sparkle_morpher.import.visibility." + (publicVisibility ? "public" : "private"));
    }
    private FlatColorButton setting(int x, int y, int w, int h, Component label, net.minecraft.client.gui.components.Button.OnPress action) {
        FlatColorButton button = addRenderableWidget(new PanelButton(x, y, w, h, -1, 0, label, action));
        settings.add(button);
        controls.add(button);
        return button;
    }
    private PanelButton iconControl(int x, int y, int u, int v, Component tooltip, Button.OnPress action) {
        PanelButton button = addRenderableWidget(new PanelButton(x, y, compact()?18:22, compact()?18:22, u, v, tooltip, action));
        controls.add(button);
        return button;
    }
    private PanelButton iconSetting(int x, int y, int u, int v, Component tooltip, Button.OnPress action) {
        PanelButton button = iconControl(x, y, u, v, tooltip, action);
        settings.add(button);
        return button;
    }

    @Override
    protected void init(){
        String query=search==null?"":search.getValue();clearWidgets();settings.clear();controls.clear();if(!listening){ModelUploadSession.addListener(this);listening=true;}
        var scene=FinalUiLayout.scene("upload",width,height);var c=scene.control(0);
        iconControl(c.x(),c.y(),64,16,Component.translatable("gui.back"),b->{if(showingFailures){showingFailures=false;init();}else onClose();});
        c=scene.control(1);iconSetting(c.x(),c.y(),0,0,label("tab.cloud_models"),b->{showingFailures=false;setTab(UploadTab.CLOUD_MODELS);}).setSelected(activeTab==UploadTab.CLOUD_MODELS);
        c=scene.control(2);iconSetting(c.x(),c.y(),64,48,label("choose_files"),b->{showingFailures=false;if(activeTab==UploadTab.LOCAL_FILES)openFilePicker();else setTab(UploadTab.LOCAL_FILES);}).setSelected(activeTab==UploadTab.LOCAL_FILES);
        c=scene.control(3);setting(c.x(),c.y(),c.w(),c.h(),fit(targetLabel(),c.w()-12),b->cycleTarget());
        c=scene.control(4);iconSetting(c.x(),c.y(),publicVisibility?96:80,publicVisibility?64:48,visibilityLabel(),b->{publicVisibility=!publicVisibility;states.replaceAll((id,state)->"waiting");failures.clear();status=Component.empty();init();});
        c=scene.control(5);iconSetting(c.x(),c.y(),0,64,label("login"),b->openAccount());
        var sr=scene.control(6);search=new EditBox(font,sr.x()+6,sr.y()+(sr.h()-12)/2,sr.w()-12,12,label("search"));search.setBordered(false);search.setTextColor(TEXT);search.setMaxLength(128);search.setHint(label("search"));search.setValue(query);search.setResponder(v->scroll=0);addRenderableWidget(search);
        c=scene.control(7);iconSetting(c.x(),c.y(),48,16,Component.translatable("gui.sparkle_morpher.upload_custom_folder.refresh"),b->refresh());
        c=scene.control(8);iconSetting(c.x(),c.y(),64,0,Component.translatable("gui.sparkle_morpher.upload_custom_folder.open_folder"),b->{if(activeTab==UploadTab.LOCAL_FILES)openFilePicker();else openFolder();});
        c=scene.control(9);iconSetting(c.x(),c.y(),112,48,label("select_all"),b->{selected.addAll(filtered());updateButtons();});
        if(!compact()){
            c=scene.control(10);iconSetting(c.x(),c.y(),48,32,label("clear_selection"),b->clearSelection());
            c=scene.control(11);failureDetails=iconControl(c.x(),c.y(),96,48,label("failure_details"),b->openFailure(null));
        }
        c=scene.control(compact()?10:12);primary=iconControl(c.x(),c.y(),64,64,label("start"),b->{if(uploading)cancel();else begin();});
        for(int row=0;row<rows();row++){int index=row;addRenderableWidget(new RowHitboxButton(panelX(),listY()+row*rowH(),24,rowH(),b->selectRow(index,false)));addRenderableWidget(new RowHitboxButton(panelX()+24,listY()+row*rowH(),panelW()-24,rowH(),b->selectRow(index,true)));}
        if(showingFailures)iconControl(panelX()+panelW()-22,height-panelY()-34,48,16,label("start"),b->{selected.addAll(failures.keySet());showingFailures=false;begin();});
        updateButtons();
    }

    private void setTab(UploadTab tab) {
        if (uploading || activeTab == tab) return;
        activeTab = tab;
        scroll = 0;
        init();
    }

    private void openFilePicker() {
        status = Component.empty();
        Component error = FilePickerCoordinator.pickYsmFile();
        if (error != null) status = error;
    }

    private void clearSelection() {
        selected.removeAll(activeTab == UploadTab.LOCAL_FILES ? localFiles.keySet() : sources.keySet());
        updateButtons();
    }

    private void removeSelectedFiles() {
        if (uploading) return;
        for (String id : new ArrayList<>(selected)) {
            PickedUpload removed = localFiles.remove(id);
            if (removed != null) {
                removed.cleanUp();
                selected.remove(id);
                states.remove(id);
                failures.remove(id);
            }
        }
        updateButtons();
    }

    private void selectRow(int row, boolean inspectFailure) {
        if (uploading || refreshing) return;
        List<String> visible = filtered();
        int index = scroll + row;
        if (row >= rows() || index >= visible.size()) return;
        String id = visible.get(index);
        if (inspectFailure && failures.containsKey(id)) { openFailure(id); return; }
        if (!selected.add(id)) selected.remove(id);
        else if ("complete".equals(states.get(id))) states.put(id, "waiting");
        updateButtons();
    }

    private List<String> filtered(){
        String query=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);Collection<String> items=showingFailures?failures.keySet():activeTab==UploadTab.LOCAL_FILES?localFiles.keySet():sources.keySet();
        return items.stream().filter(id->query.isEmpty()||rowName(id).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private String rowName(String id) {
        PickedUpload file = localFiles.get(id);
        return file == null ? id : file.fileName();
    }
    private void updateButtons() {
        if (failureDetails != null) failureDetails.active = !uploading && !failures.isEmpty();
        settings.forEach(button -> button.active = !uploading && !refreshing);
        if (search != null) search.setEditable(!uploading);
        if (primary != null) {
            primary.setMessage(label(uploading ? "cancel" : "start"));
            primary.setTooltipLines(List.of(label(uploading ? "cancel" : "start")));
            primary.setIcon(64, uploading ? 32 : 64);
            primary.active = uploading || (!refreshing && filtered().stream().anyMatch(id -> selected.contains(id) && !"complete".equals(states.get(id))));
        }
    }
    private void cycleTarget() {
        var profiles = CloudManagementScreen.management().registry().profiles();
        if (uploading || profiles.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).instanceId().equals(instanceId)) index = i;
        instanceId = profiles.get((index + 1) % profiles.size()).instanceId();
        states.replaceAll((id, state) -> "waiting");
        failures.clear();
        status = Component.empty();
        init();
    }
    private void openAccount() {
        if (parent instanceof ModernPlayerModelScreen panel) {
            InputUtil.setScreen(panel);
            panel.openCloudAccountForUpload(this, instanceId);
        } else CloudManagementScreen.open(this);
    }
    private void refresh() {
        if (uploading || refreshing) return;
        refreshing = true;
        status = Component.translatable("gui.sparkle_morpher.import.state.folder_reloading");
        updateButtons();
        ClientModelManager.reloadLocalModelsForUpload(error -> Minecraft.getInstance().execute(() -> {
            refreshing = false;
            status = error == null ? Component.translatable("gui.sparkle_morpher.import.state.folder_reloaded") : error;
            if (error == null) reloadSnapshot();
            updateButtons();
        }));
    }
    private void openFolder() {
        try {
            Files.createDirectories(ModelStoragePaths.custom());
            ClientUiUtil.openFile(ModelStoragePaths.custom().toFile());
        } catch (IOException error) {
            status = Component.translatable("gui.sparkle_morpher.import.error.open_folder", error.getMessage());
        }
    }
    private void begin() {
        if (uploading || refreshing) return;
        List<String> tabSelection = filtered().stream().filter(selected::contains).toList();
        if (tabSelection.isEmpty()) { status = label("empty_selection"); return; }
        if (CloudClientRuntime.uploadTransport(instanceId) == null) { status = label("unavailable"); return; }
        ModelUploadSession current = ModelUploadSession.getInstance();
        if (current != null && !current.isTerminal()) {
            status = Component.translatable("gui.sparkle_morpher.import.error.in_progress"); return;
        }
        pending.clear();
        for (String id : tabSelection) if (!"complete".equals(states.get(id))) {
            pending.add(id); states.put(id, "waiting"); failures.remove(id);
        }
        uploadInstanceId = instanceId;
        uploading = true;
        updateButtons();
        startNext();
    }
    private void startNext() {
        activeSession = null;
        activeId = pending.poll();
        if (activeId == null) {
            uploading = false;
            long failed = selected.stream().filter(failures::containsKey).count();
            status = failed == 0 ? label("complete") : Component.translatable("gui.sparkle_morpher.cloud_upload.partial_failure", failed);
            updateButtons(); return;
        }
        String id = activeId;
        PickedUpload picked = localFiles.get(id);
        Path source = sources.get(id);
        if (picked != null) {
            activeUploadModelId = modelIdFromFileName(picked.fileName());
            if (activeUploadModelId.isBlank()) {
                fail(Component.translatable("gui.sparkle_morpher.import.error.model_id_from_filename", picked.fileName()));
                return;
            }
        } else {
            activeUploadModelId = id;
        }
        states.put(id, "preparing");
        status = Component.translatable("gui.sparkle_morpher.cloud_upload.preparing", rowName(id));
        preparation = CompletableFuture.supplyAsync(() -> {
            try {
                if (picked != null) {
                    if (!Files.isRegularFile(picked.source())) throw new IOException("Picked upload file is missing");
                    return new PreparedUpload(picked.source(), picked.fileName(), false);
                }
                if (source == null || !Files.exists(source)) throw new IOException("Local model source is missing: " + id);
                if (!Files.isDirectory(source)) return new PreparedUpload(source, source.getFileName().toString(), false);
                Path temp = Files.createTempFile("spm-cloud-folder-", ".zip");
                try {
                    FilePickerCoordinator.packDirectoryTo(source, temp, -1);
                    return new PreparedUpload(temp, id + ".zip", true);
                } catch (IOException | RuntimeException error) { Files.deleteIfExists(temp); throw error; }
            } catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }
    @Override
    public void tick() {
        super.tick();
        FilePickerCoordinator.PickedFile picked;
        boolean added = false;
        while ((picked = FilePickerCoordinator.pollCompleted()) != null) {
            String key = "picked-file-" + (++nextPickedFileId);
            try {
                Path temp = Files.createTempFile("spm-cloud-picked-", ".upload");
                try {
                    Files.write(temp, picked.data());
                    localFiles.put(key, new PickedUpload(temp, picked.fileName(), Files.size(temp)));
                    states.put(key, "waiting");
                    selected.add(key);
                    added = true;
                } catch (IOException | RuntimeException error) {
                    Files.deleteIfExists(temp);
                    status = Component.translatable("gui.sparkle_morpher.import.error.local_storage");
                }
            } catch (IOException error) {
                status = Component.translatable("gui.sparkle_morpher.import.error.local_storage");
            }
        }
        Component pickerError = FilePickerCoordinator.consumeLastError();
        if (!pickerError.getString().isEmpty()) status = pickerError;
        if (added) {
            activeTab = UploadTab.LOCAL_FILES;
            init();
        }
        updateButtons();
        if (!uploading || preparation == null || !preparation.isDone()) return;
        var ready = preparation;
        preparation = null;
        PreparedUpload prepared = null;
        try {
            prepared = ready.join();
            Component error = ModelUploadSession.start(activeUploadModelId, prepared.fileName(), prepared.source(),
                    publicVisibility ? "PUBLIC" : "PRIVATE", uploadInstanceId, prepared.temporary());
            if (error != null) { prepared.cleanUp(); fail(error); }
            else {
                states.put(activeId, "uploading");
                activeSession = ModelUploadSession.getInstance();
                handleSession(activeSession);
            }
        } catch (RuntimeException error) {
            if (prepared != null) prepared.cleanUp();
            fail(Component.literal(CloudManagementScreen.errorText(error)));
        }
    }
    private void fail(Component reason) { states.put(activeId, "failed"); failures.put(activeId, reason); startNext(); }
    private void openFailure(String selectedId){if(!uploading&&!failures.isEmpty()){showingFailures=true;scroll=0;init();}}
    private void cancel() {
        if (!uploading) return;
        uploading = false;
        pending.forEach(id -> states.put(id, "cancelled"));
        pending.clear();
        if (activeId != null) states.put(activeId, "cancelled");
        // supplyAsync cancellation cannot stop IO; clean its eventual result instead.
        if (preparation != null) { preparation.thenAccept(PreparedUpload::cleanUp); preparation = null; }
        if (activeSession != null && ModelUploadSession.getInstance() == activeSession && !activeSession.isTerminal())
            ModelUploadSession.failCurrent(label("cancelled"));
        activeSession = null; activeId = null;
        status = label("cancelled"); updateButtons();
    }
    @Override
    public void onSessionUpdate(ModelUploadSession session) { Minecraft.getInstance().execute(() -> handleSession(session)); }
    private void handleSession(ModelUploadSession session) {
        if (!uploading || session == null || session != activeSession) return;
        if (!session.isTerminal()) { status = session.getMessage(); return; }
        if (handledSession == session) return;
        handledSession = session;
        if (session.getState() == ModelUploadSession.State.FAILED) fail(session.getMessage());
        else { states.put(activeId, "complete"); failures.remove(activeId); ModelUploadSession.clearIfTerminal(); startNext(); }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        int x=panelX(),y=panelY(),w=panelW();RoulettePanelStyle.applicationRail(g,width,height,0);
        g.text(font,title,x,y+10,TEXT,false);String account=CloudManagementScreen.management().accountId(instanceId);g.text(font,fit(Component.literal(targetLabel().getString()+" · "+(account.isBlank()?"尚未登录":account)),w-180),x+Math.min(170,w/3),y+12,MUTED,false);
        var sr=FinalUiLayout.scene("upload",width,height).control(6);RoulettePanelStyle.roundedSurface(g,sr.x(),sr.y(),sr.w(),sr.h(),3,0x0CFFFFFF,0x22FFFFFF);
        g.text(font,fit(Component.literal(showingFailures?"失败详情":activeTab==UploadTab.LOCAL_FILES?"来源：选择的本机文件":"来源：模型文件夹现有模型"),w-12),x+6,compact()?65:80,MUTED,false);
        RoulettePanelStyle.fill(g,x,listShellY(),w,listY()-listShellY(),0x0CFFFFFF);String[] labels=showingFailures?new String[]{"模型","失败原因","源文件"}:new String[]{"选择","模型 / 文件","大小","状态"};double[] cols=showingFailures?new double[]{0,.32,.7}:new double[]{0,.06,.62,.82};
        for(int i=0;i<labels.length;i++)g.text(font,Component.literal(labels[i]),x+6+(int)(w*cols[i]),listShellY()+7,MUTED,false);
        List<String> items=filtered();scroll=Math.max(0,Math.min(scroll,Math.max(0,items.size()-rows())));g.enableScissor(x,listY(),x+w,listY()+listH());List<Component> tooltip=null;
        for(int i=0;i<rows()&&scroll+i<items.size();i++){
            String id=items.get(scroll+i);int yy=listY()+i*rowH();boolean checked=selected.contains(id),hover=RoulettePanelStyle.inside(mx,my,x,yy,w,rowH());
            if(hover||checked)RoulettePanelStyle.fill(g,x,yy,w,rowH()-1,hover?0x18FFFFFF:0x0CFFFFFF);RoulettePanelStyle.fill(g,x,yy+rowH()-1,w,1,0x12FFFFFF);
            if(showingFailures){g.text(font,fit(Component.literal(rowName(id)),(int)(w*.3)),x+6,yy+8,TEXT,false);g.text(font,fit(failures.getOrDefault(id,Component.empty()),(int)(w*.36)),x+(int)(w*.32),yy+8,ERROR,false);Path source=sources.get(id);g.text(font,fit(Component.literal(source==null?rowName(id):source.toString()),(int)(w*.28)),x+(int)(w*.7),yy+8,MUTED,false);}
            else{
                if(checked)RoulettePanelStyle.drawIconScaled(g,RoulettePanelStyle.Glyph.CHECK,x+6,yy+(rowH()-10)/2,10);else RoulettePanelStyle.border(g,x+7,yy+(rowH()-7)/2,7,7,MUTED);
                g.text(font,fit(Component.literal(rowName(id)),(int)(w*.54)),x+(int)(w*.06),yy+(rowH()-9)/2,TEXT,false);PickedUpload picked=localFiles.get(id);long bytes=picked==null?sourceSizes.getOrDefault(id,0L):picked.size();g.text(font,Component.literal(bytes>0?formatBytes(bytes):"—"),x+(int)(w*.62),yy+(rowH()-9)/2,MUTED,false);
                String state=states.getOrDefault(id,"waiting");Component st=id.equals(activeId)&&activeSession!=null&&!activeSession.isTerminal()?Component.literal(Math.round(activeSession.getProgress()*100)+"%"):label("state."+state);g.text(font,fit(st,(int)(w*.17)),x+(int)(w*.82),yy+(rowH()-9)/2,state.equals("failed")?ERROR:MUTED,false);
                if(id.equals(activeId)&&activeSession!=null)RoulettePanelStyle.fill(g,x+6,yy+rowH()-2,(int)((w-12)*activeSession.getProgress()),2,ERROR);
            }
            if(hover){tooltip=new ArrayList<>();tooltip.add(Component.literal(rowName(id)));if(failures.containsKey(id))tooltip.add(failures.get(id));}
        }
        if(items.isEmpty())g.text(font,fit(label("no_results"),w-16),x+8,listY()+12,MUTED,false);g.disableScissor();
        long bytes=selected.stream().mapToLong(id->localFiles.containsKey(id)?localFiles.get(id).size():sourceSizes.getOrDefault(id,0L)).sum();g.text(font,fit(Component.literal("已选 "+selected.size()+" 个 · "+formatBytes(bytes)+"   "+status.getString()),w-36),x,height-panelY()-16,MUTED,false);
        super.extractRenderState(g,mx,my,pt);controls.forEach(b->b.renderTooltip(g,this,mx,my));if(tooltip!=null)g.setComponentTooltipForNextFrame(font,tooltip,mx,my);
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent e,boolean flag){if(com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(e.button())==0&&RoulettePanelStyle.applicationRailClick(e.x(),e.y(),width,height,this::navigateFromRail))return true;return super.mouseClicked(e,flag);}
    @Override
    public boolean keyPressed(KeyEvent event){
        if(event.key()==261&&activeTab==UploadTab.LOCAL_FILES&&!uploading&&(search==null||!search.isFocused())){removeSelectedFiles();return true;}
        return super.keyPressed(event);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelX() + 10 && mx < panelX() + panelW() - 10 && my >= listY() && my < listY() + listH() && scrollY != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - rows()), scroll + (scrollY > 0 ? -1 : 1))); return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
    @Override
    public void removed() {
        cancel();
        FilePickerCoordinator.cancelPicking();
        if (listening) { ModelUploadSession.removeListener(this); listening = false; }
    }
    @Override
    public void onClose() { cancel(); cleanupPickedFiles(); InputUtil.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
    private record PreparedUpload(Path source, String fileName, boolean temporary) {
        void cleanUp() { if (temporary) try { Files.deleteIfExists(source); } catch (IOException ignored) { } }
    }
    private record PickedUpload(Path source, String fileName, long size) {
        void cleanUp() { try { Files.deleteIfExists(source); } catch (IOException ignored) { } }
    }
    private void cleanupPickedFiles() {
        localFiles.values().forEach(PickedUpload::cleanUp);
        localFiles.clear();
    }

    private static String modelIdFromFileName(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        String stem = dot <= 0 ? fileName : fileName.substring(0, dot);
        return ModelIdUtil.normalizeImportModelId(stem);
    }

    private static String formatBytes(long bytes){return bytes<1024?bytes+" B":bytes<1048576?String.format(Locale.ROOT,"%.1f KB",bytes/1024.0):String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0);}

    private enum UploadTab { CLOUD_MODELS, LOCAL_FILES }

    /** Same glass, border and icon treatment as the custom roulette editor. */
    static final class PanelButton extends FlatColorButton {
        private int iconU, iconV;
        private boolean selected;

        PanelButton(int x, int y, int width, int height, int u, int v, Component tooltip, Button.OnPress onPress) {
            super(x, y, width, height, tooltip, onPress);
            setIcon(u, v);
            setTooltipLines(List.of(tooltip));
        }

        void setIcon(int u, int v) { iconU = u; iconV = v; }
        @Override public void setSelected(boolean value) { selected = value; }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            boolean hover = active && isHoveredOrFocused();
            RoulettePanelStyle.roundedFill(g, getX(), getY(), getWidth(), getHeight(), 4, !active ? 0x44282828
                    : hover ? RoulettePanelStyle.PANEL_HOVER : selected ? RoulettePanelStyle.PANEL_ACTIVE : 0x66303030);
            RoulettePanelStyle.roundedBorder(g, getX(), getY(), getWidth(), getHeight(), 4, !active ? 0x22888888
                    : hover ? RoulettePanelStyle.RED : selected ? RoulettePanelStyle.RED_SOFT : 0x33FFFFFF);
            if (iconU >= 0) {
                int iconSize=getHeight()<=18?10:12;int iconX = getX() + (getWidth() - iconSize) / 2, iconY = getY() + (getHeight() - iconSize) / 2;
                g.blit(RoulettePanelStyle.MODEL_PANEL_ICONS, iconX, iconY, iconX + iconSize, iconY + iconSize,
                        iconU / 128.0f, (iconU + 16) / 128.0f, iconV / 128.0f, (iconV + 16) / 128.0f);
            } else if (iconU == -2 || iconU == -3) {
                int direction = iconU == -2 ? -1 : 1;
                int centerX = getX() + getWidth() / 2, centerY = getY() + getHeight() / 2;
                for (int i = 0; i < 5; i++) {
                    int dx = centerX + direction * (2 - i);
                    RoulettePanelStyle.fill(g, dx, centerY - i, 2, 1, active ? RoulettePanelStyle.TEXT : RoulettePanelStyle.MUTED);
                    RoulettePanelStyle.fill(g, dx, centerY + i, 2, 1, active ? RoulettePanelStyle.TEXT : RoulettePanelStyle.MUTED);
                }
            } else {
                var font = Minecraft.getInstance().font;
                Component text = Component.literal(RoulettePanelStyle.trim(font, getMessage().getString(), Math.max(1, getWidth() - 10)));
                RoulettePanelStyle.drawCentered(g, font, text, getX() + getWidth() / 2, com.micaftic.morpher.core.gui.UiElementLayout.centeredY(getY(),getHeight(),font.lineHeight),
                        active ? RoulettePanelStyle.TEXT : RoulettePanelStyle.MUTED);
            }
        }
    }

    private static final class RowHitboxButton extends Button {
        private RowHitboxButton(int x, int y, int width, int height, Button.OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }
        @Override protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) { }
    }
    private void navigateFromRail(int page){
        switch(page){
            case 0 -> InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.MODEL));
            case 1 -> InputUtil.setScreen(ModernPlayerModelScreen.resourceStation());
            case 2 -> FakePlayerManagerScreen.open();
            case 3 -> InputUtil.setScreen(new ModernPlayerModelScreen(ModelPanelState.Tab.SETTINGS));
            default -> onClose();
        }
    }
}
