package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import com.micaftic.morpher.util.accessors.ProjectileStateAccessor;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Native entities supply motion and ownership. This adapter only sets their client display capability. */
public final class CloudProjectileModelSync {
    public record Ticket(EntityDisplayContext context,ProjectileFiringCandidates.Firing firing,String identityId) {}
    private record Frozen(Projectile entity,Ticket ticket,String bundle,long observedAt) {}
    private record Display(Projectile entity,ProjectileCapability cap,String model,String bundle,Map<String,Float> variables,String firingItem) {}
    private static final ProjectileFiringCandidates CANDIDATES=new ProjectileFiringCandidates();
    private static final ProjectileSnapshotLedger LEDGER=new ProjectileSnapshotLedger(4096);
    private static final Map<UUID,Frozen> LOCAL=new HashMap<>();
    private record Input(Ticket ticket,long deadline) {}
    private static final Map<String,Input> INPUTS=new HashMap<>();
    private static final Set<UUID> CHECKED=new HashSet<>();
    private static final Map<UUID,Display> APPLIED=new HashMap<>();
    private static final Map<UUID,CloudProjectileSnapshotClient.Lease> OWN=new HashMap<>();
    private static final Map<UUID,Long> RENEW_AT=new HashMap<>();
    private static final Set<UUID> RENEWING=new HashSet<>();
    private static final Map<CloudPlayerSelection,CompletableFuture<String>> IMPORTS=new HashMap<>();
    private static final Map<CloudPlayerSelection,Long> RETRIES=new HashMap<>();
    private static CloudClientRuntime.RuntimeState runtime;
    private static Object level,connection;
    private static EntityDisplayContext context;
    private static long generation,scopeGeneration,nextPoll;
    private static boolean polling,sharedContext;
    private CloudProjectileModelSync() {}
    private static String kind(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static ProjectileFiringCandidates.Family family(Projectile entity){
        return switch(kind(entity)){
            case "minecraft:trident"->ProjectileFiringCandidates.Family.TRIDENT;
            case "minecraft:snowball"->ProjectileFiringCandidates.Family.SNOWBALL;
            case "minecraft:egg"->ProjectileFiringCandidates.Family.EGG;
            case "minecraft:firework_rocket"->ProjectileFiringCandidates.Family.FIREWORK;
            default->entity instanceof AbstractArrow?ProjectileFiringCandidates.Family.ARROW:null;
        };
    }
    private static boolean shared(){return sharedContext&&!PrivacyMode.isActive();}
    private static boolean current(EntityDisplayContext expected,CloudClientRuntime.RuntimeState session){
        var client=Minecraft.getInstance();return shared()&&Objects.equals(context,expected)&&runtime==session&&CloudClientRuntime.state()==session
            &&client.level==level&&client.getConnection()==connection&&(session==null||session.scopeLifecycle().contextGeneration()==scopeGeneration);
    }
    private static void reset(){
        if(sharedContext&&runtime!=null){var api=new CloudProjectileSnapshotClient(runtime.http(),runtime.instanceInfo());OWN.values().forEach(lease->api.withdraw(context,lease).exceptionally(error->null));}
        for(UUID id:List.copyOf(APPLIED.keySet()))clear(id);
        sharedContext=false;
        LOCAL.clear();INPUTS.clear();CHECKED.clear();APPLIED.clear();OWN.clear();RENEW_AT.clear();RENEWING.clear();IMPORTS.clear();RETRIES.clear();polling=false;nextPoll=0;generation++;
        CANDIDATES.enter(null);LEDGER.enter(null);
    }
    private static void ensureContext(){
        var client=Minecraft.getInstance();var next=CloudClientRuntime.state();long nextScope=next==null?0:next.scopeLifecycle().contextGeneration();
        if(next!=runtime||client.level!=level||client.getConnection()!=connection||nextScope!=scopeGeneration){reset();runtime=next;level=client.level;connection=client.getConnection();scopeGeneration=nextScope;context=null;}
        EntityDisplayContext nextContext=null;boolean nextShared=false;
        if(client.level!=null){String dimension=client.level.dimension().location().toString();
            String scope=next==null?null:next.scopeLifecycle().activeScopeId(),epoch=next==null?null:next.scopeLifecycle().activeWorldEpoch();
            if(next!=null&&next.instanceInfo()!=null&&next.instanceInfo().supports("projectile_snapshot_v1")&&!PrivacyMode.isActive()&&scope!=null&&epoch!=null){
                nextShared=true;
                nextContext=new EntityDisplayContext(next.instance().instanceId(),CloudAssetCache.sha256(next.instance().origin().toString().getBytes(StandardCharsets.UTF_8)),scope,epoch,dimension,generation);
            }
            else nextContext=new EntityDisplayContext("local","0".repeat(64),"local","world-"+generation,dimension,generation);
        }
        if(!Objects.equals(context,nextContext)||sharedContext!=nextShared){reset();sharedContext=nextShared;context=nextContext==null?null:new EntityDisplayContext(nextContext.instanceId(),nextContext.originSha256(),nextContext.scopeId(),nextContext.worldEpoch(),nextContext.dimensionId(),generation);CANDIDATES.enter(context);LEDGER.enter(context);}
    }
    /** Capture before native item use changes inventory. Commit only if the native use consumes the action. */
    public static Ticket captureUse(Player player,InteractionHand hand){
        if(player!=Minecraft.getInstance().player)return null;ItemStack item=player.getItemInHand(hand);String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        Set<ProjectileFiringCandidates.Family> families=new HashSet<>();int count=1;
        if(id.equals("minecraft:crossbow")){
            var charged=item.get(DataComponents.CHARGED_PROJECTILES);if(charged==null||charged.isEmpty())return null;
            var ammunition=charged.getItems();count=ammunition.size();for(var ammo:ammunition){String ammoId=BuiltInRegistries.ITEM.getKey(ammo.getItem()).toString();
                if(ammoId.equals("minecraft:firework_rocket"))families.add(ProjectileFiringCandidates.Family.FIREWORK);
                else if(ammoId.endsWith("arrow"))families.add(ProjectileFiringCandidates.Family.ARROW);else return null;}
        }else if(id.equals("minecraft:snowball"))families.add(ProjectileFiringCandidates.Family.SNOWBALL);
        else if(id.equals("minecraft:egg"))families.add(ProjectileFiringCandidates.Family.EGG);else return null;
        return capture(player,item,families,count);
    }
    public static void captureRelease(Player player){
        if(player!=Minecraft.getInstance().player||player.getTicksUsingItem()<3)return;ItemStack item=player.getUseItem();String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        ProjectileFiringCandidates.Family family=id.equals("minecraft:bow")?ProjectileFiringCandidates.Family.ARROW:id.equals("minecraft:trident")?ProjectileFiringCandidates.Family.TRIDENT:null;
        if(family!=null)commit(capture(player,item,Set.of(family),1),true);
    }
    private static Ticket capture(Player player,ItemStack item,Set<ProjectileFiringCandidates.Family> families,int count){
        ensureContext();var cap=PlayerCapability.get(player).orElse(null);if(context==null||cap==null||!cap.isModelReady()||cap.isForceDisabled())return null;cap.awaitAsyncResult();
        var position=player.getEyePosition();var direction=player.getLookAngle();var struct=cap.getServerVarContainer();
        try{Map<String,Float> variables=struct instanceof Int2FloatOpenHashMapStruct numbers?numbers.snapshotNumbers(32):CloudPlayerMotionSync.snapshot(cap.getModelId()).roaming();
            return new Ticket(context,new ProjectileFiringCandidates.Firing(player.getUUID(),UUID.randomUUID().toString(),families,count,cap.getModelId(),
                CloudPlayerModelSync.ownCloudSelection(),variables,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),position.x,position.y,position.z,direction.x,direction.y,direction.z),CloudPlayerModelSync.verifiedOwnIdentity());}
        catch(IllegalArgumentException error){YesSteveModel.LOGGER.debug("[SPM projectile] firing input exceeds limits",error);return null;}
    }
    public static void commit(Ticket ticket,boolean used){if(ticket!=null&&used){ensureContext();long now=System.nanoTime();INPUTS.entrySet().removeIf(e->now-e.getValue().deadline>=0);
        if(INPUTS.size()<64&&CANDIDATES.offer(ticket.context,ticket.firing,now))INPUTS.put(ticket.firing.eventId(),new Input(ticket,now+1_500_000_000L));}}
    /** Invoked after the native add-entity packet creates the real entity and restores its owner. */
    public static void nativeSpawn(Projectile projectile){
        ensureContext();if(context==null)return;var family=family(projectile);if(family==null){CANDIDATES.remember(projectile.getUUID());return;}
        Entity owner=projectile.getOwner();var p=projectile.position();var v=projectile.getDeltaMovement();long now=System.nanoTime();
        var firing=CANDIDATES.match(context,projectile.getUUID(),owner==null?null:owner.getUUID(),family,p.x,p.y,p.z,v.x,v.y,v.z,now);if(firing==null||LOCAL.size()>=4096)return;
        var assembly=ClientModelManager.getModelContext(firing.runtimeModelId()).orElse(null);if(assembly==null)return;
        String bundle=kind(projectile);if(!assembly.getProjectileModels().containsKey(ResourceApi.parseNative(bundle))){if(projectile instanceof AbstractArrow&&assembly.getProjectileModels().containsKey(ResourceApi.parseNative("minecraft:arrow")))bundle="minecraft:arrow";else return;}
        var input=INPUTS.get(firing.eventId());if(input==null||now-input.deadline>=0)return;
        LOCAL.put(projectile.getUUID(),new Frozen(projectile,input.ticket,bundle,now));
    }
    public static void tick(){
        ensureContext();var client=Minecraft.getInstance();if(context==null||client.level==null)return;
        Map<UUID,Projectile> loaded=new HashMap<>();for(Entity entity:client.level.entitiesForRendering())if(entity instanceof Projectile projectile&&loaded.size()<4096&&family(projectile)!=null)loaded.put(entity.getUUID(),projectile);
        for(UUID id:List.copyOf(APPLIED.keySet()))if(!loaded.containsKey(id)){clear(id);LEDGER.unload(id);}
        LOCAL.entrySet().removeIf(entry->loaded.get(entry.getKey())!=entry.getValue().entity);
        LEDGER.retain(loaded.keySet());CHECKED.retainAll(LOCAL.keySet());INPUTS.entrySet().removeIf(e->System.nanoTime()-e.getValue().deadline>=0);
        Set<CloudPlayerSelection> selections=new HashSet<>();loaded.keySet().forEach(id->{var lease=LEDGER.get(id,System.nanoTime());if(lease!=null)selections.add(lease.snapshot().selection());});RETRIES.keySet().retainAll(selections);
        OWN.keySet().retainAll(loaded.keySet());RENEW_AT.keySet().retainAll(loaded.keySet());
        long now=System.nanoTime();for(var entry:loaded.entrySet()){Entity owner=entry.getValue().getOwner();LEDGER.observe(entry.getKey(),new ProjectileSnapshotLedger.Observation(kind(entry.getValue()),owner==null?null:owner.getUUID()));}
        if(shared()&&!polling&&now-nextPoll>=0&&!loaded.isEmpty())poll(loaded);
        for(var entry:loaded.entrySet()){
            UUID id=entry.getKey();Projectile entity=entry.getValue();Frozen frozen=LOCAL.get(id);
            if(frozen!=null){if(now-frozen.observedAt<100_000_000L)continue;
                Entity owner=entity.getOwner();if(owner==null||!owner.getUUID().equals(frozen.ticket.firing.ownerUuid())||!CHECKED.contains(id)&&entity instanceof ProjectileStateAccessor state&&state.isInGround()){
                    LOCAL.remove(id);clear(id);continue;}
                CHECKED.add(id);
                if(shared()&&frozen.ticket.identityId!=null&&frozen.ticket.firing.selection()!=null&&!OWN.containsKey(id)&&!RENEWING.contains(id))publish(id,frozen);
                apply(entity,frozen.ticket.firing.runtimeModelId(),frozen.bundle,frozen.ticket.firing.variables(),frozen.ticket.firing.firingItemId());
            }else{var lease=LEDGER.get(id,now);if(lease==null){clear(id);continue;}var snapshot=lease.snapshot();
                if(!ClientModelManager.getAvailableModelIds().contains(snapshot.selection().runtimeModelId())){materialize(snapshot.selection());clear(id);continue;}
                if(!snapshot.projectileBundleKey().equals(kind(entity))&&!(entity instanceof AbstractArrow&&snapshot.projectileBundleKey().equals("minecraft:arrow"))){clear(id);continue;}
                apply(entity,snapshot.selection().runtimeModelId(),snapshot.projectileBundleKey(),snapshot.variables(),snapshot.firingItemId());}
            var lease=OWN.get(id);if(lease!=null&&shared()&&!RENEWING.contains(id)&&now-RENEW_AT.getOrDefault(id,Long.MAX_VALUE)>=0)renew(id,lease);
        }
    }
    private static void apply(Projectile entity,String model,String bundle,Map<String,Float> variables,String firingItem){
        var cap=ProjectileCapability.get(entity).orElse(null);if(cap==null)return;Display old=APPLIED.get(entity.getUUID());
        if(old!=null&&old.entity==entity&&old.cap==cap&&old.model.equals(model)&&old.bundle.equals(bundle)&&old.variables.equals(variables))return;
        cap.awaitAsyncResult();cap.setDisplayBundleKey(bundle);cap.updateModelId(model);var values=new Int2FloatOpenHashMap();variables.forEach((name,value)->values.put(StringPool.computeIfAbsent(name),value.floatValue()));cap.setFloatProperties(values);
        if(entity instanceof ProjectileStateAccessor state)state.ysm$setDisplayFiringItem(firingItem);
        APPLIED.put(entity.getUUID(),new Display(entity,cap,model,bundle,variables,firingItem));
    }
    private static void clear(UUID id){Display old=APPLIED.remove(id);if(old!=null){old.cap.awaitAsyncResult();old.cap.resetModel();old.cap.setFloatProperties(null);old.cap.setDisplayBundleKey(null);if(old.entity instanceof ProjectileStateAccessor state)state.ysm$setDisplayFiringItem(null);}}
    private static void publish(UUID id,Frozen frozen){
        var expected=context;var session=runtime;var firing=frozen.ticket.firing;RENEWING.add(id);
        var snapshot=new ProjectileAppearanceSnapshot(expected.worldEpoch(),expected.dimensionId(),id,kind(frozen.entity),frozen.ticket.identityId,firing.ownerUuid(),
                CloudAssetCache.sha256((firing.eventId()+":"+id).getBytes(StandardCharsets.UTF_8)),firing.selection(),frozen.bundle,firing.variables(),firing.firingItemId());
        var api=new CloudProjectileSnapshotClient(session.http(),session.instanceInfo());CompletableFuture<CloudProjectileSnapshotClient.Lease> request;
        try{request=api.publish(expected,snapshot);}catch(IllegalArgumentException error){request=CompletableFuture.failedFuture(error);}
        request.whenComplete((lease,error)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session)){if(error==null)api.withdraw(expected,lease).exceptionally(failure->null);return;}RENEWING.remove(id);
            if(error==null){if(LOCAL.get(id)==frozen){OWN.put(id,lease);RENEW_AT.put(id,System.nanoTime()+renewDelay(lease));}}
            else{RENEW_AT.put(id,Long.MAX_VALUE);LOCAL.remove(id);clear(id);YesSteveModel.LOGGER.debug("[SPM projectile] publication rejected",error);}
        }));
    }
    private static long renewDelay(CloudProjectileSnapshotClient.Lease lease){return Math.max(1_000_000_000L,(lease.expiresAtMs()-lease.serverTimeMs())*500_000L);}
    private static void renew(UUID id,CloudProjectileSnapshotClient.Lease lease){
        var expected=context;var session=runtime;var api=new CloudProjectileSnapshotClient(session.http(),session.instanceInfo());RENEWING.add(id);
        api.renew(expected,lease).whenComplete((next,error)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session)){if(error==null)api.withdraw(expected,next).exceptionally(failure->null);return;}RENEWING.remove(id);
            if(error==null&&OWN.get(id)==lease){OWN.put(id,next);RENEW_AT.put(id,System.nanoTime()+renewDelay(next));}
            else{OWN.remove(id);LOCAL.remove(id);LEDGER.unload(id);clear(id);if(error==null)api.withdraw(expected,next).exceptionally(failure->null);}
        }));
    }
    private static void poll(Map<UUID,Projectile> loaded){
        var expected=context;var session=runtime;var api=new CloudProjectileSnapshotClient(session.http(),session.instanceInfo());polling=true;nextPoll=System.nanoTime()+2_000_000_000L;
        var ids=List.copyOf(loaded.keySet());int batch=CloudVisualQueryBudget.batchLimit(expected,session.instanceInfo());var requests=new ArrayList<CompletableFuture<Map<UUID,CloudProjectileSnapshotClient.Lease>>>();
        for(int i=0;i<ids.size();i+=batch)requests.add(api.query(expected,ids.subList(i,Math.min(i+batch,ids.size()))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored,error)->Minecraft.getInstance().execute(()->{
            if(!current(expected,session))return;polling=false;if(error!=null){nextPoll=System.nanoTime()+5_000_000_000L;
                Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof CloudHttpException denied&&(denied.statusCode()==401||denied.statusCode()==403)){LEDGER.enter(null);LEDGER.enter(context);for(UUID id:List.copyOf(APPLIED.keySet()))if(!LOCAL.containsKey(id))clear(id);}
                return;}
            Map<UUID,CloudProjectileSnapshotClient.Lease> found=new HashMap<>();requests.forEach(request->found.putAll(request.join()));long now=System.nanoTime();
            for(UUID id:ids){Projectile entity=loaded.get(id);if(Minecraft.getInstance().level.getEntity(entity.getId())!=entity)continue;
                var lease=found.get(id);if(lease==null){LEDGER.unload(id);continue;}Entity owner=entity.getOwner();LEDGER.observe(id,new ProjectileSnapshotLedger.Observation(kind(entity),owner==null?null:owner.getUUID()));
                LEDGER.accept(expected,lease.snapshot(),lease.revision(),lease.deadlineNanos(now),now);}
        }));
    }
    private static void materialize(CloudPlayerSelection selection){
        if(IMPORTS.containsKey(selection)||IMPORTS.size()>=128||System.nanoTime()-RETRIES.getOrDefault(selection,0L)<0)return;
        var expected=context;var session=runtime;var future=new CompletableFuture<String>();IMPORTS.put(selection,future);
        session.assetCache().downloadAndStore(session.assets(),selection.ref(),session.cacheRoot()).thenApplyAsync(path->{try{return Files.readAllBytes(path);}catch(java.io.IOException error){throw new CompletionException(error);}})
            .whenComplete((bytes,error)->Minecraft.getInstance().execute(()->{
                if(!current(expected,session)||!wanted(selection)){IMPORTS.remove(selection,future);future.completeExceptionally(new CancellationException("Projectile display permission changed"));return;}
                if(error!=null){IMPORTS.remove(selection,future);RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(error);return;}
                ClientModelManager.importLocalModel(selection.runtimeModelId(),selection.importFileName(),bytes,failure->{
                    if(!current(expected,session)||!wanted(selection)){IMPORTS.remove(selection,future);future.completeExceptionally(new CancellationException("Projectile display permission changed"));return;}IMPORTS.remove(selection,future);
                    if(failure==null){RETRIES.remove(selection);future.complete(selection.runtimeModelId());}else{RETRIES.put(selection,System.nanoTime()+5_000_000_000L);future.completeExceptionally(new IllegalStateException(failure.getString()));}
                });
            }));
    }
    private static boolean wanted(CloudPlayerSelection selection){
        long now=System.nanoTime();for(UUID id:APPLIED.keySet()){var lease=LEDGER.get(id,now);if(lease!=null&&lease.snapshot().selection().equals(selection))return true;}
        var client=Minecraft.getInstance();if(client.level==null)return false;
        for(Entity entity:client.level.entitiesForRendering())if(entity instanceof Projectile){var lease=LEDGER.get(entity.getUUID(),now);if(lease!=null&&lease.snapshot().selection().equals(selection))return true;}
        return false;
    }
}
