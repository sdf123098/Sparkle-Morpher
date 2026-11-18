package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.core.HostAnimationAssets.AnimationClipSet;
import com.micaftic.morpher.client.core.HostAnimationAssets.AnimationControllerSet;
import com.micaftic.morpher.client.core.HostAnimationAssets.BlendEntry;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerStateSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.TransitionEntry;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.scene.CodedLoopMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fail-closed static analyzer over {@link HostMolangScript}; never evaluates model expressions. */
public final class AnimationProgramAnalyzer {

    private AnimationProgramAnalyzer() {
    }

    private static final Set<String> PURE_MATH = Set.of(
            "floor", "round", "ceil", "trunc", "clamp", "max", "min",
            "abs", "exp", "ln", "sqrt", "mod", "pow", "sin", "cos", "acos", "asin", "atan", "atan2",
            "lerp", "lerprotate", "hermite_blend", "hermite", "min_angle", "sign");

    private static final Set<String> RANDOM_MATH = Set.of(
            "random", "random_integer", "die_roll", "die_roll_integer", "randomi", "roll", "rolli");

    private static final Set<String> INPUTS = Set.of(
            "is_on_ground", "is_in_water", "is_on_fire", "is_sprinting",
            "is_swimming", "is_spectator", "is_riding", "is_sneaking",
            "health", "max_health", "hurt_time");

    private static final Set<String> KNOWN_QUERIES = Set.of(
            "life_time", "anim_time", "delta_time", "all_animations_finished", "any_animation_finished",
            "is_on_ground", "is_in_water", "is_on_fire", "is_sprinting", "is_swimming",
            "is_spectator", "is_riding", "is_sneaking", "health", "max_health", "hurt_time",
            "ground_speed", "vertical_speed", "head_x_rotation", "head_y_rotation",
            "body_x_rotation", "body_y_rotation", "eye_target_x_rotation", "eye_target_y_rotation",
            "modified_distance_moved", "walk_distance", "has_rider", "is_first_person",
            "is_in_water_or_rain", "is_eating", "is_playing_dead", "is_sleeping",
            "is_using_item", "item_in_use_duration", "item_max_use_duration",
            "item_remaining_use_duration", "is_swinging", "swing_time", "attack_time",
            "equipment_count", "cape_flap_amount", "player_level", "is_jumping", "has_cape",
            "actor_count", "moon_phase", "time_of_day", "time_stamp", "yaw_speed",
            "cardinal_facing_2d", "distance_from_camera", "is_alive",
            "biome_has_all_tags", "biome_has_any_tag", "relative_block_has_all_tags",
            "relative_block_has_any_tag", "is_item_name_any", "equipped_item_all_tags",
            "equipped_item_any_tag", "position", "position_delta", "rotation_to_camera",
            "max_durability", "remaining_durability", "get_equipped_item_name");

    public record Facts(
            Set<String> reads,
            Set<String> writes,
            Set<String> physicsNodes,
            Map<String, String> inputs,
            Map<String, String> times,
            List<String> reasons,
            boolean unknownEffect) {
        public Facts {
            reads = Set.copyOf(reads);
            writes = Set.copyOf(writes);
            physicsNodes = Set.copyOf(physicsNodes);
            inputs = Map.copyOf(inputs);
            times = Map.copyOf(times);
            reasons = List.copyOf(reasons);
        }
    }

    private static final class Scan {
        final Set<String> reads = new LinkedHashSet<>();
        final Set<String> writes = new LinkedHashSet<>();
        final Set<String> nodes = new LinkedHashSet<>();
        final Map<String, String> inputs = new LinkedHashMap<>();
        final Map<String, String> times = new LinkedHashMap<>();
        final List<String> reasons = new ArrayList<>();
        boolean unknown;

        void reject(String reason) {
            if (!this.reasons.contains(reason)) {
                this.reasons.add(reason);
            }
        }

        Facts result() {
            return new Facts(this.reads, this.writes, this.nodes, this.inputs, this.times, this.reasons, this.unknown);
        }
    }

