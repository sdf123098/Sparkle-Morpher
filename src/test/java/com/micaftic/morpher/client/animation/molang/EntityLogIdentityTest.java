package com.micaftic.morpher.client.animation.molang;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class EntityLogIdentityTest {
    @Test void unassignedEntityIdMustNotCrashInvalidPoseRecovery(){
        var pose=new PoseValueCache(new Vector3f(1));
        assertEquals(new Vector3f(1),pose.sample(()->new Vector3f(Float.NaN)));
        assertTrue(pose.rejected());
        assertEquals("unassigned",assertDoesNotThrow(()->EntityLogIdentity.describe(()->{throw new IllegalStateException("Tried to access entity ID before ID assignment");})));
        assertEquals(new Vector3f(2),pose.sample(()->new Vector3f(2)));
    }
    @Test void assignedAndSyntheticEntityIdsRemainUsefulInLogs(){
        assertEquals("52",EntityLogIdentity.describe(()->52));
        assertEquals("-3",EntityLogIdentity.describe(()->-3));
        assertEquals("unassigned",EntityLogIdentity.describe(()->0));
    }
}
