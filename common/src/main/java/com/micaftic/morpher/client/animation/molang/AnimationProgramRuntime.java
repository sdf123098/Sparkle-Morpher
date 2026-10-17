package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.molang.storage.VariableStorage;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.MolangValue;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Variable;
import java.util.*;

/** Instance-owned writer. Fixed steps publish one complete immutable expression/channel snapshot. */
public final class AnimationProgramRuntime {
    private static final Map<AnimationProgramRuntime, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());
    public record Snapshot(long generation, long step, double time, Map<String, Object> variables,
                           Map<MolangValue, Float> poses, boolean recovery) {
        public Snapshot { variables = Map.copyOf(variables); poses = Map.copyOf(poses); }
    }
    public record Step(long generation, long id, double time, double dt, boolean recovery, Map<Variable, Object> inputs) { }
    @FunctionalInterface public interface Executor { Snapshot execute(Step step); }
    private final AnimationProgramPlan plan;
    private final SimulationClock clock;
    private final PhysicsManager physics = new PhysicsManager();
    private final VariableStorage state = new VariableStorage();
    private final NavigableMap<Double, Map<Variable, Object>> history = new TreeMap<>();
    private final double historyWindow;
    private volatile Snapshot snapshot;
    private long generation;
    private boolean closed;
    private double observed = Double.NEGATIVE_INFINITY;
    private AnimationContext<?> source;
    private Object world;
    private double lifeOffset;
    private net.minecraft.world.phys.Vec3 lastPosition;
    private boolean preview;
    public synchronized boolean closed() { return closed; }

    public AnimationProgramRuntime(AnimationProgramPlan plan, long generation) {
        this.plan = plan; this.generation = generation; clock = new SimulationClock(plan.period());
        historyWindow = Math.max(.1, plan.period() * 8 + .05);
    }
    public AnimationProgramPlan plan() { return plan; }
    public Snapshot snapshot() { return snapshot; }

    /** Game-tick observations use sample-and-hold for at most one tick; never extrapolate an absent history. */
    public synchronized void recordInput(double time, Map<Variable, Object> values) {
        if (closed || !Double.isFinite(time) || !values.keySet().containsAll(plan.inputs().keySet())) return;
        history.putIfAbsent(time, Map.copyOf(values));
        history.headMap(time - historyWindow, false).clear();
    }
    private Map<Variable, Object> inputAt(double time) {
        if (plan.inputs().isEmpty()) return Map.of();
        var entry = history.floorEntry(time + 1e-9);
        return entry != null && time - entry.getKey() <= .05000001 ? entry.getValue() : null;
    }
    public synchronized void advance(double time, long expectedGeneration, Executor executor) {
        if (closed || generation != expectedGeneration || !Double.isFinite(time) || time < observed) return;
        observed = time;
        // Check the entire potential catch-up window before mutating anything. Missing history recovers now.
        if (snapshot != null && time > snapshot.time()) {
            for (int offset = 1; offset <= 8; offset++) {
                double next = snapshot.time() + plan.period() * offset;
                if (next > time + 1e-9) break;
                if (inputAt(next) == null) { clock.recoverOnNextAdvance(); break; }
            }
        }
        clock.advanceFrame(time, (id, acceptedTime, dt, recovery) -> {
            if (closed || generation != expectedGeneration) return;
            Map<Variable, Object> inputs = inputAt(acceptedTime);
            if (inputs == null) { clock.recoverOnNextAdvance(); return; }
            Snapshot next;
            try { next = executor.execute(new Step(expectedGeneration, id, acceptedTime, dt, recovery, inputs)); }
            catch (RuntimeException exception) {
                clock.recoverOnNextAdvance();
                YesSteveModel.LOGGER.warn("[SM-PROGRAM] Holding snapshot after failed step: {}", plan.resource(), exception);
                return;
            }
            if (!closed && generation == expectedGeneration && next != null && next.generation() == generation) snapshot = next;
        });
    }

    /** First called exactly where the original timeline starts, after controller activation. */
    public synchronized void update(AnimationContext<?> context, long expectedGeneration) {
        if (closed || generation != expectedGeneration) return;
        double time = ((double)context.animationEvent.getTickCount() + context.animationEvent.getPartialTick()) / 20;
        if (source == null) {
            source = context; world = context.geoInstance().entity.level();
            preview = context.instance instanceof com.micaftic.morpher.client.entity.IPreviewAnimatable;
            lastPosition = context.geoInstance().entity.position();
            lifeOffset = context.geoInstance().getSeekTime() / 20.0 - time;
            LIVE.put(this, Boolean.TRUE);
        }
        if (world != context.geoInstance().entity.level()) {
            world = context.geoInstance().entity.level(); history.clear(); clock.recoverOnNextAdvance();
        }
        if (time < observed) { publish(context); return; }
        source = context;
        var position = context.geoInstance().entity.position();
        if (lastPosition != null && position.distanceToSqr(lastPosition) > 64) {
            history.clear(); clock.recoverOnNextAdvance();
        }
        lastPosition = position;
        captureInputs(Math.floor(time * 20 + 1e-7) / 20);
        advance(time, expectedGeneration, this::evaluate);
        publish(context);
    }
    public synchronized void publish(AnimationContext<?> context) {
        Snapshot ready = snapshot;
        if (closed || ready == null || ready.generation() != generation) return;
        ready.variables().forEach((name, value) -> context.storage.setScoped(StringPool.computeIfAbsent(name), value));
        Map<MolangValue, Float> poses = new IdentityHashMap<>(context.programPoseValues);
        poses.putAll(ready.poses()); context.programPoseValues = Map.copyOf(poses);
    }
    private void captureInputs(double time) {
        if (source == null || plan.inputs().isEmpty() || history.containsKey(time)) return;
        Map<Variable, Object> values = new HashMap<>();
        var evaluator = ExpressionEvaluator.evaluator(source);
        for (Variable variable : plan.inputs().keySet()) {
            Object value = variable.evaluate(evaluator);
            if (value instanceof Number number && !Float.isFinite(number.floatValue())) return;
            if (value != null) values.put(variable, value);
        }
        recordInput(time, values);
    }
    private Snapshot evaluate(Step step) {
        if (step.recovery()) { physics.clear(); state.initialize(null); }
        physics.updateSimulation(step.time(), step.recovery());
        AnimationContext<?> context = new AnimationContext<>(source.entity, source.instance, source.animationEvent, source.data);
        context.setStorage(state); context.programPhysics = physics;
        var evaluator = ExpressionEvaluator.evaluator(context);
        Map<Variable, Object> values = new HashMap<>(step.inputs());
        plan.times().forEach((variable, domain) -> values.put(variable, domain.equals("anim_time") ? 0f : step.time() + lifeOffset));
        evaluator.setVariableOverrides(values);
        long health = EvaluationHealth.sequence();
        for (var statement : plan.statements()) statement.evalSafe(evaluator);
        Map<String, Object> variables = new LinkedHashMap<>();
        for (String name : plan.writes()) {
            Object value = state.getScoped(StringPool.computeIfAbsent(name));
            if (value instanceof Number number) {
                if (!Double.isFinite(number.doubleValue()) || !Float.isFinite(number.floatValue())) { clock.recoverOnNextAdvance(); return null; }
            } else if (!(value instanceof Boolean) && !(value instanceof String)
                    && !(value instanceof com.micaftic.morpher.molang.parser.ast.StringExpression)) {
                clock.recoverOnNextAdvance(); return null;
            }
            variables.put(name, value);
        }
        Map<MolangValue, Float> poses = new IdentityHashMap<>();
        for (MolangValue expression : plan.poseExpressions()) {
            float value = expression.evalAsFloat(evaluator);
            if (!Float.isFinite(value)) { clock.recoverOnNextAdvance(); return null; }
            poses.put(expression, value);
        }
        if (health != EvaluationHealth.sequence()) { clock.recoverOnNextAdvance(); return null; }
        if (Boolean.getBoolean("sparkle_morpher.physicsTrace") && (step.recovery() || step.id() % 60 == 0))
            YesSteveModel.LOGGER.info("[SM-PROGRAM-STEP] program={} generation={} step={} t={} dt={} recovery={} values={}",
                    plan.resource(), generation, step.id(), step.time(), step.dt(), step.recovery(), variables);
        return new Snapshot(generation, step.id(), step.time(), variables, poses, step.recovery());
    }
    public synchronized void close() {
        closed = true; generation++; snapshot = null; source = null; history.clear(); physics.clear(); state.initialize(null); LIVE.remove(this);
    }
    /** Called once per client tick. Off-screen instances retain their declared lifecycle. */
    public static void tickInputs() {
        List<AnimationProgramRuntime> instances;
        synchronized (LIVE) { instances = new ArrayList<>(LIVE.keySet()); }
        for (AnimationProgramRuntime runtime : instances) runtime.tickInput();
    }
    private synchronized void tickInput() {
        if (closed || source == null) return;
        if (source.geoInstance().entity.isRemoved() || source.geoInstance().entity.level() != world) { close(); return; }
        double time = (preview ? com.micaftic.morpher.client.event.ClientTickEvent.getTickCount() : source.geoInstance().entity.tickCount) / 20.0;
        var position = source.geoInstance().entity.position();
        if (lastPosition != null && position.distanceToSqr(lastPosition) > 64) { history.clear(); clock.recoverOnNextAdvance(); }
        lastPosition = position;
        captureInputs(time);
        advance(time, generation, this::evaluate);
    }
}