    private record Site(
            String resource,
            ClipSpec animation,
            boolean timeline,
            boolean control,
            HostMolangScript value,
            Facts facts) {
    }

    public static Facts inspect(HostMolangScript script) {
        Scan scan = new Scan();
        if (script == null) {
            scan.unknown = true;
            scan.reject("unknown value binding");
            return scan.result();
        }
        if (!script.parsedSuccessfully()) {
            scan.unknown = true;
            String fail = script.failure();
            scan.reject("parse failure: " + (fail == null ? "invalid expression" : fail));
            return scan.result();
        }
        if (script.isConstant()) {
            return scan.result();
        }
        try {
            List<Expr> ast = new ExprParser(script.source()).parseProgram();
            for (Expr expr : ast) {
                walk(expr, scan, false);
            }
        } catch (RuntimeException parseEx) {
            scan.unknown = true;
            scan.reject("parse failure: " + parseEx.getMessage());
        }
        return scan.result();
    }

    private static List<String> definiteOverwrites(HostMolangScript script, Set<String> state) {
        List<String> reasons = new ArrayList<>();
        if (script == null || !script.parsedSuccessfully() || script.isConstant()) {
            return reasons;
        }
        Set<String> assigned = new HashSet<>();
        try {
            List<Expr> ast = new ExprParser(script.source()).parseProgram();
            for (Expr expr : ast) {
                definite(expr, assigned, state, reasons);
            }
            if (!assigned.containsAll(state)) {
                reasons.add("outputs not overwritten on every step");
            }
        } catch (RuntimeException ignored) {
            reasons.add("outputs not overwritten on every step");
        }
        return reasons;
    }

    public static List<AnimationProgramPlan.Diagnostic> bind(
            Map<String, AnimationClipSet> files,
            List<AnimationControllerSet> controllerFiles,
            Map<String, HostMolangScript> functions,
            String modelId) {
        return bind(files, controllerFiles, functions, modelId, List.of());
    }

