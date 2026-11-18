package com.micaftic.morpher.client.motion;

import com.micaftic.morpher.cloud.client.CloudMotionSources;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.peer.PeerMotionSync;
import java.util.*;

/** Routes the physical appearance owner's motion, preserving the existing Cloud protocol. */
public final class ClientMotionSources {
    private ClientMotionSources(){}
    public static ClientMotion motion(HostAnimatable<?> entity){return PeerMotionSync.handles(entity)?PeerMotionSync.motion(entity):ClientMotion.cloud(CloudMotionSources.motion(entity));}
    public static boolean isOwner(HostAnimatable<?> entity){return PeerMotionSync.handles(entity)?PeerMotionSync.isOwner(entity):CloudMotionSources.isOwner(entity);}
    public static void beginEdit(HostAnimatable<?> entity){if(!PeerMotionSync.handles(entity))CloudMotionSources.beginEdit(entity);}
    public static void play(HostAnimatable<?> entity,String key){if(PeerMotionSync.handles(entity))PeerMotionSync.play(entity,key);else CloudMotionSources.play(entity,key);}
    public static void stop(HostAnimatable<?> entity){if(PeerMotionSync.handles(entity))PeerMotionSync.play(entity,"");else CloudMotionSources.stop(entity);}
    public static void controller(HostAnimatable<?> entity,String name,String state,long started,Map<String,Float> values){if(PeerMotionSync.handles(entity))PeerMotionSync.controller(entity,name,state,started,values);else CloudMotionSources.controller(entity,name,state,started,values);}
    public static void expression(HostAnimatable<?> entity,String expression,List<Float> values){if(PeerMotionSync.handles(entity))PeerMotionSync.expression(entity,expression,values);else CloudMotionSources.expression(entity,expression,values);}
    public static void roaming(PlayerCapability entity,Map<String,Float> values){if(PeerMotionSync.handles(entity))PeerMotionSync.roaming(entity,values);else CloudMotionSources.roaming(entity,values);}
    public static float elapsedTicks(ClientMotion motion,long started){return motion.elapsedTicks(started);}
}
