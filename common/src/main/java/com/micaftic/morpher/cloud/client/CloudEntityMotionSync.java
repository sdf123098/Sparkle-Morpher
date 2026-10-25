package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.RuntimeModelLease;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.model.CloudAssetIdentity;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Independent entity timeline adapter. It reads explicit bindings and mutates display capabilities only. */
public final class CloudEntityMotionSync {
    private static final CloudEntityMotionLedger LEDGER=new CloudEntityMotionLedger();
    private record Render(LivingAnimatable<?> cap,String model,CloudPlayerMotion motion) {}
    private static final Map<UUID,Render> RENDER=new ConcurrentHashMap<>();
    private static final Map<UUID,Owner> OWN=new ConcurrentHashMap<>();
    private static final Map<UUID,Applied> APPLIED=new HashMap<>();
    private static final Map<UUID,FakePlayerRouletteRestoreGate> RESTORES=new HashMap<>();
    private static FakePlayerRouletteProfileStore preferences;
    private record RestoreToken(LivingAnimatable<?> cap, CloudEntityMotionLedger.Observation observed,
                                FakePlayerRouletteProfileStore.Key key) {}
    private static final Map<CloudPlayerSelection,CompletableFuture<String>> IMPORTS=new HashMap<>();
    private static final Map<CloudPlayerSelection,Long> RETRIES=new HashMap<>();
    private static final class Owner {
        final LivingAnimatable<?> cap;final CloudPlayerMotionState state=new CloudPlayerMotionState();
        long revision=-1,nextPublish,lastPublish;boolean publishing;CloudPlayerMotion published;
        Owner(LivingAnimatable<?> cap){this.cap=cap;}
    }
    private static final class Applied {
        final LivingAnimatable<?> cap;final String model;RuntimeModelLease lease;String action;Map<String,Float> variables;
        final Set<String> expressions=new LinkedHashSet<>();
        Applied(LivingAnimatable<?> cap){this.cap=cap;model=cap.getModelId();lease=ClientModelManager.acquireRuntimeModelLease(model);}
    }
    private static CloudClientRuntime.RuntimeState runtime;
    private static EntityDisplayContext context;
    private static Object level,connection;
    private static long generation,scopeGeneration,nextPoll;
    private static boolean polling;
    private CloudEntityMotionSync() {}
    private static LivingAnimatable<?> capability(Entity entity){
        var maid=MaidCapability.get(entity).orElse(null);if(maid!=null)return maid;
        return entity instanceof Player?PlayerCapability.get(entity).orElse(null):null;
    }
    private static CloudEntityMotionLedger.Observation observation(LivingAnimatable<?> cap){
        if(context==null||cap==null)return null;
        if(cap instanceof MaidCapability maid && maid.hasLocalState())return null;
        Entity entity=cap.getEntity();if(entity==Minecraft.getInstance().player||entity.isRemoved())return null;
        String kind=cap instanceof MaidCapability?"MAID":"FAKE_PLAYER";
        if("FAKE_PLAYER".equals(kind)&&CloudPlayerModelSync.ownsAppearance(entity.getUUID()))return null;
        var resolution=runtime.bindingResolver().resolve(context.scopeId(),context.worldEpoch(),entity.getUUID(),kind);
        if(resolution.status()!=CloudEntityBindingResolver.Status.BOUND)return null;
        var binding=resolution.binding();var appearance=runtime.appearances().get(context.scopeId(),binding.targetId());
        if(appearance==null||appearance.disabled()||appearance.assetId()==null||appearance.assetRevision()==null||appearance.rawSha256()==null)return null;
        String model=new CloudAssetIdentity(context.instanceId(),"catalog",appearance.assetId(),Long.toString(appearance.assetRevision()),appearance.rawSha256()).runtimeModelId();
        return new CloudEntityMotionLedger.Observation(entity.getUUID(),kind,binding.targetId(),binding.revision(),appearance.revision(),model);
    }
    private static EntityDisplayContext location(Minecraft client,CloudClientRuntime.RuntimeState next){
        if(client.level==null||next==null||next.instanceInfo()==null||!next.instanceInfo().supports("entity_motion_v1")||(!com.micaftic.morpher.core.network.NetworkAccess.allowsCloud() || PrivacyMode.isActive()))return null;
        String scope=next.scopeLifecycle().activeScopeId(),epoch=next.scopeLifecycle().activeWorldEpoch();if(scope==null||epoch==null)return null;
        return new EntityDisplayContext(next.instance().instanceId(),CloudAssetCache.sha256(next.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),scope,epoch,client.level.dimension().location().toString(),generation);
    }
    private static boolean current(EntityDisplayContext expected,CloudClientRuntime.RuntimeState session){
        var client=Minecraft.getInstance();return (com.micaftic.morpher.core.network.NetworkAccess.allowsCloud() && !PrivacyMode.isActive())&&context!=null&&context.equals(expected)&&runtime==session&&CloudClientRuntime.state()==session
            &&client.level==level&&client.getConnection()==connection&&session.scopeLifecycle().contextGeneration()==scopeGeneration;
    }
    private static void clear(LivingAnimatable<?> cap){
        cap.setForceDisabled(true);clearTimeline(cap);
    }
    private static void clearTimeline(LivingAnimatable<?> cap){
        cap.awaitAsyncResult();
        if(cap instanceof MaidCapability maid)maid.clearDisplayMotion();
        else if(cap instanceof PlayerCapability player){player.awaitAsyncResult();player.clearModelSwitch();if(player.isModelReady())player.updateMolangVars(player.getModelAssembly().getModelData().getHashId(),new Int2FloatOpenHashMap());}
    }
    private static void reset(){
        if(runtime!=null&&context!=null&&!OWN.isEmpty()){
            var api=new CloudEntityMotionClient(runtime.http(),runtime.instanceInfo());
            OWN.forEach((id,owner)->{if(owner.revision>0)api.withdraw(context,id,owner.revision).exceptionally(error->{YesSteveModel.LOGGER.debug("[SPM entity motion] withdrawal failed",error);return null;});});
        }
        APPLIED.values().forEach(applied->{if(applied.lease!=null)applied.lease.close();clear(applied.cap);});APPLIED.clear();RENDER.clear();OWN.clear();IMPORTS.clear();RETRIES.clear();polling=false;nextPoll=0;generation++;
        LEDGER.reset(null);RESTORES.clear();
    }
    public static void tick(){
        var client=Minecraft.getInstance();var next=CloudClientRuntime.state();long nextScope=next==null?0:next.scopeLifecycle().contextGeneration();
        if(next!=runtime||client.level!=level||client.getConnection()!=connection||nextScope!=scopeGeneration){reset();runtime=next;level=client.level;connection=client.getConnection();scopeGeneration=nextScope;context=null;}
        var nextContext=location(client,next);if(!Objects.equals(context,nextContext)){reset();context=location(client,next);LEDGER.reset(context);}
        if(context==null||client.level==null)return;
        Map<UUID,LivingAnimatable<?>> visible=new HashMap<>();
        for(Entity entity:client.level.entitiesForRendering())if(visible.size()<512){var cap=capability(entity);if(observation(cap)!=null)visible.put(entity.getUUID(),cap);}
        LEDGER.retain(visible.keySet());RESTORES.keySet().retainAll(visible.keySet());
        Set<CloudPlayerSelection> selections=new HashSet<>();
        visible.values().forEach(cap->{var entry=LEDGER.get(context,observation(cap),System.nanoTime());if(entry!=null)selections.add(entry.selection());});
        RETRIES.keySet().retainAll(selections);
        for(UUID id:List.copyOf(APPLIED.keySet()))if(!visible.containsKey(id)){var old=retire(id);if(old!=null)clear(old.cap);RENDER.remove(id);}
        for(UUID id:List.copyOf(OWN.keySet()))if(!visible.containsKey(id)){Owner old=OWN.remove(id);if(old.revision>0)new CloudEntityMotionClient(runtime.http(),runtime.instanceInfo()).withdraw(context,id,old.revision).exceptionally(error->null);}
        long now=System.nanoTime();if(!polling&&now-nextPoll>=0&&!visible.isEmpty())poll(visible,now);
        for(var entry:visible.entrySet()){
            UUID id=entry.getKey();var cap=entry.getValue();Owner owner=OWN.get(id);
            if(owner!=null&&owner.cap!=cap){OWN.remove(id);owner=null;}
            var observed=observation(cap);var received=LEDGER.get(context,observed,now);
            if(received!=null&&!ready(cap,received.selection())){
                var old=retire(id);
                if(old!=null)clear(old.cap);
                RENDER.remove(id);materialize(id,cap,received,observed);continue;
            }
            if(owner!=null&&(!cap.isModelReady()||!observed.runtimeModelId().equals(cap.getModelId()))){OWN.remove(id);owner=null;}
            if(owner==null&&received==null)restorePreferences(cap,observed,now);
            if(owner!=null){cap.awaitAsyncResult();try{owner.state.roaming(variables(cap));}catch(IllegalArgumentException error){YesSteveModel.LOGGER.debug("[SPM entity motion] variables exceed limits",error);}}
            CloudPlayerMotion motion=owner!=null?owner.state.snapshot():received==null?null:received.update().motion();
            if(owner!=null&&!owner.publishing&&now-owner.lastPublish>=250_000_000L
                &&(now-owner.nextPublish>=0||!motion.equals(owner.published)))publish(id,owner,observed,now);
            if(motion==null){var old=retire(id);if(old!=null)clearTimeline(old.cap);RENDER.remove(id);continue;}
            RENDER.put(id,new Render(cap,cap.getModelId(),motion));if(owner==null)apply(id,cap,motion);
        }
    }
    private static void poll(Map<UUID,LivingAnimatable<?>> visible,long now){
        polling=true;nextPoll=now+1_000_000_000L;final var expected=context;final var session=runtime;var api=new CloudEntityMotionClient(session.http(),session.instanceInfo());
        var ids=List.copyOf(visible.keySet());List<CompletableFuture<Map<UUID,CloudEntityMotionClient.Entry>>> requests=new ArrayList<>();int batch=CloudVisualQueryBudget.batchLimit(expected,session.instanceInfo());
        for(int i=0;i<ids.size();i+=batch)requests.add(api.query(expected,ids.subList(i,Math.min(i+batch,ids.size()))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored,error)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session))return;polling=false;if(error!=null){nextPoll=System.nanoTime()+5_000_000_000L;
                Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof CloudHttpException denied&&(denied.statusCode()==401||denied.statusCode()==403)){LEDGER.reset(context);APPLIED.values().forEach(applied->clear(applied.cap));APPLIED.clear();RENDER.clear();OWN.clear();}
                return;}
            Map<UUID,CloudEntityMotionClient.Entry> found=new HashMap<>();requests.forEach(request->found.putAll(request.join()));long receipt=System.nanoTime();
            for(UUID id:ids){var entry=found.get(id);if(entry==null){LEDGER.remove(id);continue;}
                var cap=visible.get(id);if(!loaded(cap)||!appearanceMatches(entry))continue;
                LEDGER.receive(expected,observation(cap),entry,receipt);}
        }));
    }
    private static void publish(UUID id,Owner owner,CloudEntityMotionLedger.Observation observed,long now){
        if(observed==null)return;var snapshot=owner.state.snapshot();if(snapshot.equals(owner.published)){owner.nextPublish=now+20_000_000_000L;}else owner.nextPublish=now+250_000_000L;
        owner.publishing=true;owner.lastPublish=now;final var expected=context;final var session=runtime;var api=new CloudEntityMotionClient(session.http(),session.instanceInfo());
        var revision=owner.revision<0?api.revisions(expected,List.of(id)).thenApply(rows->{if(!rows.containsKey(id))throw new CompletionException(new IllegalStateException("Entity edit permission required"));return rows.get(id);}):CompletableFuture.completedFuture(owner.revision);
        revision.thenCompose(value->CloudEntityPublicationGuard.run(Minecraft.getInstance()::execute,()->current(expected,session)&&OWN.get(id)==owner&&Objects.equals(observed,observation(owner.cap)),()->{
            var motion=new CloudEntityMotion(expected.worldEpoch(),expected.dimensionId(),observed.entityKind(),observed.targetId(),observed.bindingRevision(),observed.appearanceRevision(),snapshot);return api.publish(expected,id,motion,value);
        })).whenComplete((entry,error)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session)||OWN.get(id)!=owner){
                if(error==null&&entry!=null)api.withdraw(expected,id,entry.revision()).exceptionally(failure->null);
                return;
            }owner.publishing=false;
            if(error!=null){OWN.remove(id,owner);owner.nextPublish=System.nanoTime()+5_000_000_000L;YesSteveModel.LOGGER.warn("[SPM entity motion] publication rejected: {}",error.toString());
                var player=Minecraft.getInstance().player;if(player!=null)player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.sparkle_morpher.cloud.entity_motion_failed"));
                nextPoll=0;return;}
            owner.revision=entry.revision();owner.published=snapshot;if(!snapshot.equals(owner.state.snapshot()))owner.nextPublish=0;
        }));
    }
    private static Applied retire(UUID id){
        Applied old=APPLIED.remove(id);if(old!=null&&old.lease!=null)old.lease.close();return old;
    }
    private static void apply(UUID id,LivingAnimatable<?> cap,CloudPlayerMotion motion){
        Applied state=APPLIED.get(id);
        if(state!=null&&(state.cap!=cap||!state.model.equals(cap.getModelId()))){retire(id);state=null;}
        if(state==null){state=new Applied(cap);APPLIED.put(id,state);}
        if(cap instanceof MaidCapability maid)maid.applyDisplayMotion(motion.animationKey(),motion.roaming());
        else if(cap instanceof PlayerCapability player){player.awaitAsyncResult();
            if(!motion.roaming().equals(state.variables)){var values=new Int2FloatOpenHashMap();motion.roaming().forEach((key,value)->values.put(StringPool.computeIfAbsent(key),value.floatValue()));player.updateMolangVars(player.getModelAssembly().getModelData().getHashId(),values);state.variables=motion.roaming();}
            if(!motion.eventId().equals(state.action)){if(motion.animationKey().isEmpty())player.clearModelSwitch();else player.requestModelSwitch(motion.animationKey());}}
        state.action=motion.eventId();long now=System.currentTimeMillis();
        for(var expression:motion.expressions()){if(!state.expressions.add(expression.eventId())||now-expression.startedAtUnixMs()>30000||expression.startedAtUnixMs()-now>60000)continue;
            try{if(expression.expression().isEmpty()&&cap instanceof PlayerCapability player)player.executeAnimationExpression(new it.unimi.dsi.fastutil.floats.FloatArrayList(expression.values()));
                else if(!expression.expression().isEmpty())cap.executeExpression(GeckoLibCache.parseSimpleExpression(expression.expression()),true,false,null);
            }catch(Exception error){YesSteveModel.LOGGER.debug("[SPM entity motion] expression ignored",error);}}
        while(state.expressions.size()>128)state.expressions.remove(state.expressions.iterator().next());
    }
    public static boolean play(AnimatableEntity<?> entity,String key){
        if(!(entity instanceof LivingAnimatable<?> cap)||entity.getEntity()==Minecraft.getInstance().player)return false;
        if(!entity.getEntity().level().isClientSide()||!cap.isModelReady())return false;
        tick();
        cap.awaitAsyncResult();var values=variables(cap);
        if(cap instanceof MaidCapability maid){key=resolveMaidAction(maid,key);if(key==null)return true;maid.applyDisplayMotion(key,values);}
        else if(cap instanceof PlayerCapability player){if(key.isEmpty())player.clearModelSwitch();else{player.requestModelSwitch(key);key=player.isModelSwitching()?player.getSelectedModelId():"";}}else return false;
        if(context!=null&&observation(cap)!=null&&(OWN.size()<128||OWN.containsKey(entity.getEntity().getUUID()))){Owner owner=OWN.compute(entity.getEntity().getUUID(),(id,old)->old!=null&&old.cap==cap?old:new Owner(cap));owner.state.roaming(values);owner.state.play(key,System.currentTimeMillis());owner.nextPublish=0;retire(entity.getEntity().getUUID());
            RENDER.put(entity.getEntity().getUUID(),new Render(cap,cap.getModelId(),owner.state.snapshot()));}
        rememberAction(cap,key,values);
        return true;
    }
    private static FakePlayerRouletteProfileStore preferences() {
        if(preferences==null)preferences=new FakePlayerRouletteProfileStore(
            com.micaftic.morpher.core.storage.ModelStoragePaths.folder().resolve("fake_player_roulette.json"));
        return preferences;
    }
    private static FakePlayerRouletteProfileStore.Key preferenceKey(LivingAnimatable<?> cap) {
        if(!(cap instanceof PlayerCapability)||context==null||cap.getEntity()==Minecraft.getInstance().player)return null;
        var observed=observation(cap);
        if(observed==null||!"FAKE_PLAYER".equals(observed.entityKind())||!observed.runtimeModelId().equals(cap.getModelId()))return null;
        try {
            String account=com.micaftic.morpher.client.gui.CloudManagementScreen.offlineScope(runtime).accountId();
            return new FakePlayerRouletteProfileStore.Key(context.instanceId(),context.originSha256(),account,
                context.scopeId(),context.worldEpoch(),cap.getEntity().getUUID(),observed.targetId(),cap.getModelId());
        } catch(IllegalArgumentException missingAccount){return null;}
    }
    private static void preferenceFailure(Exception error) {
        YesSteveModel.LOGGER.warn("[SPM fake roulette] preferences could not be saved/restored",error);
        var player=Minecraft.getInstance().player;
        if(player!=null)player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
            "gui.sparkle_morpher.model_panel.setting.local_save_failed"));
    }
    public static void rememberSetting(AnimatableEntity<?> entity,String control,String expression) {
        if(!(entity instanceof LivingAnimatable<?> cap))return;
        var key=preferenceKey(cap);if(key==null)return;
        try {
            cap.awaitAsyncResult();var previous=preferences().load(key);
            if(previous==null)previous=FakePlayerRouletteProfileStore.Profile.empty();
            preferences().save(key,previous.withSetting(control,expression,variables(cap)));
        } catch(Exception error){preferenceFailure(error);}
    }
    private static void rememberAction(LivingAnimatable<?> cap,String action,Map<String,Float> values) {
        var key=preferenceKey(cap);if(key==null)return;
        try {
            var previous=preferences().load(key);
            if(previous==null)previous=FakePlayerRouletteProfileStore.Profile.empty();
            preferences().save(key,previous.withAction(action,values));
        } catch(Exception error){preferenceFailure(error);}
    }
    private static void restorePreferences(LivingAnimatable<?> cap,CloudEntityMotionLedger.Observation observed,long now) {
        if(!cap.isModelReady()||cap.isForceDisabled()||OWN.size()>=128)return;
        var key=preferenceKey(cap);if(key==null)return;
        UUID id=cap.getEntity().getUUID();var token=new RestoreToken(cap,observed,key);
        var gate=RESTORES.computeIfAbsent(id,ignored->new FakePlayerRouletteRestoreGate());
        if(!gate.begin(token,now))return;
        FakePlayerRouletteProfileStore.Profile saved;
        try {saved=preferences().load(key);}
        catch(Exception error){gate.complete(token,false,true,now);preferenceFailure(error);return;}
        if(saved==null){gate.complete(token,true,false,now);return;}
        var expected=context;var session=runtime;
        new CloudEntityMotionClient(session.http(),session.instanceInfo()).revisions(expected,List.of(id))
            .whenComplete((revisions,error)->Minecraft.getInstance().execute(()->{
                if(!current(expected,session)||RESTORES.get(id)!=gate||!loaded(cap)
                    ||!Objects.equals(observed,observation(cap))||!key.equals(preferenceKey(cap)))return;
                Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
                boolean denied=error==null&&!revisions.containsKey(id)
                    ||cause instanceof CloudHttpException http&&(http.statusCode()==401||http.statusCode()==403);
                // Readiness can change while permission is in flight. Retry rather than losing the restore.
                boolean edited=OWN.containsKey(id);
                boolean ready=error==null&&!denied&&cap.isModelReady()&&!cap.isForceDisabled()
                    &&observed.runtimeModelId().equals(cap.getModelId())&&LEDGER.get(context,observed,System.nanoTime())==null;
                if(!gate.complete(token,ready||edited,denied,System.nanoTime())||!ready||edited)return;
                var player=(PlayerCapability)cap;player.awaitAsyncResult();
                var numbers=new Int2FloatOpenHashMap();
                variables(cap).forEach((name,value)->numbers.put(StringPool.computeIfAbsent(name),value.floatValue()));
                saved.variables().forEach((name,value)->numbers.put(StringPool.computeIfAbsent(name),value.floatValue()));
                player.updateMolangVars(player.getModelAssembly().getModelData().getHashId(),numbers);
                var owner=new Owner(cap);owner.revision=revisions.get(id);
                owner.state.roaming(saved.variables());
                String action=saved.action()==null?"":saved.action();
                if(action.isEmpty())player.clearModelSwitch();else player.requestModelSwitch(action);
                owner.state.play(player.isModelSwitching()?player.getSelectedModelId():"",System.currentTimeMillis());
                for(String expression:saved.settings().values())try {
                    player.executeExpression(GeckoLibCache.parseSimpleExpression(expression),true,false,null);
                    if(!GeckoLibCache.isRoamingVariableAssignment(expression))owner.state.expression(expression,List.of(),System.currentTimeMillis());
                } catch(Exception invalid){YesSteveModel.LOGGER.debug("[SPM fake roulette] outdated control ignored",invalid);}
                OWN.put(id,owner);retire(id);
                RENDER.put(id,new Render(cap,cap.getModelId(),owner.state.snapshot()));
            }));
    }
    private static boolean loaded(LivingAnimatable<?> cap){
        var client=Minecraft.getInstance();if(client.level==null||cap==null)return false;
        for(Entity entity:client.level.entitiesForRendering())if(entity==cap.getEntity())return true;
        return false;
    }
    private static boolean appearanceMatches(CloudEntityMotionClient.Entry entry){
        var appearance=runtime.appearances().get(context.scopeId(),entry.update().targetId());
        return appearance!=null&&Objects.equals(entry.selection().textureId(),appearance.textureId()==null?"default":appearance.textureId());
    }
    private static boolean ready(LivingAnimatable<?> cap,CloudPlayerSelection selection){
        return cap.isModelReady()&&!cap.isForceDisabled()&&selection.runtimeModelId().equals(cap.getModelId());
    }
    private static void materialize(UUID id,LivingAnimatable<?> cap,CloudEntityMotionClient.Entry entry,CloudEntityMotionLedger.Observation observed){
        var selection=entry.selection();final var expected=context;final var session=runtime;
        java.util.function.BooleanSupplier accepted=()->current(expected,session)&&loaded(cap)
            &&Objects.equals(observed,observation(cap))&&appearanceMatches(entry)
            &&Objects.equals(entry,LEDGER.get(expected,observation(cap),System.nanoTime()));
        if(ClientModelManager.getAvailableModelIds().contains(selection.runtimeModelId())){
            if(accepted.getAsBoolean()){cap.awaitAsyncResult();cap.initModelWithTexture(selection.runtimeModelId(),selection.textureId());cap.setForceDisabled(false);}return;
        }
        if(!IMPORTS.containsKey(selection)&&IMPORTS.size()<128&&System.nanoTime()-RETRIES.getOrDefault(selection,0L)>=0){
            var future=new CompletableFuture<String>();IMPORTS.put(selection,future);
            session.downloadDisplayAsset(selection.ref(),selection.format())
                .thenApply(CloudAssetClient.MemoryAsset::bytes)
                .whenComplete((bytes,error)->Minecraft.getInstance().execute(()->{
                if(!current(expected,session)){future.completeExceptionally(new CancellationException("Entity context changed"));return;}
                if(error!=null){IMPORTS.remove(selection,future);RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(error);return;}
                if(!accepted.getAsBoolean()){IMPORTS.remove(selection,future);future.completeExceptionally(new CancellationException("Entity binding changed"));return;}
                ClientModelManager.importCloudModelInMemory(selection.runtimeModelId(),selection.importFileName(),bytes,failure->{
                    if(!current(expected,session)){future.completeExceptionally(new CancellationException("Entity context changed"));return;}
                    IMPORTS.remove(selection,future);if(failure==null){RETRIES.remove(selection);future.complete(selection.runtimeModelId());}
                    else{RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(new IllegalStateException(failure.getString()));}
                });
            }));
        }
    }
    public static CloudPlayerMotion motion(AnimatableEntity<?> entity){var view=RENDER.get(entity.getEntity().getUUID());return view!=null&&view.cap==entity&&view.model.equals(view.cap.getModelId())?view.motion:null;}
    /** Only explicit editor UI calls this; passive controller and expression evaluation cannot claim publication. */
    public static void beginEdit(AnimatableEntity<?> entity){
        if(!(entity instanceof LivingAnimatable<?> cap)||entity.getEntity()==Minecraft.getInstance().player||!cap.isModelReady())return;
        tick();var observed=observation(cap);if(observed==null||!observed.runtimeModelId().equals(cap.getModelId())||OWN.size()>=128&&!OWN.containsKey(entity.getEntity().getUUID()))return;
        UUID id=entity.getEntity().getUUID();Owner owner=OWN.get(id);if(owner!=null&&owner.cap==cap)return;
        owner=new Owner(cap);var existing=motion(cap);if(existing==null)owner.state.stop(System.currentTimeMillis());else owner.state.adopt(existing);
        OWN.put(id,owner);retire(id);owner.nextPublish=0;
    }
    public static boolean ownsAppearance(Entity entity){
        var cap=capability(entity);var observed=observation(cap);if(observed==null||cap.isForceDisabled()||!cap.isModelReady()||!observed.runtimeModelId().equals(cap.getModelId()))return false;
        return OWN.containsKey(entity.getUUID())||LEDGER.get(context,observed,System.nanoTime())!=null;
    }
    public static boolean isOwner(AnimatableEntity<?> entity){Owner owner=OWN.get(entity.getEntity().getUUID());return owner!=null&&owner.cap==entity;}
    private static Map<String,Float> variables(LivingAnimatable<?> cap){
        var struct=cap instanceof MaidCapability maid?maid.getServerVarContainer():cap instanceof PlayerCapability player?player.getServerVarContainer():null;
        return struct instanceof com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct numbers?numbers.snapshotNumbers(32):Map.of();
    }
    private static String resolveMaidAction(MaidCapability cap,String key){
        if(key.isEmpty()||cap.getAnimation(key)!=null)return key;
        var properties=cap.getModelAssembly().getModelData().getModelProperties();
        var groups=new ArrayList<com.micaftic.morpher.util.data.OrderedStringMap<String,String>>();groups.add(properties.getExtraAnimation());groups.addAll(properties.getExtraAnimationClassify().values());
        for(var group:groups)if(group!=null)for(var entry:group.entrySet())if(key.equals(entry.getKey())&&entry.getValue()!=null&&cap.getAnimation(entry.getValue())!=null)return entry.getValue();
        return null;
    }
    public static void controller(AnimatableEntity<?> entity,String name,String state,long started,Map<String,Float> variables){Owner owner=OWN.get(entity.getEntity().getUUID());if(owner!=null&&owner.cap==entity)try{owner.state.controller(name,state,started,variables);}catch(IllegalArgumentException error){YesSteveModel.LOGGER.debug("[SPM entity motion] controller exceeds limits",error);}}
    public static void expression(AnimatableEntity<?> entity,String expression,List<Float> values){Owner owner=OWN.get(entity.getEntity().getUUID());if(owner!=null&&owner.cap==entity)try{owner.state.expression(expression,values,System.currentTimeMillis());}catch(IllegalArgumentException error){YesSteveModel.LOGGER.debug("[SPM entity motion] expression exceeds limits",error);}}
    public static void roaming(AnimatableEntity<?> entity,Map<String,Float> values){Owner owner=OWN.get(entity.getEntity().getUUID());if(owner!=null&&owner.cap==entity)try{owner.state.roaming(values);}catch(IllegalArgumentException error){YesSteveModel.LOGGER.debug("[SPM entity motion] variable exceeds limits",error);}}
}
