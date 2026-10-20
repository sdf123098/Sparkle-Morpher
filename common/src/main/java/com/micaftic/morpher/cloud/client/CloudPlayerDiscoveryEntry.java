package com.micaftic.morpher.cloud.client;
import java.util.UUID;

/** Publisher identity is separate from model content and changes when the binding changes. */
public record CloudPlayerDiscoveryEntry(UUID entityUuid,long revision,CloudPlayerSelection selection,String publisherId,String identityKind,Status status) {
    public static final class Sources {
        private final java.util.Map<UUID,String> publishers=new java.util.HashMap<>();
        public void accept(UUID uuid,String publisher,Runnable clearPrevious){
            if(!java.util.Objects.equals(publishers.get(uuid),publisher)){
                clearPrevious.run();if(publisher==null)publishers.remove(uuid);else publishers.put(uuid,publisher);
            }
        }
        public void remove(UUID uuid){publishers.remove(uuid);}
        public void clear(){publishers.clear();}
    }
    public enum Status { NONE, ACTIVE, AMBIGUOUS }
    public CloudPlayerDiscoveryEntry {
        if(entityUuid==null||revision<0||status==null||publisherId!=null&&(publisherId.length()>256||publisherId.isBlank()||publisherId.chars().anyMatch(Character::isISOControl))||status==Status.AMBIGUOUS&&selection!=null)throw new IllegalArgumentException("Invalid Cloud discovery metadata");
    }
}
