package com.micaftic.morpher.client.animation.molang;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimulationClockTest {
    @Test void lateWorldOrHudDrawCannotRewindTheAcceptedClock() {
        SimulationClock clock=new SimulationClock();
        List<Long> ids=new ArrayList<>();
        clock.advanceFrame(163.708,(id,t,dt,recover)->ids.add(id));
        clock.advanceFrame(163.765,(id,t,dt,recover)->{assertFalse(recover);ids.add(id);});
        clock.advanceFrame(163.758,(id,t,dt,recover)->fail("late draw rewound the instance"));
        clock.advanceFrame(163.765,(id,t,dt,recover)->fail("repeat draw advanced the instance"));
        clock.advanceFrame(163.808,(id,t,dt,recover)->{assertFalse(recover);ids.add(id);});
        assertEquals(List.of(0L,1L,2L,3L,4L,5L,6L),ids);
    }
    @Test void sameStepsAtEveryRenderRateAndRepeatedDraws() {
        List<Long> reference = replay(60);
        for(int fps : new int[]{15, 30, 144, 240}) assertEquals(reference, replay(fps));
        assertEquals(121, reference.size());
    }
    private List<Long> replay(int fps) {
        SimulationClock clock = new SimulationClock();
        List<Long> steps = new ArrayList<>();
        for(int frame=0; frame<=2*fps; frame++) {
            double time=(double)frame/fps;
            clock.advance(time,(id,t,dt,recover)->steps.add(id));
            clock.advance(time,(id,t,dt,recover)->fail("duplicate draw advanced simulation"));
        }
        return steps;
    }
    @Test void gapsRecoverOnceWithoutReplayingExternalEvents() {
        SimulationClock clock = new SimulationClock();
        List<Boolean> resets = new ArrayList<>();
        clock.advance(0,(id,t,dt,recover)->resets.add(recover));
        clock.advance(.1,(id,t,dt,recover)->resets.add(recover));
        assertEquals(7, resets.size());
        clock.advance(30,(id,t,dt,recover)->{assertTrue(recover); assertEquals(0,dt); resets.add(recover);});
        assertEquals(8,resets.size());
        clock.advance(1,(id,t,dt,recover)->assertTrue(recover));
    }
}
