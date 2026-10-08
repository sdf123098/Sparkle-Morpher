package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.capability.PlayerCapability;
import java.util.*;

/** Controller consumers share immutable DTOs; player and bound entity publication remain separate. */
public final class CloudMotionSources {
    private CloudMotionSources() {}
    public static CloudPlayerMotion motion(AnimatableEntity<?> entity){var motion=CloudEntityMotionSync.motion(entity);return motion!=null?motion:CloudPlayerMotionSync.motion(entity);}
    public static boolean isOwner(AnimatableEntity<?> entity){return CloudEntityMotionSync.isOwner(entity)||CloudPlayerMotionSync.isOwner(entity);}
    public static void beginEdit(AnimatableEntity<?> entity){CloudEntityMotionSync.beginEdit(entity);}
    public static void play(AnimatableEntity<?> entity,String key){if(!CloudEntityMotionSync.play(entity,key))CloudPlayerMotionSync.play(entity,key);}
    public static void stop(AnimatableEntity<?> entity){if(!CloudEntityMotionSync.play(entity,""))CloudPlayerMotionSync.stop(entity);}
    public static void controller(AnimatableEntity<?> entity,String name,String state,long started,Map<String,Float> variables){CloudPlayerMotionSync.controller(entity,name,state,started,variables);CloudEntityMotionSync.controller(entity,name,state,started,variables);}
    public static void expression(AnimatableEntity<?> entity,String expression,List<Float> values){CloudPlayerMotionSync.expression(entity,expression,values);CloudEntityMotionSync.expression(entity,expression,values);}
    public static void roaming(PlayerCapability entity,Map<String,Float> values){CloudPlayerMotionSync.roaming(entity,values);CloudEntityMotionSync.roaming(entity,values);}
    public static float elapsedTicks(long started){return CloudPlayerMotionSync.elapsedTicks(started);}
}
