package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.capability.PlayerCapability;
import java.util.*;

/** Controller consumers share immutable DTOs; player and bound entity publication remain separate. */
public final class CloudMotionSources {
    private CloudMotionSources() {}
    public static CloudPlayerMotion motion(HostAnimatable<?> entity){var motion=CloudEntityMotionSync.motion(entity);return motion!=null?motion:CloudPlayerMotionSync.motion(entity);}
    public static boolean isOwner(HostAnimatable<?> entity){return CloudEntityMotionSync.isOwner(entity)||CloudPlayerMotionSync.isOwner(entity);}
    public static void beginEdit(HostAnimatable<?> entity){CloudEntityMotionSync.beginEdit(entity);}
    public static void play(HostAnimatable<?> entity,String key){if(!CloudEntityMotionSync.play(entity,key))CloudPlayerMotionSync.play(entity,key);}
    public static void stop(HostAnimatable<?> entity){if(!CloudEntityMotionSync.play(entity,""))CloudPlayerMotionSync.stop(entity);}
    public static void controller(HostAnimatable<?> entity,String name,String state,long started,Map<String,Float> variables){CloudPlayerMotionSync.controller(entity,name,state,started,variables);CloudEntityMotionSync.controller(entity,name,state,started,variables);}
    public static void expression(HostAnimatable<?> entity,String expression,List<Float> values){CloudPlayerMotionSync.expression(entity,expression,values);CloudEntityMotionSync.expression(entity,expression,values);}
    public static void roaming(PlayerCapability entity,Map<String,Float> values){CloudPlayerMotionSync.roaming(entity,values);CloudEntityMotionSync.roaming(entity,values);}
    public static float elapsedTicks(long started){return CloudPlayerMotionSync.elapsedTicks(started);}
}
