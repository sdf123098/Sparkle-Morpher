package com.micaftic.morpher.client.animation.molang;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KipfelSimulationTest {
    private static final KipfelSimulation.Noise NOISE = (seed,x,y)->(float)(.25*Math.sin(x+y+seed));
    private static KipfelSimulation.Input input(double t) {
        return new KipfelSimulation.Input((float)(2+Math.sin(t)), (float)(-4*Math.sin(t)),
                (float)Math.cos(t), (float)(40*Math.sin(t)), (float)(90*Math.cos(t)), 0);
    }
    @Test void recordedInputsHaveSameStateAtAllFps() {
        var expected = replay(60);
        for(int fps:new int[]{15,30,144,240}) assertEquals(expected,replay(fps));
    }
    private KipfelSimulation.Output replay(int fps) {
        KipfelSimulation simulation = new KipfelSimulation(NOISE);
        for(int i=0;i<=2*fps;i++) {
            double time=(double)i/fps;
            simulation.advance(time,KipfelSimulationTest::input);
            var before=simulation.output();
            simulation.advance(time,KipfelSimulationTest::input);
            assertEquals(before,simulation.output());
        }
        return simulation.output();
    }
    @Test void formerDirectionBoundaryIsContinuous() {
        float a=KipfelSimulation.targetAngle(new KipfelSimulation.Input(.1f,-3.5f,-.049f,0,0,0));
        float b=KipfelSimulation.targetAngle(new KipfelSimulation.Input(.1f,-3.5f,-.050f,0,0,0));
        assertTrue(Math.abs(a-b)<1,"old threshold caused 75 degree jump");
    }
    @Test void initializationAndRecoveryHaveNoDerivativeImpulse() {
        KipfelSimulation simulation=new KipfelSimulation(NOISE);
        simulation.advance(0,t->new KipfelSimulation.Input(0,0,1,35,0,0));
        assertEquals(0,simulation.output().deltaHair());
        simulation.advance(30,t->new KipfelSimulation.Input(0,0,1,80,0,0));
        assertEquals(0,simulation.output().deltaHair());
        assertEquals(0,simulation.output().tailAngle());
    }
    @Test void finiteExtremeInputsStayInsideProgramBudget() {
        KipfelSimulation simulation=new KipfelSimulation(NOISE);
        for(int i=0;i<300;i++) simulation.advance(i/60.0,t->new KipfelSimulation.Input(Float.MAX_VALUE,-Float.MAX_VALUE,-1,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE));
        var result=simulation.output();
        assertTrue(Float.isFinite(result.hair()));
        assertTrue(Math.abs(result.deltaHair())<=KipfelSimulation.MAX_EXCITATION);
    }
}
