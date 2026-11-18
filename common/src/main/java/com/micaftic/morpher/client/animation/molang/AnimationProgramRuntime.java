package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostMolangBindings;
import com.micaftic.morpher.client.core.HostMolangScript;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** Instance-owned writer. Fixed steps publish one complete immutable expression/channel snapshot. */
public final class AnimationProgramRuntime {
    private static final Map<AnimationProgramRuntime, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());

    public record Snapshot(
            long generation,
            long step,
            double time,
            Map<String, MolangValue> variables,
            Map<HostMolangScript, Float> poses,
            boolean recovery) {
        public Snapshot {
            variables = Map.copyOf(variables);
            poses = Map.copyOf(poses);
        }
    }

    public record Step(
            long generation,
            long id,
            double time,
            double dt,
            boolean recovery,
            Map<String, Double> inputs) {
    }

    @FunctionalInterface
    public interface Executor {
        Snapshot execute(Step step);
    }

    private final AnimationProgramPlan plan;
    private final SimulationClock clock;
    private final Map<String, MolangValue> state = new LinkedHashMap<>();
    private final NavigableMap<Double, Map<String, Double>> history = new TreeMap<>();
    private final double historyWindow;
    private volatile @Nullable Snapshot snapshot;
    private long generation;
    private boolean closed;
    private double observed = Double.NEGATIVE_INFINITY;
    private @Nullable HostAnimatable<?> sourceOwner;
    private @Nullable HostActionEvent<?> sourceEvent;
    private @Nullable Object world;
    private double lifeOffset;
    private net.minecraft.world.phys.Vec3 lastPosition;
    private boolean preview;

    public synchronized boolean closed() {
        return this.closed;
    }

    public AnimationProgramRuntime(AnimationProgramPlan plan, long generation) {
        this.plan = plan;
        this.generation = generation;
        this.clock = new SimulationClock(plan.period());
        this.historyWindow = Math.max(.1, plan.period() * 8 + .05);
    }

    public AnimationProgramPlan plan() {
        return this.plan;
    }

    public @Nullable Snapshot snapshot() {
        return this.snapshot;
    }

    /** Game-tick observations use sample-and-hold for at most one tick; never extrapolate an absent history. */
    public synchronized void recordInput(double time, Map<String, Double> values) {
        if (this.closed || !Double.isFinite(time) || !values.keySet().containsAll(this.plan.inputs().keySet())) {
            return;
        }
        this.history.putIfAbsent(time, Map.copyOf(values));
        this.history.headMap(time - this.historyWindow, false).clear();
    }

    private @Nullable Map<String, Double> inputAt(double time) {
        if (this.plan.inputs().isEmpty()) {
            return Map.of();
        }
        var entry = this.history.floorEntry(time + 1e-9);
        return entry != null && time - entry.getKey() <= .05000001 ? entry.getValue() : null;
    }

    public synchronized void advance(double time, long expectedGeneration, Executor executor) {
        if (this.closed || this.generation != expectedGeneration || !Double.isFinite(time) || time < this.observed) {
            return;
        }
        this.observed = time;
        if (this.snapshot != null && time > this.snapshot.time()) {
            for (int offset = 1; offset <= 8; offset++) {
                double next = this.snapshot.time() + this.plan.period() * offset;
                if (next > time + 1e-9) {
                    break;
                }
                if (inputAt(next) == null) {
                    this.clock.recoverOnNextAdvance();
                    break;
                }
            }
        }
        this.clock.advanceFrame(time, (id, acceptedTime, dt, recovery) -> {
            if (this.closed || this.generation != expectedGeneration) {
                return;
            }
            Map<String, Double> inputs = inputAt(acceptedTime);
            if (inputs == null) {
                this.clock.recoverOnNextAdvance();
                return;
            }
            Snapshot next;
            try {
                next = executor.execute(new Step(expectedGeneration, id, acceptedTime, dt, recovery, inputs));
            } catch (RuntimeException exception) {
                this.clock.recoverOnNextAdvance();
                YesSteveModel.LOGGER.warn("[SM-PROGRAM] Holding snapshot after failed step: {}", this.plan.resource(), exception);
                return;
            }
            if (!this.closed && this.generation == expectedGeneration && next != null && next.generation() == this.generation) {
                this.snapshot = next;
            }
        });
    }

    /** First called exactly where the original timeline starts, after controller activation. */
    public synchronized void update(
            HostAnimatable<?> owner,
            HostActionEvent<?> event,
            long expectedGeneration,
            Map<String, MolangValue> adhocVariables) {
        if (this.closed || this.generation != expectedGeneration) {
            return;
        }
        double time = ((double) event.getTickCount() + event.getPartialTick()) / 20.0;
        if (this.sourceOwner == null) {
            this.sourceOwner = owner;
            this.sourceEvent = event;
            this.world = owner.entity.level();
            this.preview = owner instanceof com.micaftic.morpher.client.entity.IPreviewAnimatable;
            this.lastPosition = owner.entity.position();
            this.lifeOffset = owner.seekTime / 20.0 - time;
            LIVE.put(this, Boolean.TRUE);
        }
        if (this.world != owner.entity.level()) {
            this.world = owner.entity.level();
            this.history.clear();
            this.clock.recoverOnNextAdvance();
        }
        if (time < this.observed) {
            publish(adhocVariables);
            return;
        }
        this.sourceOwner = owner;
        this.sourceEvent = event;
        var position = owner.entity.position();
        if (this.lastPosition != null && position.distanceToSqr(this.lastPosition) > 64) {
            this.history.clear();
            this.clock.recoverOnNextAdvance();
        }
        this.lastPosition = position;
        captureInputs(Math.floor(time * 20 + 1e-7) / 20);
        advance(time, expectedGeneration, this::evaluate);
        publish(adhocVariables);
    }

    public synchronized void publish(Map<String, MolangValue> adhocVariables) {
        Snapshot ready = this.snapshot;
        if (this.closed || ready == null || ready.generation() != this.generation) {
            return;
        }
        ready.variables().forEach(adhocVariables::put);
    }

    private void captureInputs(double time) {
        if (this.sourceEvent == null || this.plan.inputs().isEmpty() || this.history.containsKey(time)) {
            return;
        }
        SceneInputs base = HostMolangBindings.buildInputs(this.sourceEvent, Map.of(), false, Map.of());
        Map<String, Double> values = new HashMap<>();
        for (String key : this.plan.inputs().keySet()) {
            String normalized = normalizeQueryKey(key);
            Double qVal = base.queries().get(normalized);
            if (qVal != null && Double.isFinite(qVal)) {
                values.put(key, qVal);
            }
        }
        recordInput(time, values);
    }

    private static String normalizeQueryKey(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("query.")) return lower.substring(6);
        if (lower.startsWith("q.")) return lower.substring(2);
        return lower;
    }

    private @Nullable Snapshot evaluate(Step step) {
        if (step.recovery()) {
            this.state.clear();
        }
        Map<String, Double> queries = new HashMap<>();
        step.inputs().forEach((key, value) -> queries.put(normalizeQueryKey(key), value));
        this.plan.times().forEach((variable, domain) -> {
            double t = "anim_time".equals(domain) ? 0.0 : step.time() + this.lifeOffset;
            queries.put(normalizeQueryKey(variable), t);
            queries.put(domain, t);
        });
        SceneInputs inputs = new SceneInputs(queries, dev.spm.core.scene.MolangBindings.empty(), false, SceneInputs.SyncMode.NONE);
        MolangContext context = new MolangContext(
                inputs,
                this.state,
                Map.of(),
                new ScriptBudget(2_000_000, 64, new CancellationToken()));

        long health = EvaluationHealth.sequence();
        for (HostMolangScript statement : this.plan.statements()) {
            statement.evaluateValue(context);
        }
        Map<String, MolangValue> written = context.typedVariables();
        this.state.clear();
        this.state.putAll(written);

        Map<String, MolangValue> variables = new LinkedHashMap<>();
        for (String name : this.plan.writes()) {
            MolangValue value = written.get(name);
            if (value == null) {
                value = MolangValue.number(0.0);
            }
            if (!(value instanceof MolangValue.TextValue) && !(value instanceof MolangValue.BooleanValue)
                    && !Double.isFinite(value.number())) {
                this.clock.recoverOnNextAdvance();
                return null;
            }
            variables.put(name, value);
        }
        Map<HostMolangScript, Float> poses = new IdentityHashMap<>();
        for (HostMolangScript expression : this.plan.poseExpressions()) {
            float value = expression.evaluateAsFloat(context);
            if (!Float.isFinite(value)) {
                this.clock.recoverOnNextAdvance();
                return null;
            }
            poses.put(expression, value);
        }
        if (health != EvaluationHealth.sequence()) {
            this.clock.recoverOnNextAdvance();
            return null;
        }
        if (Boolean.getBoolean("sparkle_morpher.physicsTrace") && (step.recovery() || step.id() % 60 == 0)) {
            YesSteveModel.LOGGER.info(
                    "[SM-PROGRAM-STEP] program={} generation={} step={} t={} dt={} recovery={} values={}",
                    this.plan.resource(), this.generation, step.id(), step.time(), step.dt(), step.recovery(), variables);
        }
        return new Snapshot(this.generation, step.id(), step.time(), variables, poses, step.recovery());
    }

    public synchronized void close() {
        this.closed = true;
        this.generation++;
        this.snapshot = null;
        this.sourceOwner = null;
        this.sourceEvent = null;
        this.state.clear();
        this.history.clear();
        LIVE.remove(this);
    }

    /** Called once per client tick. Off-screen instances retain their declared lifecycle. */
    public static void tickInputs() {
        List<AnimationProgramRuntime> instances;
        synchronized (LIVE) {
            instances = new ArrayList<>(LIVE.keySet());
        }
        for (AnimationProgramRuntime runtime : instances) {
            runtime.tickInput();
        }
    }

    private synchronized void tickInput() {
        if (this.closed || this.sourceOwner == null || this.sourceEvent == null) {
            return;
        }
        if (this.sourceOwner.entity.isRemoved() || this.sourceOwner.entity.level() != this.world) {
            close();
            return;
        }
        double time = (this.preview
                ? com.micaftic.morpher.client.event.ClientTickEvent.getTickCount()
                : this.sourceOwner.entity.tickCount) / 20.0;
        var position = this.sourceOwner.entity.position();
        if (this.lastPosition != null && position.distanceToSqr(this.lastPosition) > 64) {
            this.history.clear();
            this.clock.recoverOnNextAdvance();
        }
        this.lastPosition = position;
        captureInputs(time);
        advance(time, this.generation, this::evaluate);
    }
}
