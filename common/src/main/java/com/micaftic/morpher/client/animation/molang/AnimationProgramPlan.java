package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.core.molang.value.MolangValue;
import com.micaftic.morpher.molang.runtime.Variable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, model-local proof and executable statements. No model names or asset fingerprints. */
public record AnimationProgramPlan(String resource, String animationName, double period, List<IValue> statements,
                                   Set<String> reads, Set<String> writes, Set<String> physicsNodes,
                                   Map<Variable, String> inputs, Map<Variable, String> times,
                                   List<MolangValue> poseExpressions, Phase phase, Recovery recovery) {
    public enum Phase { TIMELINE_START }
    /** All scalar outputs are overwritten each step; physics is rebuilt at the current target. */
    public enum Recovery { REBUILD_PHYSICS_AND_OVERWRITE }
    public AnimationProgramPlan {
        if (!Double.isFinite(period) || period <= 0) throw new IllegalArgumentException("period");
        statements = List.copyOf(statements);
        reads = Set.copyOf(reads); writes = Set.copyOf(writes); physicsNodes = Set.copyOf(physicsNodes);
        inputs = Map.copyOf(inputs); times = Map.copyOf(times); poseExpressions = List.copyOf(poseExpressions);
    }
    public record Diagnostic(String resource, double period, Set<String> reads, Set<String> writes,
                             Set<String> physicsNodes, Phase phase, List<String> reasons) {
        public Diagnostic {
            reads = Set.copyOf(reads); writes = Set.copyOf(writes); physicsNodes = Set.copyOf(physicsNodes);
            reasons = List.copyOf(reasons);
        }
        public boolean accepted() { return reasons.isEmpty(); }
    }
}
