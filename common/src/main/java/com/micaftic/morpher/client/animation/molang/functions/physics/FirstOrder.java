package com.micaftic.morpher.client.animation.molang.functions.physics;

public class FirstOrder implements IPhysics {

    private float input;

    private float response;

    private float lastSimulation = 0.0f;
    private final boolean analytic;
    private double preciseValue;

    public FirstOrder(float input, float response) {
        this(input, response, false);
    }

    public FirstOrder(float input, float response, boolean analytic) {
        this.analytic = analytic;
        this.input = input;
        this.response = response;
        this.lastSimulation = finiteOrZero(input);
        this.preciseValue = this.lastSimulation;
    }

    @Override
    public void update(float timeStep) {
        if (!Float.isFinite(this.input) || !Float.isFinite(this.response) || !Float.isFinite(timeStep) || timeStep <= 0.0f) return;
        float input = this.input;
        float response = finiteOrZero(this.response);
        if (response <= 0.0f) {
            this.lastSimulation = input;
            this.preciseValue = input;
            return;
        }
        if (analytic) {
            double next = preciseValue + -Math.expm1(-(double) timeStep / response) * ((double) input - preciseValue);
            if (Float.isFinite((float) next)) {
                preciseValue = next;
                lastSimulation = (float) next;
            }
            return;
        }
        // 步长钳制到 response：离屏/跳帧后 seekTime 大步长会让 (1 - step/response) 变负 → 过冲震荡（头发/尾巴乱飘）
        float step = Math.min(timeStep, response);
        this.lastSimulation = ((1 - (step / response)) * this.lastSimulation) + ((step / response) * input);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }



    @Override
    public void setArgs(float input, float response, float arg2, float arg3) {
        this.input = input;
        this.response = response;
    }

    @Override
    public float getValue() {
        return this.lastSimulation;
    }
}
