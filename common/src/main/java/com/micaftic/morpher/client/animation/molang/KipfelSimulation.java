package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.animation.molang.functions.physics.FirstOrder;
import com.micaftic.morpher.client.animation.molang.functions.physics.SecondOrder;
import java.util.function.DoubleFunction;

/** Compatibility program for the fingerprinted Kipfel attachment graph, in degrees/seconds. */
public final class KipfelSimulation {
    public static final float MAX_EXCITATION = 60;
    public static final float MAX_OFFSET = 180;
    public static final float MAX_ANGULAR_SPEED = 720;
    @FunctionalInterface public interface Noise { float sample(int seed, float x, float y); }
    public record Input(float groundSpeed, float verticalSpeed, float forward, float pitch, float yawSpeed, float bv) {
        boolean finite() {
            return Float.isFinite(groundSpeed) && Float.isFinite(verticalSpeed) && Float.isFinite(forward)
                    && Float.isFinite(pitch) && Float.isFinite(yawSpeed) && Float.isFinite(bv);
        }
    }
    public record Output(long stepId, double time, boolean recovery, float target, float hair, float yaw, float pitch,
                         float deltaHair, float delta, float frontNoise, float tailResponse, float tailAngle) { }
    private final SimulationClock clock = new SimulationClock();
    private final Noise noise;
    private final int seed;
    private FirstOrder speed, tailNoise;
    private SecondOrder hair, yaw, pitch, delta;
    private Output output;
    private float lastHair;
    private double tailAngle;

    public KipfelSimulation(Noise noise) { this(noise, 14651); }
    public KipfelSimulation(Noise noise, int seed) { this.noise = noise; this.seed = seed; }

    public void advance(double time, DoubleFunction<Input> input) {
        advance(time,input,o->{});
    }
    public void advance(double time, DoubleFunction<Input> input, java.util.function.Consumer<Output> snapshots) {
        clock.advanceFrame(time, (id,t,dt,recovery)->{
            step(id,t,dt,recovery,input.apply(t));
            if(output != null && output.stepId == id) snapshots.accept(output);
        });
    }
    public Output output() { return output; }
    public void recoverOnNextAdvance() { clock.recoverOnNextAdvance(); }
    public float interpolation() { return clock.interpolation(); }

    /** Blend the two directional targets continuously, including the atan2(0,0) singularity. */
    public static float targetAngle(Input input) {
        double ground = Math.max(0, input.groundSpeed), vertical = input.verticalSpeed;
        double correction = vertical < 0 && vertical > -7.5
                ? Math.exp(-2 * ground) * (-3 * vertical * (vertical + 7.5)) : 0;
        double angle = 130 * Math.atan2(ground + correction, vertical + 3.5) / Math.PI;
        double forward = Math.max(-1, Math.min(1, input.forward));
        return (float)(angle * forward);
    }

    private void step(long id, double time, double dt, boolean recovery, Input input) {
        if (input == null || !input.finite()) return;
        float ground = Math.max(0, input.groundSpeed);
        float front = noise.sample(seed, (float)(time * .3), 0)
                + noise.sample(seed + 1, (float)(time * .3), (float)(time * .3));
        float tail = Math.abs(noise.sample(seed + 2, (float)(time * .25), 0)
                + noise.sample(seed + 3, (float)(time * .25), (float)(time * .25)));
        if (!Float.isFinite(front) || !Float.isFinite(tail)) return;
        if (speed == null || recovery) {
            speed = new FirstOrder(ground, .1f, true);
            tailNoise = new FirstOrder(tail, 1, true);
        } else {
            speed.setArgs(ground, .1f, 0, 0); speed.update((float)dt);
            tailNoise.setArgs(tail, 1, 0, 0); tailNoise.update((float)dt);
        }
        float hairNoise = (float)(20 * noise.sample(seed + 4, (float)(5 * time), 0)
                * (1 - Math.exp(-.2 * Math.pow(speed.getValue(), 3))));
        if (!Float.isFinite(hairNoise)) return;
        float hairTarget = clamp(Math.max(-10, targetAngle(input) + hairNoise + clamp(input.bv,180))
                + clamp(input.pitch,90), MAX_OFFSET);
        float yawTarget = clamp(.03f * input.yawSpeed, 60), pitchTarget = clamp(input.pitch,90);
        float excitation;
        if (hair == null || recovery) {
            hair = new SecondOrder(hairTarget,.7f,.8f,1,true);
            yaw = new SecondOrder(yawTarget,.8f,.4f,1.2f,true);
            pitch = new SecondOrder(pitchTarget,.8f,.8f,1,true);
            delta = new SecondOrder(0,1,.8f,1,true);
            lastHair = hairTarget;
            excitation = 0;
        } else {
            hair.setArgs(hairTarget,.7f,.8f,1); hair.update((float)dt);
            yaw.setArgs(yawTarget,.8f,.4f,1.2f); yaw.update((float)dt);
            pitch.setArgs(pitchTarget,.8f,.8f,1); pitch.update((float)dt);
            float currentHair = limited(hair.getValue(),lastHair,MAX_OFFSET,dt);
            excitation = clamp((float)(-8 * (currentHair - lastHair) / (60 * dt)),MAX_EXCITATION);
            lastHair = currentHair;
            delta.setArgs(excitation,1,.8f,1); delta.update((float)dt);
        }
        float tailResponse = tailNoise.getValue() + .2f;
        tailAngle = (tailAngle + dt * tailResponse * 360) % 360;
        output = new Output(id,time,recovery,hairTarget,lastHair,
                limited(yaw.getValue(),output == null ? yawTarget : output.yaw,60,dt),
                limited(pitch.getValue(),output == null ? pitchTarget : output.pitch,90,dt),
                excitation,clamp(delta.getValue(),MAX_EXCITATION),front,tailResponse,(float)tailAngle);
    }

    private static float clamp(float value, float max) { return Math.max(-max, Math.min(max,value)); }
    private static float limited(float value,float previous,float max,double dt) {
        float bounded = clamp(value,max);
        return dt == 0 ? bounded : previous + clamp(bounded - previous,(float)(MAX_ANGULAR_SPEED * dt));
    }
}
