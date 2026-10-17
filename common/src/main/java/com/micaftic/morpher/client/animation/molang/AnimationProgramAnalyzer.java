package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.molang.functions.ysm.FirstOrderFunction;
import com.micaftic.morpher.client.animation.molang.functions.ysm.SecondOrderFunction;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.molang.builtin.MathBinding;
import com.micaftic.morpher.geckolib3.core.molang.builtin.QueryBinding;
import com.micaftic.morpher.geckolib3.core.molang.binding.variable.ScopedVariableBinding;
import com.micaftic.morpher.geckolib3.core.molang.value.*;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.geckolib3.file.AnimationControllerFile;
import com.micaftic.morpher.molang.parser.ast.*;
import com.micaftic.morpher.molang.runtime.Variable;
import java.util.*;

/** Fail-closed analysis of the actual bound AST; never evaluates a model expression. */
public final class AnimationProgramAnalyzer {
    private AnimationProgramAnalyzer() { }
    private static final Set<String> PURE_MATH = Set.of("floor", "round", "ceil", "trunc", "clamp", "max", "min",
            "abs", "exp", "ln", "sqrt", "mod", "pow", "sin", "cos", "acos", "asin", "atan", "atan2",
            "lerp", "lerprotate", "hermite_blend", "min_angle");
    // These bindings read entity state directly, with no render/position-tracker dependency.
    private static final Set<String> INPUTS = Set.of("is_on_ground", "is_in_water", "is_on_fire", "is_sprinting",
            "is_swimming", "is_spectator", "is_riding", "is_sneaking", "health", "max_health", "hurt_time");
    public record Facts(Set<String> reads, Set<String> writes, Set<String> physicsNodes,
                        Map<Variable, String> inputs, Map<Variable, String> times, List<String> reasons,
                        boolean unknownEffect) {
        public Facts {
            reads = Set.copyOf(reads); writes = Set.copyOf(writes); physicsNodes = Set.copyOf(physicsNodes);
            inputs = Map.copyOf(inputs); times = Map.copyOf(times); reasons = List.copyOf(reasons);
        }
    }
    private static final class Scan {
        final Set<String> reads = new LinkedHashSet<>(), writes = new LinkedHashSet<>(), nodes = new LinkedHashSet<>();
        final Map<Variable, String> inputs = new LinkedHashMap<>(), times = new LinkedHashMap<>();
        final List<String> reasons = new ArrayList<>();
        boolean unknown;
        void reject(String reason) { if (!reasons.contains(reason)) reasons.add(reason); }
        Facts result() { return new Facts(reads, writes, nodes, inputs, times, reasons, unknown); }
    }
    private record Site(String resource, Animation animation, boolean timeline, boolean control, IValue value, Facts facts) { }

    public static Facts inspect(IValue value) {
        Scan scan = new Scan();
        if (value instanceof MolangValue molang) {
            if (!molang.parsedSuccessfully()) {
                scan.unknown = true; scan.reject("parse failure: " + molang.failure());
            } else for (Expression expression : molang.expressions()) walk(expression, scan, false);
        } else if (!(value instanceof FloatValue)) {
            scan.unknown = true; scan.reject("unknown value binding");
        }
        return scan.result();
    }

    private static String scoped(AssignableVariableExpression expression) {
        String name = expression.symbol().toLowerCase(Locale.ROOT);
        if ((name.startsWith("v.") || name.startsWith("variable."))
                && expression.target() instanceof ScopedVariableBinding.ScopedVariable variable) {
            String suffix = name.substring(name.indexOf('.') + 1);
            if (suffix.equals(com.micaftic.morpher.geckolib3.core.molang.util.StringPool.getString(variable.name())))
                return suffix;
        }
        return null;
    }

