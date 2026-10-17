package com.micaftic.morpher.client.animation.molang.functions.physics;

import net.minecraft.util.Mth;

/**
 * @author MicroCraft
 *
 * <a href="https://www.youtube.com/watch?v=KPoeNZZ6H4s">Giving Personality to Procedural Animations using Math</a>
 */
public class SecondOrder implements IPhysics {

    private static final float MIN_FREQUENCY = 0.001f;
    private static final float MAX_TIME_STEP = 0.1f;
    private static final int MAX_CYCLES = 16;

    private float inputFunction = 0.0f;
    private float lastSimulation = 0.0f;
    private float lastSimulationDot = 0.0f;
    private float input;
    private float frequency;
    private float coefficient;
    private float response;
    private final boolean analytic;
    private double preciseValue;
    private double preciseVelocity;

    public SecondOrder(float input, float frequency, float coefficient, float response) {
        this(input, frequency, coefficient, response, false);
    }

    public SecondOrder(float input, float frequency, float coefficient, float response, boolean analytic) {
        this.analytic = analytic;
        this.input = input;
        this.frequency = frequency;
        this.coefficient = coefficient;
        this.response = response;
        snapToInput(finiteOrZero(input));
    }

    @Override
    public void update(float timeStep) {
        if (!Float.isFinite(this.input) || !Float.isFinite(frequency) || !Float.isFinite(coefficient) || !Float.isFinite(response)) return;
        float input = this.input;
        if (!Float.isFinite(timeStep) || timeStep <= 0.0f) {
            return;
        }
        if (analytic) { updateAnalytic(timeStep); return; }

        timeStep = Math.min(timeStep, MAX_TIME_STEP);
        float frequency = Mth.clamp(finiteOrZero(this.frequency), MIN_FREQUENCY, 5.0f);
        float coefficient = Mth.clamp(finiteOrZero(this.coefficient), 0.0f, 1.0f);
        float response = finiteOrZero(this.response);

        float k1 = coefficient / Mth.PI / frequency;
        float k2 = 1.0f / (2.0f * Mth.PI * frequency) / (2.0f * Mth.PI * frequency);
        float k3 = response * coefficient / 2.0f / Mth.PI / frequency;
        if (!Float.isFinite(k1) || !Float.isFinite(k2) || k2 <= 0.0f || !Float.isFinite(k3)) {
            snapToInput(input);
            return;
        }

        float inputFunctionDot = (input - this.inputFunction) / timeStep;
        if (!Float.isFinite(inputFunctionDot)) {
            inputFunctionDot = 0.0f;
        }
        this.inputFunction = input;

        float maxTimeStep = (float) Math.sqrt(4.0f * k2 + k1 * k1) - k1;
        if (!Float.isFinite(maxTimeStep) || maxTimeStep <= 0.0f) {
            maxTimeStep = timeStep;
        }
        int cycleTime = Mth.clamp((int) Math.ceil(timeStep / maxTimeStep), 1, MAX_CYCLES);
        timeStep = timeStep / cycleTime;

        float lastSimulationDot = Float.isFinite(this.lastSimulationDot) ? this.lastSimulationDot : 0.0f;
        float lastSimulation = Float.isFinite(this.lastSimulation) ? this.lastSimulation : input;
        for (; cycleTime > 0; cycleTime--) {
            lastSimulation = lastSimulation + timeStep * lastSimulationDot;
            lastSimulationDot = lastSimulationDot + timeStep * (k3 * inputFunctionDot + input - lastSimulation - k1 * lastSimulationDot) / k2;
            if (!Float.isFinite(lastSimulation) || !Float.isFinite(lastSimulationDot)) {
                snapToInput(input);
                return;
            }
        }
        this.lastSimulation = lastSimulation;
        this.lastSimulationDot = lastSimulationDot;
    }

    private void sanitizeSimulation(float input) {
        if (!Float.isFinite(this.lastSimulation) || !Float.isFinite(this.lastSimulationDot)) {
            snapToInput(input);
        }
    }

    private void snapToInput(float input) {
        this.inputFunction = input;
        this.lastSimulation = input;
        this.lastSimulationDot = 0.0f;
        this.preciseValue = input;
        this.preciseVelocity = 0;
    }

    /** Exact linear-ramp forcing for y'' + 2*z*w*y' + w*w*y = w*w*x + r*z*w*x'. */
    private void updateAnalytic(double dt) {
        if (!Float.isFinite(frequency) || !Float.isFinite(coefficient) || !Float.isFinite(response)) return;
        if (frequency <= 0) { snapToInput(input); return; }
        if (coefficient < 0) return;
        double w = 2 * Math.PI * frequency, a = coefficient * w, b = w * w;
        double velocity = ((double) input - inputFunction) / dt;
        double offset = (response - 2.0) * coefficient / w * velocity;
        double u = preciseValue - inputFunction - offset, v = preciseVelocity - velocity;
        double nextU, nextV;
        if (coefficient < 1) {
            double wd = w * Math.sqrt(1.0 - (double) coefficient * coefficient);
            double e = Math.exp(-a * dt), c = Math.cos(wd * dt), s = Math.sin(wd * dt) / wd;
            nextU = e * (u * c + (v + a * u) * s);
            nextV = e * (v * c - (a * v + b * u) * s);
        } else if (coefficient == 1) {
            double e = Math.exp(-a * dt), c = v + a * u;
            nextU = e * (u + c * dt);
            nextV = e * (v - a * c * dt);
        } else {
            double d = Math.sqrt(a * a - b), slow = -b / (a + d), fast = -a - d;
            double c = (v - fast * u) / (slow - fast);
            double slowTerm = c * Math.exp(slow * dt), fastTerm = (u - c) * Math.exp(fast * dt);
            nextU = slowTerm + fastTerm;
            nextV = slow * slowTerm + fast * fastTerm;
        }
        double y = input + offset + nextU, yd = velocity + nextV;
        if (Float.isFinite((float)y) && Float.isFinite((float)yd)) {
            preciseValue = y; preciseVelocity = yd;
            lastSimulation = (float)y; lastSimulationDot = (float)yd;
            inputFunction = input;
        }
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    @Override
    public void setArgs(float input, float frequency, float coefficient, float response) {
        this.input = input;
        this.frequency = frequency;
        this.coefficient = coefficient;
        this.response = response;
    }

    @Override
    public float getValue() {
        return this.lastSimulation;
    }
}
