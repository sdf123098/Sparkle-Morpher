package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.core.HostMolangScript;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, model-local proof and executable statements. No model names or asset fingerprints. */
public record AnimationProgramPlan(
        String resource,
        String animationName,
        double period,
        List<HostMolangScript> statements,
        Set<String> reads,
        Set<String> writes,
        Set<String> physicsNodes,
        Map<String, String> inputs,
        Map<String, String> times,
        List<HostMolangScript> poseExpressions,
        Phase phase,
        Recovery recovery) {

    public enum Phase { TIMELINE_START }

    /** All scalar outputs are overwritten each step; physics is rebuilt at the current target. */
    public enum Recovery { REBUILD_PHYSICS_AND_OVERWRITE }

    public AnimationProgramPlan {
        if (!Double.isFinite(period) || period <= 0) {
            throw new IllegalArgumentException("period");
        }
        statements = List.copyOf(statements);
        reads = Set.copyOf(reads);
        writes = Set.copyOf(writes);
        physicsNodes = Set.copyOf(physicsNodes);
        inputs = Map.copyOf(inputs);
        times = Map.copyOf(times);
        poseExpressions = List.copyOf(poseExpressions);
    }

    public record Diagnostic(
            String resource,
            double period,
            Set<String> reads,
            Set<String> writes,
            Set<String> physicsNodes,
            Phase phase,
            List<String> reasons) {
        public Diagnostic {
            reads = Set.copyOf(reads);
            writes = Set.copyOf(writes);
            physicsNodes = Set.copyOf(physicsNodes);
            reasons = List.copyOf(reasons);
        }

        public boolean accepted() {
            return reasons.isEmpty();
        }
    }
}