    private static void walk(Expression expression, Scan scan, boolean writing) {
        if (expression instanceof FloatExpression || expression instanceof StringExpression) return;
        if (expression instanceof AssignableVariableExpression variable) {
            String name = scoped(variable);
            if (name == null) {
                scan.reject("unsupported variable scope: " + variable.symbol());
                if (writing) scan.unknown = true;
            } else (writing ? scan.writes : scan.reads).add(name);
        } else if (expression instanceof VariableExpression variable) {
            String symbol = variable.symbol().toLowerCase(Locale.ROOT).replaceFirst("^q\\.", "query.");
            String name = symbol.startsWith("query.") ? symbol.substring(6) : "";
            if (!name.isEmpty() && QueryBinding.INSTANCE.getProperty(name) == variable.target()) {
                if (name.equals("life_time") || name.equals("anim_time")) scan.times.put(variable.target(), name);
                else if (INPUTS.contains(name)) scan.inputs.put(variable.target(), name);
                else scan.reject("input history unavailable: " + symbol);
            } else { scan.unknown = true; scan.reject("unknown input binding: " + symbol); }
        } else if (expression instanceof BinaryExpression binary) {
            if (binary.op() == BinaryExpression.Op.ASSIGN) {
                walk(binary.right(), scan, false);
                if (!(binary.left() instanceof AssignableVariableExpression)) {
                    scan.unknown = true; scan.reject("dynamic or structured write");
                }
                walk(binary.left(), scan, true);
            } else {
                if (binary.op() == BinaryExpression.Op.ARROW) { scan.unknown = true; scan.reject("foreign context"); }
                walk(binary.left(), scan, false); walk(binary.right(), scan, false);
            }
        } else if (expression instanceof UnaryExpression unary) {
            walk(unary.expression(), scan, false);
        } else if (expression instanceof TernaryConditionalExpression ternary) {
            walk(ternary.condition(), scan, false); walk(ternary.trueExpression(), scan, false); walk(ternary.falseExpression(), scan, false);
        } else if (expression instanceof ExecutionScopeExpression scope) {
            for (Expression child : scope.expressions()) walk(child, scan, false);
        } else if (expression instanceof CallExpression call) {
            String symbol = call.symbol().toLowerCase(Locale.ROOT);
            boolean math = symbol.startsWith("math.") && PURE_MATH.contains(symbol.substring(5))
                    && MathBinding.INSTANCE.getProperty(symbol.substring(5)) == call.function();
            boolean first = call.function().getClass() == FirstOrderFunction.class && symbol.equals("ysm.first_order");
            boolean second = call.function().getClass() == SecondOrderFunction.class && symbol.equals("ysm.second_order");
            if (first || second) {
                if (call.arguments().size() == 0 || !(call.arguments().getExpression(0) instanceof StringExpression name)
                        || name.getName().isEmpty()) scan.reject("dynamic physics identity");
                else scan.nodes.add((first ? "first:" : "second:timeline:") + name.getName());
            } else if (!math) {
                boolean knownRandom = symbol.startsWith("math.") && Set.of("random", "random_integer", "die_roll", "die_roll_integer", "randomi", "roll", "rolli").contains(symbol.substring(5))
                        && MathBinding.INSTANCE.getProperty(symbol.substring(5)) == call.function();
                boolean knownQuery = (symbol.startsWith("query.") || symbol.startsWith("q."))
                        && QueryBinding.INSTANCE.getProperty(symbol.substring(symbol.indexOf('.') + 1)) == call.function();
                scan.unknown |= !knownRandom && !knownQuery;
                scan.reject("unsupported call effect: " + symbol);
            }
            for (int i = 0; i < call.arguments().size(); i++) walk(call.arguments().getExpression(i), scan, false);
        } else {
            scan.reject("unsupported expression: " + expression.getClass().getSimpleName());
            if (!(expression instanceof ThisExpression)) scan.unknown = true;
        }
    }

    /** Prove overwrite-before-read in original statement order. History/conditional writes are rejected. */
    private static void definite(Expression expression, Set<String> assigned, Set<String> state, List<String> reasons) {
        if (expression instanceof AssignableVariableExpression variable) {
            String name = scoped(variable);
            if (name != null && state.contains(name) && !assigned.contains(name))
                reasons.add("unverified history/accumulator recovery: " + name);
        } else if (expression instanceof BinaryExpression binary && binary.op() == BinaryExpression.Op.ASSIGN) {
            definite(binary.right(), assigned, state, reasons);
            if (binary.left() instanceof AssignableVariableExpression variable && scoped(variable) != null) assigned.add(scoped(variable));
        } else if (expression instanceof BinaryExpression binary) {
            definite(binary.left(), assigned, state, reasons);
            Set<String> right = new HashSet<>(assigned);
            definite(binary.right(), right, state, reasons);
            if (binary.op() != BinaryExpression.Op.AND && binary.op() != BinaryExpression.Op.OR
                    && binary.op() != BinaryExpression.Op.CONDITIONAL && binary.op() != BinaryExpression.Op.NULL_COALESCE) assigned.addAll(right);
        } else if (expression instanceof ExecutionScopeExpression scope) {
            for (Expression child : scope.expressions()) definite(child, assigned, state, reasons);
        } else if (expression instanceof UnaryExpression unary) {
            if (unary.op() == UnaryExpression.Op.RETURN) reasons.add("early return in state program");
            definite(unary.expression(), assigned, state, reasons);
        } else if (expression instanceof TernaryConditionalExpression ternary) {
            definite(ternary.condition(), assigned, state, reasons);
            Set<String> a = new HashSet<>(assigned), b = new HashSet<>(assigned);
            definite(ternary.trueExpression(), a, state, reasons); definite(ternary.falseExpression(), b, state, reasons);
            a.retainAll(b); assigned.addAll(a);
        } else if (expression instanceof CallExpression call) {
            for (int i = 0; i < call.arguments().size(); i++) definite(call.arguments().getExpression(i), assigned, state, reasons);
        }
    }

