package com.micaftic.morpher.client.motion;

import com.micaftic.morpher.cloud.client.CloudPlayerMotion;
import java.util.*;

/** Immutable renderer data. Peer clocks are receiver-local monotonic milliseconds. */
public record ClientMotion(boolean peer,String eventId,String animationKey,long startedAtUnixMs,
        Map<String,Float> roaming,Map<String,Controller> controllers) {
    public record Controller(String eventId,String state,long startedAtUnixMs,Map<String,Float> variables) {
        public Controller {variables=Map.copyOf(variables);}
    }
    public ClientMotion {roaming=Map.copyOf(roaming);controllers=Map.copyOf(controllers);}
    public static ClientMotion cloud(CloudPlayerMotion motion) {
        if(motion==null)return null;
        Map<String,Controller> entries=new HashMap<>();motion.controllers().forEach((name,state)->entries.put(name,
                new Controller(Long.toString(state.startedAtUnixMs()),state.state(),state.startedAtUnixMs(),state.variables())));
        return new ClientMotion(false,motion.eventId(),motion.animationKey(),motion.startedAtUnixMs(),motion.roaming(),entries);
    }
    public float elapsedTicks(long started){return Math.max(0,((peer?System.nanoTime()/1_000_000L:System.currentTimeMillis())-started)/50f);}
}
