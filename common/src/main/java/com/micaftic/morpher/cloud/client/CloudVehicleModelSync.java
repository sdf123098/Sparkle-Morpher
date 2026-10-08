package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Client display adapter. Reads native control ownership; never modifies a seat, entity or game tick. */
public final class CloudVehicleModelSync {
    private record Bound(CloudVehicleAppearance appearance,long deadline) {}
    private record Applied(Entity entity,VehicleCapability cap,String model,Map<String,Float> variables) {}
    private static final Map<UUID,Bound> BINDINGS=new HashMap<>();
    private static final Map<UUID,Applied> APPLIED=new HashMap<>();
    private static final Map<CloudPlayerSelection,CompletableFuture<String>> IMPORTS=new HashMap<>();
    private static final Map<CloudPlayerSelection,Long> RETRIES=new HashMap<>();
    private static CloudClientRuntime.RuntimeState runtime;
    private static Object connection,level;
    private static EntityDisplayContext context;
    private static long generation,scopeGeneration,nextPoll;
    private static boolean polling;
    private CloudVehicleModelSync() {}
    private static String kind(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static boolean vanilla(Entity entity){String kind=kind(entity);return kind.endsWith("boat")||kind.endsWith("raft")||kind.endsWith("minecart");}
    private static boolean candidate(Entity entity){return !(entity instanceof Player)
        && !(entity instanceof net.minecraft.world.entity.projectile.Projectile) && !vanilla(entity);}
    private static void reset(){
        APPLIED.values().forEach(applied->{applied.cap.awaitAsyncResult();applied.cap.resetModel();applied.cap.setFloatMap(new Int2FloatOpenHashMap());});
        APPLIED.clear();BINDINGS.clear();IMPORTS.clear();RETRIES.clear();polling=false;nextPoll=0;generation++;
    }
    private static EntityDisplayContext currentContext(Minecraft client,CloudClientRuntime.RuntimeState next){
        if(client.level==null||next==null||next.instanceInfo()==null||!next.instanceInfo().supports("vehicle_appearance_v1")||PrivacyMode.isActive())return null;
        String scope=next.scopeLifecycle().activeScopeId(),epoch=next.scopeLifecycle().activeWorldEpoch();
        if(scope==null||epoch==null)return null;
        return new EntityDisplayContext(next.instance().instanceId(),CloudAssetCache.sha256(next.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),
            scope,epoch,client.level.dimension().location().toString(),generation);
    }
    private static boolean current(EntityDisplayContext expected,CloudClientRuntime.RuntimeState session){
        Minecraft client=Minecraft.getInstance();
        return Objects.equals(context,expected)&&runtime==session&&runtime==CloudClientRuntime.state()
            &&client.level==level&&client.getConnection()==connection&&!PrivacyMode.isActive()
            &&session.scopeLifecycle().contextGeneration()==scopeGeneration;
    }
    public static void tick(){
        Minecraft client=Minecraft.getInstance();var next=CloudClientRuntime.state();
        long nextScope=next==null?0:next.scopeLifecycle().contextGeneration();
        if(next!=runtime||client.level!=level||client.getConnection()!=connection||nextScope!=scopeGeneration){reset();runtime=next;level=client.level;connection=client.getConnection();scopeGeneration=nextScope;}
        EntityDisplayContext nextContext=currentContext(client,next);
        if(!Objects.equals(context,nextContext)){reset();context=currentContext(client,next);}
        if(client.level==null)return;
        List<Entity> vehicles=new ArrayList<>();
        for(Entity entity:client.level.entitiesForRendering())if(candidate(entity))vehicles.add(entity);
        Set<UUID> loaded=new HashSet<>();vehicles.forEach(entity->loaded.add(entity.getUUID()));
        BINDINGS.keySet().retainAll(loaded);
        Set<CloudPlayerSelection> activeSelections=new HashSet<>();
        BINDINGS.values().forEach(bound->{if(bound.appearance.selection()!=null)activeSelections.add(bound.appearance.selection());});
        RETRIES.keySet().retainAll(activeSelections);
        for(UUID id:List.copyOf(APPLIED.keySet()))if(!loaded.contains(id)){var old=APPLIED.remove(id);old.cap.awaitAsyncResult();old.cap.resetModel();}
        long now=System.nanoTime();
        if(context!=null&&!polling&&now-nextPoll>=0&&!vehicles.isEmpty())poll(vehicles,now);
        for(Entity entity:vehicles)apply(entity,now,client);
    }
    private static void poll(List<Entity> vehicles,long now){
        polling=true;nextPoll=now+1_000_000_000L;final var session=runtime;final var expected=context;
        var api=new CloudVehicleAppearanceClient(session.http(),session.instanceInfo());
        var ids=vehicles.stream().map(Entity::getUUID).distinct().limit(4096).toList();
        List<CompletableFuture<Map<UUID,CloudVehicleAppearance>>> requests=new ArrayList<>();
        int batch=CloudVisualQueryBudget.batchLimit(expected,session.instanceInfo());
        for(int i=0;i<ids.size();i+=batch)requests.add(api.query(expected,ids.subList(i,Math.min(i+batch,ids.size()))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored,failure)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session))return;polling=false;
            if(failure!=null){nextPoll=System.nanoTime()+5_000_000_000L;
                Throwable cause=failure;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof CloudHttpException error&&(error.statusCode()==401||error.statusCode()==403))BINDINGS.clear();return;}
            Map<UUID,CloudVehicleAppearance> found=new HashMap<>();requests.forEach(r->found.putAll(r.join()));long receipt=System.nanoTime();
            for(UUID id:ids){var entry=found.get(id);var old=BINDINGS.get(id);
                if(entry==null){BINDINGS.remove(id);continue;}
                if(!entry.worldEpoch().equals(expected.worldEpoch())||!entry.dimensionId().equals(expected.dimensionId()))continue;
                if(old==null||entry.revision()>old.appearance.revision()
                    ||entry.sameBinding(old.appearance))BINDINGS.put(id,new Bound(entry,entry.deadlineNanos(receipt)));}
        }));
    }
    private static void apply(Entity entity,long now,Minecraft client){
        Bound bound=BINDINGS.get(entity.getUUID());
        if(bound!=null&&(now-bound.deadline>=0||!bound.appearance.entityKind().equals(kind(entity)))){BINDINGS.remove(entity.getUUID());bound=null;}
        CloudPlayerSelection independent=bound==null?null:bound.appearance.selection();
        String model=null;Map<String,Float> variables=Map.of();
        Entity passenger=entity.getControllingPassenger();
        if(independent!=null){model=independent.runtimeModelId();variables=bound.appearance.variables();
            if(!ClientModelManager.getAvailableModelIds().contains(model)){materialize(independent);clear(entity);return;}}
        else if(passenger instanceof Player player&&(player==client.player||!PrivacyMode.isActive()
                &&(CloudPlayerModelSync.ownsAppearance(player.getUUID())||CloudEntityModelSync.ownsFakeAppearance(player.getUUID())||CloudEntityMotionSync.ownsAppearance(player)))){
            var cap=PlayerCapability.get(player).orElse(null);
            if(cap!=null&&!cap.isForceDisabled()&&cap.isModelReady()){model=cap.getModelId();var motion=CloudMotionSources.motion(cap);if(motion!=null)variables=motion.roaming();}
        }else if(passenger!=null&&!PrivacyMode.isActive()&&(!CloudEntityModelSync.selectedModelId(passenger.getUUID()).isEmpty()||CloudEntityMotionSync.ownsAppearance(passenger))){
            var cap=MaidCapability.get(passenger).orElse(null);if(cap!=null&&!cap.isForceDisabled()&&cap.isModelReady()){model=cap.getModelId();var motion=CloudMotionSources.motion(cap);if(motion!=null)variables=motion.roaming();}
        }
        if(VehicleAppearancePolicy.choose(vanilla(entity),independent!=null,model!=null)==VehicleAppearancePolicy.Source.VANILLA){clear(entity);return;}
        var cap=VehicleCapability.get(entity).orElse(null);if(cap==null)return;
        Applied old=APPLIED.get(entity.getUUID());
        if(old==null||old.entity!=entity||old.cap!=cap||!Objects.equals(old.model,model)){
            cap.awaitAsyncResult();cap.resetModel();cap.setOwnerModelId(model);cap.setFloatMap(values(variables));APPLIED.put(entity.getUUID(),new Applied(entity,cap,model,variables));
        }else if(!old.variables.equals(variables)){cap.awaitAsyncResult();cap.setFloatMap(values(variables));APPLIED.put(entity.getUUID(),new Applied(entity,cap,model,variables));}
    }
    private static Int2FloatOpenHashMap values(Map<String,Float> variables){var values=new Int2FloatOpenHashMap();variables.forEach((name,value)->values.put(StringPool.computeIfAbsent(name),value.floatValue()));return values;}
    private static void clear(Entity entity){Applied old=APPLIED.remove(entity.getUUID());if(old!=null){old.cap.awaitAsyncResult();old.cap.resetModel();old.cap.setFloatMap(new Int2FloatOpenHashMap());}}
    private static void materialize(CloudPlayerSelection selection){
        if(runtime==null||context==null||IMPORTS.containsKey(selection)||IMPORTS.size()>=128
                ||System.nanoTime()-RETRIES.getOrDefault(selection,0L)<0)return;
        final var session=runtime;final var expected=context;
        var future=new CompletableFuture<String>();IMPORTS.put(selection,future);
        session.assetCache().downloadAndStore(session.assets(),selection.ref(),session.cacheRoot()).thenApplyAsync(path->{
            try{return Files.readAllBytes(path);}catch(java.io.IOException error){throw new CompletionException(error);}
        }).whenComplete((bytes,failure)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session)){future.completeExceptionally(new CancellationException("Vehicle context changed"));return;}
            if(failure!=null){IMPORTS.remove(selection,future);RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(failure);YesSteveModel.LOGGER.debug("[SPM Cloud vehicle] download failed",failure);return;}
            ClientModelManager.importLocalModel(selection.runtimeModelId(),selection.importFileName(),bytes,error->{
                if(!current(expected,session)){future.completeExceptionally(new CancellationException("Vehicle context changed"));return;}
                IMPORTS.remove(selection,future);
                if(error==null){RETRIES.remove(selection);future.complete(selection.runtimeModelId());}else{RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(new IllegalStateException(error.getString()));}
            });
        }));
    }
    /** Explicit user operation using a pre-existing Cloud target and CAS revision. */
    public static CompletableFuture<CloudVehicleAppearance> bind(UUID entityId,String target,boolean remove){
        Minecraft client=Minecraft.getInstance();tick();
        if(context==null||runtime==null)return CompletableFuture.failedFuture(new IllegalStateException("Choose a supported Cloud scope first"));
        Entity entity=null;for(Entity candidate:client.level.entitiesForRendering())if(candidate.getUUID().equals(entityId))entity=candidate;
        if(entity==null||!candidate(entity))return CompletableFuture.failedFuture(new IllegalArgumentException("Choose a loaded supported vehicle"));
        final var expected=context;final var session=runtime;final String kind=kind(entity);
        CloudPlayerSelection selection=null;
        if(!remove){var owner=PlayerCapability.get(client.player).orElse(null);String model=owner==null?null:owner.getModelId();
            if(model==null)return CompletableFuture.failedFuture(new IllegalArgumentException("Select an accessible Cloud model first"));
            var catalog=new ArrayList<>(session.assetCatalog().snapshot().values());catalog.addAll(CloudModelSelectionStore.recent(session.instance().instanceId()));
            var asset=CloudSelectedModelRecovery.findAsset(session.instance().instanceId(),model,catalog);
            if(asset==null)return CompletableFuture.failedFuture(new IllegalArgumentException("Select an accessible Cloud model first"));
            selection=new CloudPlayerSelection(expected.instanceId(),expected.originSha256(),asset.ref(),asset.format(),"default");}
        final var selected=selection;var api=new CloudVehicleAppearanceClient(session.http(),session.instanceInfo());
        return api.query(expected,List.of(entityId)).thenCompose(found->CloudEntityPublicationGuard.run(client::execute,
                ()->current(expected,session),()->{
                    var old=found.get(entityId);return api.publish(expected,entityId,kind,target,old==null?0:old.revision(),selected,Map.of());
                })).whenComplete((value,error)->client.execute(()->{if(error==null&&current(expected,session))nextPoll=0;}));
    }
}
