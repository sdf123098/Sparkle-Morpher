package com.micaftic.morpher.client.animation.molang;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class PoseValueCacheTest {
    @Test void badSourceKeepsLastValueWhileHealthySourcesContinue() {
        PoseValueCache source=new PoseValueCache(new Vector3f());
        assertEquals(new Vector3f(1,2,3),source.sample(()->new Vector3f(1,2,3)));
        assertEquals(new Vector3f(1,2,3),source.sample(()->new Vector3f(Float.NaN,9,8)));
        assertEquals(new Vector3f(1,2,3),source.sample(()->{throw new IllegalArgumentException("bad expression");}));
        assertEquals(new Vector3f(1,2,3),source.sample(()->{EvaluationHealth.invalid();return new Vector3f();}));
        assertEquals(new Vector3f(4,5,6),source.sample(()->new Vector3f(4,5,6)));
    }
    @Test void returnedVectorsCannotPolluteHistory() {
        PoseValueCache cache=new PoseValueCache(new Vector3f(1));
        cache.sample(()->new Vector3f(2)).mul(Float.NaN);
        assertEquals(new Vector3f(2),cache.sample(()->new Vector3f(Float.NaN)));
    }
}
