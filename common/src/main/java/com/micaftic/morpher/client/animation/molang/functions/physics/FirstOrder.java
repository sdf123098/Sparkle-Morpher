package com.micaftic.morpher.client.animation.molang.functions.physics;

public class FirstOrder implements IPhysics {

    private float input;

    private float response;

    private float lastSimulation = 0.0f;

    public FirstOrder(float input, float response) {
        this.input = input;
        this.response = response;
    }

    @Override
    public void update(float timeStep) {
        float input = finiteOrZero(this.input);
        float response = finiteOrZero(this.response);
        if (!Float.isFinite(timeStep) || timeStep <= 0.0f || response <= 0.0f) {
            this.lastSimulation = input;
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