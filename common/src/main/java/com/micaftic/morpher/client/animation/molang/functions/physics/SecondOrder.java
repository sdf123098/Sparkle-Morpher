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

    public SecondOrder(float input, float frequency, float coefficient, float response) {
        this.input = input;
        this.frequency = Mth.clamp(frequency, 0, 5);
        this.coefficient = Mth.clamp(coefficient, 0, 1);
        this.response = response;
    }

    @Override
    public void update(float timeStep) {
        float input = finiteOrZero(this.input);
        if (!Float.isFinite(timeStep) || timeStep <= 0.0f) {
            this.inputFunction = input;
            sanitizeSimulation(input);
            return;
        }

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