package com.micaftic.morpher.geckolib3.core.event;

import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;

import java.util.List;

public class InstructionKeyFrameExecutor {

    private final List<EventKeyFrame<IValue[]>> list;

    private int nextIndex = 0;
    private final com.micaftic.morpher.client.animation.molang.AnimationProgramPlan program;
    private final java.util.Set<IValue> migrated;
    public InstructionKeyFrameExecutor(List<EventKeyFrame<IValue[]>> list, com.micaftic.morpher.client.animation.molang.AnimationProgramPlan program) {
        this.list = list; this.program = program;
        var values = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IValue, Boolean>());
        if (program != null) values.addAll(program.statements());
        this.migrated = java.util.Collections.unmodifiableSet(values);
    }
    private boolean runningProgram;
    private void prepare(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        runningProgram = program != null && evaluator.entity().geoInstance().getEvaluationContext().runProgram(program, evaluator.entity());
    }

    public InstructionKeyFrameExecutor(List<EventKeyFrame<IValue[]>> list) {
        this(list, null);
    }

    private void evalValues(ExpressionEvaluator<?> evaluator, IValue[] values) {
        for (IValue value : values) {
            if (!runningProgram || !migrated.contains(value)) value.evalSafe(evaluator);
        }
    }

    public void executeTo(ExpressionEvaluator<AnimationContext<?>> evaluator, float currentTick, boolean isClientSide) {
        prepare(evaluator);
        evaluator.entity().setIsClientSide(isClientSide);
        while (!reachEnd()) {
            EventKeyFrame<IValue[]> keyFrame = this.list.get(this.nextIndex);
            if (keyFrame.getStartTick() > currentTick) {
                break;
            }
            evalValues(evaluator, keyFrame.getEventData());
            this.nextIndex++;
        }
        evaluator.entity().setIsClientSide(false);
    }

    public void executeRemaining(ExpressionEvaluator<AnimationContext<?>> evaluator, boolean isClientSide) {
        prepare(evaluator);
        evaluator.entity().setIsClientSide(isClientSide);
        for (int i = this.nextIndex; i < this.list.size(); i++) {
            evalValues(evaluator, this.list.get(i).getEventData());
        }
        evaluator.entity().setIsClientSide(false);
        this.nextIndex = this.list.size();
    }

    public boolean reachEnd() {
        return this.nextIndex >= this.list.size();
    }

    public void skipBefore(float tick) {
        while (!reachEnd() && this.list.get(this.nextIndex).getStartTick() < tick) this.nextIndex++;
    }

    public void reset() {
        this.nextIndex = 0;
    }
}