    public static List<AnimationProgramPlan.Diagnostic> bind(
            Map<String, AnimationClipSet> files,
            List<AnimationControllerSet> controllerFiles,
            Map<String, HostMolangScript> functions,
            String modelId,
            List<HostMolangScript> externalControls) {
        List<Site> sites = new ArrayList<>();
        Map<ClipSpec, String> animations = new LinkedHashMap<>();
        List<ControllerSpec> controllers = new ArrayList<>();
        for (var file : files.entrySet()) {
            for (ClipSpec animation : file.getValue().getAnimations().values()) {
                animation.setProgramPlan(null);
                animations.put(animation, file.getKey() + "/" + animation.name());
                Set<HostMolangScript> timeline = Collections.newSetFromMap(new IdentityHashMap<>());
                // Collect scripts from timeline events matching parsed expressions.
                int tlExprCount = 0;
                if (animation.raw().timelineEvents != null) {
                    for (RawYsmModel.RawTimelineEvent tle : animation.raw().timelineEvents) {
                        if (tle.events != null) {
                            tlExprCount += tle.events.size();
                        }
                    }
                }
                List<HostMolangScript> allExprs = animation.programExpressions();
                int tlStart = Math.max(0, allExprs.size() - tlExprCount
                        - (animation.raw().blendWeight instanceof String ? 1 : 0));
                int tlEnd = Math.min(allExprs.size(), tlStart + tlExprCount);
                for (int i = tlStart; i < tlEnd; i++) {
                    timeline.add(allExprs.get(i));
                }
                int index = 0;
                for (HostMolangScript value : allExprs) {
                    sites.add(new Site(
                            animations.get(animation) + "/expression/" + index++,
                            animation,
                            timeline.contains(value),
                            false,
                            value,
                            inspect(value)));
                }
            }
        }
        for (AnimationControllerSet file : controllerFiles) {
            controllers.addAll(file.getAnimationControllers().values());
        }
        int index = 0;
        for (ControllerSpec controller : controllers) {
            for (ControllerStateSpec state : controller.states().values()) {
                List<HostMolangScript> values = new ArrayList<>(state.onEntry());
                values.addAll(state.onExit());
                for (BlendEntry pair : state.animations()) {
                    if (pair.weight() != null) {
                        values.add(pair.weight());
                    }
                }
                for (TransitionEntry pair : state.transitions()) {
                    if (pair.condition() != null) {
                        values.add(pair.condition());
                    }
                }
                for (HostMolangScript value : values) {
                    sites.add(new Site("controller/" + index++ + "/" + state.name(), null, false, true, value, inspect(value)));
                }
            }
        }
        functions.forEach((name, value) -> sites.add(new Site("functions/" + name, null, false, true, value, inspect(value))));
        for (HostMolangScript value : externalControls) {
            sites.add(new Site("gui/" + index++, null, false, true, value, inspect(value)));
        }

        List<AnimationProgramPlan.Diagnostic> diagnostics = new ArrayList<>();
        for (var entry : animations.entrySet()) {
            ClipSpec animation = entry.getKey();
            if (!animation.hasTimelineEvents() || animation.isStableKipfelAttachments()) {
                continue;
            }
            List<HostMolangScript> statements = new ArrayList<>();
            boolean hasNonZeroEventTime = false;
            for (RawYsmModel.RawTimelineEvent tle : animation.raw().timelineEvents) {
                if (tle.timestamp != 0.0f) {
                    hasNonZeroEventTime = true;
                }
                if (tle.events != null) {
                    for (String line : tle.events) {
                        statements.add(HostMolangScript.compile(line));
                    }
                }
            }
            Scan merged = new Scan();
            for (HostMolangScript statement : statements) {
                merge(merged, inspect(statement));
            }
            List<String> reasons = new ArrayList<>(merged.reasons);
            double period = animation.lengthSeconds();
            if (animation.loopMode() != CodedLoopMode.LOOP || !Double.isFinite(period) || period <= 0) {
                reasons.add("not a fixed periodic timeline");
            }
            if (hasNonZeroEventTime) {
                reasons.add("timeline has nonzero event times");
            }
            if (animation.raw().soundEffects != null && !animation.raw().soundEffects.isEmpty()) {
                reasons.add("mixed events");
            }
            if (merged.writes.isEmpty()) {
                reasons.add("no persistent scalar outputs");
            }
            if (Double.isFinite(period) && period < .05 / 8) {
                reasons.add("period exceeds the eight-step client-tick budget");
            }
            if (!merged.nodes.isEmpty() && period > PhysicsManager.RECOVERY_SECONDS) {
                reasons.add("physics period exceeds the existing solver recovery window");
            }
            Set<String> assigned = new HashSet<>();
            for (HostMolangScript statement : statements) {
                if (!statement.parsedSuccessfully()) {
                    continue;
                }
                try {
                    for (Expr expr : new ExprParser(statement.source()).parseProgram()) {
                        definite(expr, assigned, merged.writes, reasons);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            if (!assigned.containsAll(merged.writes)) {
                reasons.add("outputs not overwritten on every step");
            }
            if (!merged.writes.containsAll(merged.reads)) {
                reasons.add("external scalar input/writer has no history protocol");
            }

            int references = 0;
            boolean staticActivation = true;
            for (ControllerSpec controller : controllers) {
                String initialState = controller.initialState();
                for (ControllerStateSpec state : controller.states().values()) {
                    for (BlendEntry pair : state.animations()) {
                        if (pair.animationName().equals(animation.name())) {
                            references++;
                            staticActivation &= controller.states().size() == 1
                                    && initialState.equals(state.name())
                                    && state.transitions().isEmpty()
                                    && state.onEntry().isEmpty()
                                    && state.onExit().isEmpty()
                                    && !"builtin".equals(state.name())
                                    && constantOne(pair.weight());
                        }
                    }
                }
            }
            if (references != 1 || !staticActivation) {
                reasons.add("activation/phase is not a single unconditional controller state");
            }

            List<HostMolangScript> poses = new ArrayList<>();
            for (Site site : sites) {
                boolean own = site.animation == animation && site.timeline;
                boolean dependent = intersects(merged.writes, site.facts.reads)
                        || intersects(merged.writes, site.facts.writes)
                        || intersects(merged.nodes, site.facts.physicsNodes);
                if (site.facts.unknownEffect && !own) {
                    reasons.add(site.resource + ": unknown effects prevent proving ownership");
                }
                if (!dependent || own) {
                    continue;
                }
                if (!site.control && site.animation != animation) {
                    reasons.add(site.resource + ": consumer phase outside the owning clip is not proven");
                }
                if (site.control || !site.facts.writes.isEmpty() || !site.facts.physicsNodes.isEmpty()) {
                    reasons.add(site.resource + ": shared writer/physics node or controller feedback");
                } else {
                    reasons.addAll(site.facts.reasons);
                    if (!site.facts.inputs.isEmpty() || !site.facts.times.isEmpty() || !merged.writes.containsAll(site.facts.reads)) {
                        reasons.add(site.resource + ": consumer has an independent input/time domain");
                    }
                    poses.add(site.value);
                }
            }
            reasons = new ArrayList<>(new LinkedHashSet<>(reasons));
            var diagnostic = new AnimationProgramPlan.Diagnostic(
                    entry.getValue(), period, merged.reads, merged.writes,
                    merged.nodes, AnimationProgramPlan.Phase.TIMELINE_START, reasons);
            diagnostics.add(diagnostic);
            if (diagnostic.accepted()) {
                animation.setProgramPlan(new AnimationProgramPlan(
                        entry.getValue(), animation.name(), period, statements,
                        merged.reads, merged.writes, merged.nodes, merged.inputs, merged.times, poses,
                        AnimationProgramPlan.Phase.TIMELINE_START, AnimationProgramPlan.Recovery.REBUILD_PHYSICS_AND_OVERWRITE));
            }
            YesSteveModel.LOGGER.info(
                    "[SM-PROGRAM] model={} program={} period={} phase={} reads={} writes={} nodes={} accepted={} reasons={}",
                    modelId, entry.getValue(), period, diagnostic.phase(), merged.reads, merged.writes,
                    merged.nodes, diagnostic.accepted(), reasons.stream().limit(8).toList());
        }
        return List.copyOf(diagnostics);
    }

    private static void walk(Expr expr, Scan scan, boolean writing) {
        switch (expr) {
            case LiteralExpr ignored -> {
            }
            case ReturnExpr ret -> walk(ret.value, scan, false);
            case ThisExpr ignored -> scan.reject("unsupported expression: ThisExpression");
            case ArrowExpr arrow -> {
                scan.unknown = true;
                scan.reject("foreign context");
                walk(arrow.left, scan, false);
                walk(arrow.right, scan, false);
            }
            case VarExpr varExpr -> {
                String lower = varExpr.symbol.toLowerCase(Locale.ROOT);
                String scopedName = scopedVarName(lower);
                if (scopedName != null) {
                    (writing ? scan.writes : scan.reads).add(scopedName);
                } else if (lower.startsWith("q.") || lower.startsWith("query.")) {
                    String qName = lower.substring(lower.indexOf('.') + 1);
                    if (qName.equals("life_time") || qName.equals("anim_time")) {
                        scan.times.put(qName, qName);
                    } else if (INPUTS.contains(qName)) {
                        scan.inputs.put(qName, qName);
                    } else if (KNOWN_QUERIES.contains(qName)) {
                        scan.reject("input history unavailable: query." + qName);
                    } else {
                        scan.unknown = true;
                        scan.reject("unknown input binding: " + (lower.startsWith("q.") ? "query." + qName : lower));
                    }
                } else if (lower.startsWith("v.") || lower.startsWith("variable.")) {
                    scan.reject("unsupported variable scope: " + varExpr.symbol);
                    if (writing) {
                        scan.unknown = true;
                    }
                } else {
                    scan.unknown = true;
                    scan.reject("unknown input binding: " + lower);
                }
            }
            case AssignExpr assign -> {
                walk(assign.right, scan, false);
                if (assign.left instanceof VarExpr v && scopedVarName(v.symbol.toLowerCase(Locale.ROOT)) != null) {
                    walk(v, scan, true);
                } else {
                    scan.unknown = true;
                    scan.reject("dynamic or structured write");
                }
            }
            case BinaryExpr binary -> {
                walk(binary.left, scan, false);
                walk(binary.right, scan, false);
            }
            case UnaryExpr unary -> walk(unary.expr, scan, false);
            case TernaryExpr ternary -> {
                walk(ternary.cond, scan, false);
                walk(ternary.whenTrue, scan, false);
                if (ternary.whenFalse != null) {
                    walk(ternary.whenFalse, scan, false);
                }
            }
            case BlockExpr block -> {
                for (Expr child : block.statements) {
                    walk(child, scan, false);
                }
            }
            case CallExpr call -> {
                String symbol = call.symbol.toLowerCase(Locale.ROOT);
                boolean math = symbol.startsWith("math.") && PURE_MATH.contains(symbol.substring(5));
                boolean first = "ysm.first_order".equals(symbol);
                boolean second = "ysm.second_order".equals(symbol);
                if (first || second) {
                    if (call.args.isEmpty() || !(call.args.getFirst() instanceof LiteralExpr lit)
                            || lit.stringValue == null || lit.stringValue.isEmpty()) {
                        scan.reject("dynamic physics identity");
                    } else {
                        scan.nodes.add((first ? "first:" : "second:timeline:") + lit.stringValue);
                    }
                } else if (!math) {
                    boolean knownRandom = symbol.startsWith("math.") && RANDOM_MATH.contains(symbol.substring(5));
                    boolean knownQuery = (symbol.startsWith("query.") || symbol.startsWith("q."))
                            && KNOWN_QUERIES.contains(symbol.substring(symbol.indexOf('.') + 1));
                    scan.unknown |= !knownRandom && !knownQuery;
                    scan.reject("unsupported call effect: " + symbol);
                }
                for (Expr arg : call.args) {
                    walk(arg, scan, false);
                }
            }
        }
    }

    private static void definite(Expr expr, Set<String> assigned, Set<String> state, List<String> reasons) {
        switch (expr) {
            case LiteralExpr ignored -> {
            }
            case ThisExpr ignored -> {
            }
            case ReturnExpr ret -> {
                reasons.add("early return in state program");
                definite(ret.value, assigned, state, reasons);
            }
            case VarExpr varExpr -> {
                String name = scopedVarName(varExpr.symbol.toLowerCase(Locale.ROOT));
                if (name != null && state.contains(name) && !assigned.contains(name)) {
                    reasons.add("unverified history/accumulator recovery: " + name);
                }
            }
            case AssignExpr assign -> {
                definite(assign.right, assigned, state, reasons);
                if (assign.left instanceof VarExpr varExpr) {
                    String name = scopedVarName(varExpr.symbol.toLowerCase(Locale.ROOT));
                    if (name != null) {
                        assigned.add(name);
                    }
                }
            }
            case BinaryExpr binary -> {
                definite(binary.left, assigned, state, reasons);
                Set<String> right = new HashSet<>(assigned);
                definite(binary.right, right, state, reasons);
                if (!binary.shortCircuit) {
                    assigned.addAll(right);
                }
            }
            case UnaryExpr unary -> definite(unary.expr, assigned, state, reasons);
            case BlockExpr block -> {
                for (Expr child : block.statements) {
                    definite(child, assigned, state, reasons);
                }
            }
            case ArrowExpr arrow -> {
                definite(arrow.left, assigned, state, reasons);
                definite(arrow.right, assigned, state, reasons);
            }
            case TernaryExpr ternary -> {
                definite(ternary.cond, assigned, state, reasons);
                Set<String> a = new HashSet<>(assigned);
                Set<String> b = new HashSet<>(assigned);
                definite(ternary.whenTrue, a, state, reasons);
                if (ternary.whenFalse != null) {
                    definite(ternary.whenFalse, b, state, reasons);
                }
                a.retainAll(b);
                assigned.addAll(a);
            }
            case CallExpr call -> {
                for (Expr arg : call.args) {
                    definite(arg, assigned, state, reasons);
                }
            }
        }
    }

    private static String scopedVarName(String lowerSymbol) {
        if (lowerSymbol.startsWith("v.")) {
            String rest = lowerSymbol.substring(2);
            return rest.indexOf('.') >= 0 ? null : rest;
        }
        if (lowerSymbol.startsWith("variable.")) {
            String rest = lowerSymbol.substring(9);
            return rest.indexOf('.') >= 0 ? null : rest;
        }
        return null;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        return a.stream().anyMatch(b::contains);
    }

    private static boolean constantOne(HostMolangScript value) {
        if (value == null) {
            return true;
        }
        if (value.isConstant()) {
            return value.constantValue() == 1.0;
        }
        if (!value.parsedSuccessfully()) {
            return false;
        }
        try {
            List<Expr> ast = new ExprParser(value.source()).parseProgram();
            return ast.size() == 1 && ast.getFirst() instanceof LiteralExpr lit && lit.numberValue != null && lit.numberValue == 1.0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void merge(Scan into, Facts facts) {
        into.reads.addAll(facts.reads);
        into.writes.addAll(facts.writes);
        into.nodes.addAll(facts.physicsNodes);
        into.inputs.putAll(facts.inputs);
        into.times.putAll(facts.times);
        into.reasons.addAll(facts.reasons);
        into.unknown |= facts.unknownEffect;
    }

    // ---- Lightweight AST parser for static proof checks ----

    private sealed interface Expr permits LiteralExpr, ThisExpr, VarExpr, AssignExpr, BinaryExpr, ArrowExpr, UnaryExpr, TernaryExpr, BlockExpr, CallExpr, ReturnExpr {
    }

    private record LiteralExpr(Double numberValue, String stringValue) implements Expr {
    }

    private record ThisExpr() implements Expr {
    }

    private record VarExpr(String symbol) implements Expr {
    }

    private record AssignExpr(Expr left, Expr right) implements Expr {
    }

    private record BinaryExpr(Expr left, Expr right, boolean shortCircuit) implements Expr {
    }

    private record ArrowExpr(Expr left, Expr right) implements Expr {
    }

    private record UnaryExpr(Expr expr) implements Expr {
    }

    private record TernaryExpr(Expr cond, Expr whenTrue, Expr whenFalse) implements Expr {
    }

    private record BlockExpr(List<Expr> statements) implements Expr {
    }

    private record CallExpr(String symbol, List<Expr> args) implements Expr {
    }

    private static final class ExprParser {
        private final String s;
        private int pos;

        ExprParser(String s) {
            this.s = s;
        }

        List<Expr> parseProgram() {
            List<Expr> list = new ArrayList<>();
            skipWs();
            while (pos < s.length()) {
                if (peek() == ';') {
                    pos++;
                    skipWs();
                    continue;
                }
                list.add(parseAssignment());
                skipWs();
                while (pos < s.length() && peek() == ';') {
                    pos++;
                    skipWs();
                }
            }
            return list;
        }

        private Expr parseAssignment() {
            Expr left = parseTernary();
            skipWs();
            if (peek() == '=' && peekAt(pos + 1) != '=') {
                pos++;
                return new AssignExpr(left, parseAssignment());
            }
            return left;
        }

        private Expr parseTernary() {
            Expr cond = parseCoalesce();
            skipWs();
            if (peek() == '?' && peekAt(pos + 1) != '?') {
                pos++;
                Expr whenTrue = parseAssignment();
                skipWs();
                Expr whenFalse = null;
                if (peek() == ':') {
                    pos++;
                    whenFalse = parseAssignment();
                }
                return new TernaryExpr(cond, whenTrue, whenFalse);
            }
            return cond;
        }

        private Expr parseCoalesce() {
            Expr left = parseOr();
            while (match("??")) {
                left = new BinaryExpr(left, parseOr(), true);
            }
            return left;
        }

        private Expr parseOr() {
            Expr left = parseAnd();
            while (match("||")) {
                left = new BinaryExpr(left, parseAnd(), true);
            }
            return left;
        }

        private Expr parseAnd() {
            Expr left = parseComparison();
            while (match("&&")) {
                left = new BinaryExpr(left, parseComparison(), true);
            }
            return left;
        }

        private Expr parseComparison() {
            Expr left = parseAdd();
            while (true) {
                skipWs();
                if (match("==") || match("!=") || match("<=") || match(">=")) {
                    left = new BinaryExpr(left, parseAdd(), false);
                } else if (peek() == '<' || peek() == '>') {
                    pos++;
                    left = new BinaryExpr(left, parseAdd(), false);
                } else {
                    break;
                }
            }
            return left;
        }

        private Expr parseAdd() {
            Expr left = parseMul();
            while (true) {
                skipWs();
                if (match("->")) {
                    left = new ArrowExpr(left, parseMul());
                } else if (peek() == '+' || peek() == '-') {
                    pos++;
                    left = new BinaryExpr(left, parseMul(), false);
                } else {
                    break;
                }
            }
            return left;
        }

        private Expr parseMul() {
            Expr left = parseUnary();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '*' || c == '/') {
                    pos++;
                    left = new BinaryExpr(left, parseUnary(), false);
                } else {
                    break;
                }
            }
            return left;
        }

        private Expr parseUnary() {
            skipWs();
            char c = peek();
            if (c == '!' || c == '-' || c == '+') {
                pos++;
                return new UnaryExpr(parseUnary());
            }
            return parsePrimary();
        }

        private Expr parsePrimary() {
            skipWs();
            if (pos >= s.length()) {
                return new LiteralExpr(0.0, null);
            }
            char c = peek();
            if (c == '(') {
                pos++;
                Expr inner = parseAssignment();
                skipWs();
                if (peek() == ')') {
                    pos++;
                }
                return inner;
            }
            if (c == '{') {
                pos++;
                List<Expr> stmts = new ArrayList<>();
                skipWs();
                while (pos < s.length() && peek() != '}') {
                    if (peek() == ';') {
                        pos++;
                        skipWs();
                        continue;
                    }
                    stmts.add(parseAssignment());
                    skipWs();
                    while (pos < s.length() && peek() == ';') {
                        pos++;
                        skipWs();
                    }
                }
                if (peek() == '}') {
                    pos++;
                }
                return new BlockExpr(stmts);
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < s.length() && s.charAt(pos) != quote) {
                    if (s.charAt(pos) == '\\' && pos + 1 < s.length()) {
                        pos++;
                    }
                    sb.append(s.charAt(pos++));
                }
                if (pos < s.length()) {
                    pos++;
                }
                return new LiteralExpr(null, sb.toString());
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.'
                        || s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                    if ((s.charAt(pos) == 'e' || s.charAt(pos) == 'E') && pos + 1 < s.length()
                            && (s.charAt(pos + 1) == '+' || s.charAt(pos + 1) == '-')) {
                        pos++;
                    }
                    pos++;
                }
                return new LiteralExpr(Double.parseDouble(s.substring(start, pos)), null);
            }
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("Unexpected token '" + s.charAt(pos++) + "'");
            }
            String ident = s.substring(start, pos);
            String lower = ident.toLowerCase(Locale.ROOT);
            if ("return".equals(lower)) {
                return new ReturnExpr(parseAssignment());
            }
            if ("this".equals(lower)) {
                return new ThisExpr();
            }
            if ("true".equals(lower)) {
                return new LiteralExpr(1.0, null);
            }
            if ("false".equals(lower)) {
                return new LiteralExpr(0.0, null);
            }
            skipWs();
            if (peek() == '(') {
                pos++;
                List<Expr> args = new ArrayList<>();
                skipWs();
                if (peek() != ')') {
                    args.add(parseAssignment());
                    skipWs();
                    while (peek() == ',') {
                        pos++;
                        args.add(parseAssignment());
                        skipWs();
                    }
                }
                if (peek() == ')') {
                    pos++;
                }
                return new CallExpr(ident, args);
            }
            return new VarExpr(ident);
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private char peekAt(int index) {
            return index < s.length() ? s.charAt(index) : '\0';
        }

        private boolean match(String token) {
            skipWs();
            if (s.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }
    }

    private record ReturnExpr(Expr value) implements Expr {
    }
}
