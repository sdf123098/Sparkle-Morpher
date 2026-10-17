package com.micaftic.morpher.client.peer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.motion.ClientMotion;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.peer.*;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread application, synchronized owner capture, immutable worker snapshots. */
public final class PeerMotionSync {
    private record Binding(String model,PeerRoomRuntime runtime,long revision){}
    private record Controller(String state,long sequence,long began,long localToken,Map<String,Float> variables){}
    private record Event(long sequence,long began,List<Float> values,Map<String,Float> assignments){}
    private record Remote(PeerSession owner,String model,long revision,long sequence,long expires,ClientMotion motion,Map<String,Float> variables,List<PeerMotion.NumericEvent> events,long received,long transit){}
    private static final Map<String,PeerModelPolicy> POLICIES=new ConcurrentHashMap<>();
    private static final Deque<String> policyOrder=new ArrayDeque<>();
    private static volatile String localModel;
    public static void keepLocalModel(String model){localModel=model;}
    private static final Map<UUID,Remote> REMOTES=new ConcurrentHashMap<>();
    private static final Map<UUID,Applied> APPLIED=new HashMap<>();
    private static volatile Binding binding;
    private static volatile ClientMotion ownSnapshot;
    private static long sequence,eventSequence,transitionSequence,numericSequence,actionBegan,nextPublish;
    private static String action="";
    private static final Map<String,Float> roaming=new LinkedHashMap<>();
    private static final Map<String,Float> variables=new LinkedHashMap<>();
    private static final Map<String,Controller> controllers=new LinkedHashMap<>();
    private static final Deque<Event> events=new ArrayDeque<>();
    private static final class Applied {final PlayerCapability cap;String action;Map<String,Float> roaming=Map.of(),variables=Map.of();long numeric;Applied(PlayerCapability cap){this.cap=cap;}}
    private PeerMotionSync(){}
    public static void register(String model,PeerModelPolicy policy){synchronized(POLICIES){POLICIES.put(model,policy);policyOrder.remove(model);policyOrder.addLast(model);
        int attempts=policyOrder.size();while(POLICIES.size()>64&&attempts-->0){String old=policyOrder.removeFirst();if(Objects.equals(old,localModel)||ClientPeerRuntime.retainsMotionPolicy(old))policyOrder.addLast(old);else POLICIES.remove(old);}
    }}
    public static void forget(String model){synchronized(POLICIES){POLICIES.remove(model);policyOrder.remove(model);}}
    private static boolean allowed(){return com.micaftic.morpher.core.network.NetworkAccess.allowsPeer()&&!com.micaftic.morpher.client.PrivacyMode.isActive();}
    public static boolean handles(AnimatableEntity<?> entity){if(!allowed()||!(entity instanceof CustomPlayerEntity custom))return false;var own=binding;return own!=null&&custom.isLocalPlayerModel()&&custom.getModelId().equals(own.model)||custom.getModelId().startsWith("peer:");}
    public static boolean isOwner(AnimatableEntity<?> entity){var own=binding;return own!=null&&entity instanceof CustomPlayerEntity custom&&custom.isLocalPlayerModel()&&custom.getModelId().equals(own.model)&&entity.getEntity()==Minecraft.getInstance().player;}
    public static ClientMotion motion(AnimatableEntity<?> entity){if(!handles(entity))return null;if(isOwner(entity))return ownSnapshot;var state=REMOTES.get(entity.getEntity().getUUID());return state!=null&&System.nanoTime()<state.expires&&entity instanceof CustomPlayerEntity custom&&custom.getModelId().equals(state.model)?state.motion:null;}
    private static PeerModelPolicy policy(){var own=binding;return own==null?null:POLICIES.get(own.model);}
    public static synchronized void play(AnimatableEntity<?> entity,String key){
        if(!(entity instanceof CustomPlayerEntity custom))return;if(key.isEmpty())custom.clearModelSwitch();else custom.requestModelSwitch(key);
        var policy=policy();if(!isOwner(entity)||policy==null||!policy.actions().contains(key))return;
        action=key;eventSequence++;actionBegan=System.nanoTime();nextPublish=0;
    }
    public static synchronized void roaming(PlayerCapability entity,Map<String,Float> values){var policy=policy();if(!isOwner(entity)||policy==null)return;var permitted=policy.numbers(values,64);permitted.forEach((key,value)->{if(roaming.containsKey(key)||roaming.size()<64)roaming.put(key,value);});}
    public static synchronized void controller(AnimatableEntity<?> entity,String name,String state,long localToken,Map<String,Float> values){
        var policy=policy();if(!isOwner(entity)||policy==null||!policy.controller(name,state))return;var old=controllers.get(name);if(old==null&&controllers.size()>=16)return;
        boolean change=old==null||old.localToken!=localToken||!old.state.equals(state);
        controllers.put(name,new Controller(state,change?++transitionSequence:old.sequence,change?System.nanoTime():old.began,localToken,policy.numbers(values,8)));
    }
    public static synchronized void expression(AnimatableEntity<?> entity,String expression,List<Float> values){
        if(!isOwner(entity)||values.size()>16||values.stream().anyMatch(value->value==null||!Float.isFinite(value)))return;
        var policy=policy();var assignments=expression.isEmpty()?Map.<String,Float>of():policy==null?Map.<String,Float>of():PeerNumericAssignments.capture(expression,policy.variables());
        if(!expression.isEmpty()){if(assignments.isEmpty())return;assignments.forEach((name,value)->{if(variables.containsKey(name)||variables.size()<16)variables.put(name,value);});nextPublish=0;return;}
        if(events.size()==4)events.removeFirst();events.addLast(new Event(++numericSequence,System.nanoTime(),List.copyOf(values),assignments));nextPublish=0;
    }
    private static long age(long now,long began){return Math.min(86_400_000L,Math.max(0,(now-began)/1_000_000L));}
    public static synchronized void tick(String model,PeerRoomRuntime runtime,boolean sharing){
        var appearance=runtime.publishedAppearance();var source=com.micaftic.morpher.client.ClientModelManager.MODEL_LIBRARY.source(model);boolean live=source!=null&&source.type()==com.micaftic.morpher.core.model.ModelSourceType.LOCAL&&allowed()&&sharing&&runtime.connected()&&appearance!=null&&!appearance.cleared()&&POLICIES.containsKey(model);
        Binding next=live?new Binding(model,runtime,appearance.revision()):null;
        if(!Objects.equals(binding,next)){
            binding=next;ownSnapshot=null;action="";sequence=eventSequence=transitionSequence=numericSequence=0;controllers.clear();events.clear();roaming.clear();variables.clear();actionBegan=System.nanoTime();nextPublish=0;
            if(next!=null){var seed=com.micaftic.morpher.cloud.client.CloudPlayerMotionSync.snapshot(model);if(policy().actions().contains(seed.animationKey())){action=seed.animationKey();eventSequence=1;actionBegan-=Math.min(86_400_000L,Math.max(0,System.currentTimeMillis()-seed.startedAtUnixMs()))*1_000_000L;}}
        }
        long now=System.nanoTime();REMOTES.entrySet().removeIf(entry->now>=entry.getValue().expires);
        if(next!=null){
            var mc=Minecraft.getInstance();if(mc.player!=null&&mc.options.getCameraType().isFirstPerson())PlayerCapability.get(mc.player).ifPresent(cap->{if(cap.isModelReady()&&!cap.isForceDisabled())cap.advanceCloudMotion();});
            events.removeIf(event->age(now,event.began)>1000);
            if(now>=nextPublish){
                nextPublish=now+500_000_000L;Map<String,PeerMotion.Controller> states=new HashMap<>();controllers.forEach((name,state)->states.put(name,new PeerMotion.Controller(state.sequence,state.state,age(now,state.began),state.variables)));
                var numeric=events.stream().map(event->new PeerMotion.NumericEvent(event.sequence,age(now,event.began),event.values,event.assignments)).toList();
                var wire=new PeerMotion(next.revision,++sequence,eventSequence,action,age(now,actionBegan),3000,roaming,variables,states,numeric);
                try{wire.encode();ownSnapshot=convert(null,wire,0,now);runtime.motion(wire);}catch(java.io.IOException tooLarge){com.micaftic.morpher.YesSteveModel.LOGGER.debug("[SM][PeerMotion] state exceeds wire budget");}
            }
        }
        var mc=Minecraft.getInstance();if(mc.level!=null)for(var player:mc.level.players())apply(player);
    }
    private static ClientMotion convert(PeerSession owner,PeerMotion motion,long transit,long now){
        String prefix=(owner==null?"own":owner.memberSession().toString())+":"+motion.appearanceRevision()+":";
        Map<String,ClientMotion.Controller> entries=new HashMap<>();motion.controllers().forEach((name,state)->entries.put(name,new ClientMotion.Controller(prefix+state.transitionSequence(),state.state(),now/1_000_000L-state.ageMillis()-transit,state.variables())));
        return new ClientMotion(true,prefix+motion.eventSequence(),motion.action(),now/1_000_000L-motion.actionAgeMillis()-transit,motion.roaming(),entries);
    }
    public static void receive(List<PeerMotionReceipt> receipts){
        if(!allowed())return;long now=System.nanoTime();
        for(var receipt:receipts){var member=ClientPeerRuntime.motionMember(receipt.owner().minecraftUuid());var wire=receipt.motion();
            if(member==null||!member.session().equals(receipt.owner())||member.appearance()==null||member.appearance().cleared()||member.appearance().revision()!=wire.appearanceRevision())continue;
            String model=member.appearance().model().runtimeModelId();var policy=POLICIES.get(model);if(policy==null||!policy.accepts(wire))continue;
            UUID uuid=member.session().minecraftUuid();var previous=REMOTES.get(uuid);if(previous!=null&&previous.owner.equals(receipt.owner())&&previous.revision==wire.appearanceRevision()&&previous.sequence>=wire.sequence())continue;
            REMOTES.put(uuid,new Remote(receipt.owner(),model,wire.appearanceRevision(),wire.sequence(),now+(wire.ttlMillis()-receipt.transitAgeMillis())*1_000_000L,convert(receipt.owner(),wire,receipt.transitAgeMillis(),now),wire.modelVariables(),wire.events(),now,receipt.transitAgeMillis()));
        }
    }
    private static void apply(Player player){
        if(!ClientPeerRuntime.ownsAppearance(player.getUUID()))return;
        PlayerCapability.get(player).ifPresent(cap->{if(!cap.isModelReady()||cap.isForceDisabled())return;cap.awaitAsyncResult();var remote=REMOTES.get(player.getUUID());var applied=APPLIED.get(player.getUUID());if(applied==null||applied.cap!=cap){applied=new Applied(cap);APPLIED.put(player.getUUID(),applied);}
            if(remote==null||!cap.getModelId().equals(remote.model)){if(applied.action!=null)cap.clearModelSwitch();if(!applied.roaming.isEmpty())cap.updateMolangVars(cap.getModelAssembly().getModelData().getHashId(),new Int2FloatOpenHashMap());for(var name:applied.variables.keySet())assign(cap,name,0f);applied.action=null;applied.roaming=Map.of();applied.variables=Map.of();return;}
            var motion=remote.motion;if(!motion.roaming().equals(applied.roaming)){var vars=new Int2FloatOpenHashMap();motion.roaming().forEach((name,value)->vars.put(StringPool.computeIfAbsent(name),value.floatValue()));cap.updateMolangVars(cap.getModelAssembly().getModelData().getHashId(),vars);applied.roaming=motion.roaming();}
            if(!remote.variables.equals(applied.variables)){for(var assignment:remote.variables.entrySet())assign(cap,assignment.getKey(),assignment.getValue());applied.variables=remote.variables;}
            if(!motion.eventId().equals(applied.action)){if(motion.animationKey().isEmpty())cap.clearModelSwitch();else cap.requestModelSwitch(motion.animationKey());applied.action=motion.eventId();}
            for(var event:remote.events){if(event.sequence()<=applied.numeric)continue;applied.numeric=event.sequence();if(event.ageMillis()+remote.transit+age(System.nanoTime(),remote.received)>1000)continue;try{if(event.assignments().isEmpty())cap.executeAnimationExpression(new it.unimi.dsi.fastutil.floats.FloatArrayList(event.values()));else for(var assignment:event.assignments().entrySet())cap.executeExpression(com.micaftic.morpher.geckolib3.resource.GeckoLibCache.parseSimpleExpression(assignment.getKey()+"="+Float.toString(assignment.getValue())+";"),true,false,null);}catch(Exception failure){com.micaftic.morpher.YesSteveModel.LOGGER.debug("[SM][PeerMotion] numeric event ignored",failure);}}
        });
    }
    private static void assign(PlayerCapability cap,String name,float value){try{cap.executeExpression(com.micaftic.morpher.geckolib3.resource.GeckoLibCache.parseSimpleExpression(name+"="+Float.toString(value)+";"),true,false,null);}catch(Exception ignored){}}
    public static void remove(UUID uuid){REMOTES.remove(uuid);APPLIED.remove(uuid);}
    public static synchronized void reset(){binding=null;ownSnapshot=null;REMOTES.clear();APPLIED.clear();roaming.clear();variables.clear();controllers.clear();events.clear();}
}
