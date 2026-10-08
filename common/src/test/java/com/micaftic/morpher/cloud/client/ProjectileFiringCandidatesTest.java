package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileFiringCandidatesTest {
    private static final EntityDisplayContext WORLD=new EntityDisplayContext("test","a".repeat(64),"scope","epoch","minecraft:overworld",1);
    private static ProjectileFiringCandidates.Firing firing(UUID owner,int count,String model){return new ProjectileFiringCandidates.Firing(owner,UUID.randomUUID().toString(),Set.of(ProjectileFiringCandidates.Family.ARROW),count,model,null,Map.of("variant",2f),"minecraft:bow",0,1,0,1,0,0);}
    private static ProjectileFiringCandidates.Firing spawn(ProjectileFiringCandidates queue,UUID owner,UUID id,long time){return queue.match(WORLD,id,owner,ProjectileFiringCandidates.Family.ARROW,0,1,0,2,0,0,time);}
    @Test void freezesSelectionAndEquipmentAndRejectsLoadedReloadedWrongOwnerAndExpiredSpawns(){
        var queue=new ProjectileFiringCandidates();queue.enter(WORLD);UUID owner=UUID.randomUUID(),old=UUID.randomUUID();queue.remember(old);
        var candidate=firing(owner,1,"firing-model");assertTrue(queue.offer(WORLD,candidate,10));
        assertNull(spawn(queue,owner,old,20));assertNull(spawn(queue,UUID.randomUUID(),UUID.randomUUID(),20));
        var accepted=spawn(queue,owner,UUID.randomUUID(),30);assertSame(candidate,accepted);
        assertEquals("firing-model",accepted.runtimeModelId());assertEquals("minecraft:bow",accepted.firingItemId());
        assertNull(spawn(queue,owner,UUID.randomUUID(),40));
        assertTrue(queue.offer(WORLD,firing(owner,1,"later-model"),50));assertNull(spawn(queue,owner,UUID.randomUUID(),1_500_000_050L));
    }
    @Test void ambiguityNeverPicksAnEventButOneExplicitMultishotFreezesAllItsProjectiles(){
        var queue=new ProjectileFiringCandidates();queue.enter(WORLD);UUID owner=UUID.randomUUID();
        assertTrue(queue.offer(WORLD,firing(owner,1,"first"),0));assertFalse(queue.offer(WORLD,firing(owner,1,"second"),1));assertNull(spawn(queue,owner,UUID.randomUUID(),2));
        var multishot=firing(owner,3,"crossbow");assertTrue(queue.offer(WORLD,multishot,1_500_000_002L));
        for(int i=0;i<3;i++)assertSame(multishot,spawn(queue,owner,UUID.randomUUID(),1_500_000_003L+i));
        assertNull(spawn(queue,owner,UUID.randomUUID(),1_500_000_010L));
    }
    @Test void wrongWorldAndIncompatibleTrajectoryDoNotBecomeFiringEvidence(){
        var queue=new ProjectileFiringCandidates();queue.enter(WORLD);UUID owner=UUID.randomUUID();assertTrue(queue.offer(WORLD,firing(owner,1,"model"),0));
        assertNull(queue.match(WORLD,UUID.randomUUID(),owner,ProjectileFiringCandidates.Family.ARROW,20,1,0,2,0,0,1));
        assertNull(queue.match(WORLD,UUID.randomUUID(),owner,ProjectileFiringCandidates.Family.ARROW,0,1,0,-2,0,0,1));
        queue.enter(new EntityDisplayContext("test","a".repeat(64),"scope","epoch","minecraft:the_nether",2));
        assertNull(spawn(queue,owner,UUID.randomUUID(),2));assertFalse(queue.offer(WORLD,firing(owner,1,"model"),3));
    }
}
