package com.micaftic.morpher.cloud.client;

import java.util.*;

/** Bounded, monotonic display input for currently loaded explicitly bound entities. */
public final class CloudEntityMotionLedger {
    public record Observation(UUID entityUuid,String entityKind,String targetId,long bindingRevision,long appearanceRevision,String runtimeModelId) {}
    private record Received(CloudEntityMotionClient.Entry entry,long deadline) {}
    private final Map<UUID,Received> entries=new HashMap<>();
    private EntityDisplayContext context;
    public synchronized void reset(EntityDisplayContext next){entries.clear();context=next;}
    public synchronized boolean receive(EntityDisplayContext expected,Observation nativeObservation,CloudEntityMotionClient.Entry entry,long receipt){
        if(!Objects.equals(context,expected)||expected==null||!matches(expected,nativeObservation,entry)||entry.expiresAtMs()<=entry.serverTimeMs())return false;
        Received old=entries.get(entry.entityUuid());
        if(old!=null&&old.entry.revision()>entry.revision())return false;
        long deadline=entry.deadlineNanos(receipt);
        if(old!=null&&old.entry.revision()==entry.revision()){
            if(!old.entry.update().equals(entry.update())||!old.entry.selection().equals(entry.selection()))return false;
            deadline=Math.min(old.deadline,deadline);
        }
        if(old==null&&entries.size()>=512)return false;
        entries.put(entry.entityUuid(),new Received(entry,deadline));return true;
    }
    public synchronized CloudEntityMotionClient.Entry get(EntityDisplayContext expected,Observation nativeObservation,long now){
        if(!Objects.equals(context,expected)||nativeObservation==null)return null;
        Received received=entries.get(nativeObservation.entityUuid());if(received==null)return null;
        if(now-received.deadline>=0||!matches(expected,nativeObservation,received.entry)){entries.remove(nativeObservation.entityUuid());return null;}
        return received.entry;
    }
    public synchronized void retain(Set<UUID> loaded){entries.keySet().retainAll(loaded);}
    public synchronized void remove(UUID id){entries.remove(id);}
    private static boolean matches(EntityDisplayContext expected,Observation observation,CloudEntityMotionClient.Entry entry){
        if(observation==null)return false;var motion=entry.update();var selection=entry.selection();
        return entry.entityUuid().equals(observation.entityUuid())&&motion.worldEpoch().equals(expected.worldEpoch())
            &&motion.dimensionId().equals(expected.dimensionId())&&selection.instanceId().equals(expected.instanceId())
            &&selection.originSha256().equals(expected.originSha256())&&motion.entityKind().equals(observation.entityKind())
            &&motion.targetId().equals(observation.targetId())&&motion.bindingRevision()==observation.bindingRevision()
            &&motion.appearanceRevision()==observation.appearanceRevision()&&selection.runtimeModelId().equals(observation.runtimeModelId());
    }
}