    public static List<AnimationProgramPlan.Diagnostic> bind(Map<String, AnimationFile> files,
                    List<AnimationControllerFile> controllerFiles, Map<String, IValue> functions, String modelId) {
        return bind(files, controllerFiles, functions, modelId, List.of());
    }

    public static List<AnimationProgramPlan.Diagnostic> bind(Map<String, AnimationFile> files,
                    List<AnimationControllerFile> controllerFiles, Map<String, IValue> functions, String modelId, List<IValue> externalControls) {
        List<Site> sites = new ArrayList<>();
        Map<Animation, String> animations = new LinkedHashMap<>();
        List<AnimationController> controllers = new ArrayList<>();
        for (var file : files.entrySet()) for (Animation animation : file.getValue().getAnimations().values()) {
            animation.programPlan = null;
            animations.put(animation, file.getKey() + "/" + animation.animationName);
            Set<IValue> timeline = Collections.newSetFromMap(new IdentityHashMap<>());
            animation.customInstructionKeyframes.forEach(frame -> timeline.addAll(Arrays.asList(frame.getEventData())));
            int index = 0;
            for (IValue value : animation.programExpressions) sites.add(new Site(animations.get(animation) + "/expression/" + index++,
                    animation, timeline.contains(value), false, value, inspect(value)));
        }
        for (var file : controllerFiles) controllers.addAll(file.getAnimationControllers().values());
        int index = 0;
        for (var controller : controllers) for (var state : controller.getStates().values()) {
            List<IValue> values = new ArrayList<>(state.getPreExpressions()); values.addAll(state.getPostExpressions());
            state.getAnimations().forEach(pair -> { if (pair.getValue() != null) values.add(pair.getValue()); });
            state.getTransitions().forEach(pair -> values.add(pair.right()));
            for (IValue value : values) sites.add(new Site("controller/" + index++ + "/" + state.getName(), null, false, true, value, inspect(value)));
        }
        functions.forEach((name, value) -> sites.add(new Site("functions/" + name, null, false, true, value, inspect(value))));
        for (IValue value : externalControls) sites.add(new Site("gui/" + index++, null, false, true, value, inspect(value)));
        List<AnimationProgramPlan.Diagnostic> diagnostics = new ArrayList<>();
        for (var entry : animations.entrySet()) {
            Animation animation = entry.getKey();
            if (animation.customInstructionKeyframes.isEmpty() || animation.stableKipfelAttachments) continue;
            List<IValue> statements = new ArrayList<>();
            animation.customInstructionKeyframes.forEach(frame -> statements.addAll(Arrays.asList(frame.getEventData())));
            Scan merged = new Scan();
            for (IValue statement : statements) merge(merged, inspect(statement));
            List<String> reasons = new ArrayList<>(merged.reasons);
            double period = animation.declaredPeriodSeconds;
            if (animation.loop != ILoopType.EDefaultLoopTypes.LOOP || !Double.isFinite(period) || period <= 0)
                reasons.add("not a fixed periodic timeline");
            if (animation.customInstructionKeyframes.stream().anyMatch(frame -> frame.getStartTick() != 0))
                reasons.add("timeline has nonzero event times");
            if (!animation.soundKeyFrames.isEmpty() || !animation.particleKeyFrames.isEmpty()) reasons.add("mixed events");
            if (merged.writes.isEmpty()) reasons.add("no persistent scalar outputs");
            if (Double.isFinite(period) && period < .05 / 8) reasons.add("period exceeds the eight-step client-tick budget");
            if (!merged.nodes.isEmpty() && period > PhysicsManager.RECOVERY_SECONDS) reasons.add("physics period exceeds the existing solver recovery window");
            Set<String> assigned = new HashSet<>();
            for (IValue statement : statements) if (statement instanceof MolangValue value && value.parsedSuccessfully())
                for (Expression expression : value.expressions()) definite(expression, assigned, merged.writes, reasons);
            if (!assigned.containsAll(merged.writes)) reasons.add("outputs not overwritten on every step");
            if (!merged.writes.containsAll(merged.reads)) reasons.add("external scalar input/writer has no history protocol");
            // One static activation site with no conditions, transitions, entry/exit side effects or blending.
            int references = 0; boolean staticActivation = true;
            for (var controller : controllers) for (var state : controller.getStates().values())
                for (var pair : state.getAnimations()) if (pair.getKey().equals(animation.animationName)) {
                    references++;
                    staticActivation &= controller.getStates().size() == 1 && controller.getStateId() == state.getHashId()
                            && state.getTransitions().isEmpty() && state.getPreExpressions().isEmpty() && state.getPostExpressions().isEmpty()
                            && !state.isBuiltinEntry() && state.getSubName() == null && constantOne(pair.getValue());
                }
            if (references != 1 || !staticActivation) reasons.add("activation/phase is not a single unconditional controller state");
            List<MolangValue> poses = new ArrayList<>();
            for (Site site : sites) {
                boolean own = site.animation == animation && site.timeline;
                boolean dependent = intersects(merged.writes, site.facts.reads) || intersects(merged.writes, site.facts.writes)
                        || intersects(merged.nodes, site.facts.physicsNodes);
                // Unknown calls can mutate or dynamically address any state. Never infer purity from spelling.
                if (site.facts.unknownEffect && !own) reasons.add(site.resource + ": unknown effects prevent proving ownership");
                if (!dependent || own) continue;
                if (!site.control && site.animation != animation)
                    reasons.add(site.resource + ": consumer phase outside the owning clip is not proven");
                if (site.control || !site.facts.writes.isEmpty() || !site.facts.physicsNodes.isEmpty())
                    reasons.add(site.resource + ": shared writer/physics node or controller feedback");
                else {
                    reasons.addAll(site.facts.reasons);
                    if (!site.facts.inputs.isEmpty() || !site.facts.times.isEmpty() || !merged.writes.containsAll(site.facts.reads))
                        reasons.add(site.resource + ": consumer has an independent input/time domain");
                    if (site.value instanceof MolangValue value) poses.add(value);
                }
            }
            reasons = new ArrayList<>(new LinkedHashSet<>(reasons));
            var diagnostic = new AnimationProgramPlan.Diagnostic(entry.getValue(), period, merged.reads, merged.writes,
                    merged.nodes, AnimationProgramPlan.Phase.TIMELINE_START, reasons);
            diagnostics.add(diagnostic);
            if (diagnostic.accepted()) animation.programPlan = new AnimationProgramPlan(entry.getValue(), animation.animationName, period, statements,
                    merged.reads, merged.writes, merged.nodes, merged.inputs, merged.times, poses,
                    AnimationProgramPlan.Phase.TIMELINE_START, AnimationProgramPlan.Recovery.REBUILD_PHYSICS_AND_OVERWRITE);
            YesSteveModel.LOGGER.info("[SM-PROGRAM] model={} program={} period={} phase={} reads={} writes={} nodes={} accepted={} reasons={}",
                    modelId, entry.getValue(), period, diagnostic.phase(), merged.reads, merged.writes, merged.nodes, diagnostic.accepted(), reasons.stream().limit(8).toList());
        }
        return List.copyOf(diagnostics);
    }
    private static boolean intersects(Set<String> a, Set<String> b) { return a.stream().anyMatch(b::contains); }
    private static boolean constantOne(IValue value) {
        if (value == null) return true;
        if (value instanceof MolangValue molang && molang.parsedSuccessfully() && molang.expressions().size() == 1
                && molang.expressions().get(0) instanceof FloatExpression number) return number.value() == 1;
        return false;
    }
    private static void merge(Scan into, Facts facts) {
        into.reads.addAll(facts.reads); into.writes.addAll(facts.writes); into.nodes.addAll(facts.physicsNodes);
        into.inputs.putAll(facts.inputs); into.times.putAll(facts.times); into.reasons.addAll(facts.reasons); into.unknown |= facts.unknownEffect;
    }
}
