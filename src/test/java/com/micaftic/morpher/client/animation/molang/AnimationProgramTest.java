package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.geckolib3.core.builder.*;
import com.micaftic.morpher.geckolib3.core.molang.MolangParser;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.molang.storage.VariableStorage;
import com.micaftic.morpher.geckolib3.core.molang.value.*;
import com.micaftic.morpher.geckolib3.file.*;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.runtime.*;
import com.micaftic.morpher.molang.parser.ast.*;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnimationProgramTest {
    private java.util.Queue<MolangParser> parserPool;
    private List<MolangParser> savedParsers;
    @BeforeEach void useRealCoreBindingsWithoutPlatformBootstrap() throws Exception {
        // Plain JVM tests have no Architectury platform implementation in the 1.21.1 branch.
        // Isolate the pool, using the actual physics functions and PrimaryBinding's math/query bindings.
        var field = GeckoLibCache.class.getDeclaredField("PARSER_POOL"); field.setAccessible(true);
        parserPool = (java.util.Queue<MolangParser>)field.get(null);
        savedParsers = new ArrayList<>(parserPool); parserPool.clear();
        com.micaftic.morpher.molang.runtime.binding.ObjectBinding physics = name -> switch (name) {
            case "first_order" -> new com.micaftic.morpher.client.animation.molang.functions.ysm.FirstOrderFunction();
            case "second_order" -> new com.micaftic.morpher.client.animation.molang.functions.ysm.SecondOrderFunction();
            default -> null;
        };
        parserPool.add(new MolangParser(Map.of("ysm", physics)));
    }
    @AfterEach void restoreParserPool() { parserPool.clear(); parserPool.addAll(savedParsers); }
    private record Fixture(Map<String, AnimationFile> files, List<AnimationControllerFile> controllers, Animation animation) {
        List<AnimationProgramPlan.Diagnostic> bind() { return AnimationProgramAnalyzer.bind(files, controllers, Map.of(), "test"); }
    }
    private Fixture fixture(String name, String variable, String script, String... consumers) {
        var file = new RawYsmModel.RawAnimationFile();
        var animation = new RawYsmModel.RawAnimation();
        animation.name = name; animation.length = .023f; animation.loopMode = 1;
        var timeline = new RawYsmModel.RawTimelineEvent(); timeline.events.add(script);
        animation.timelineEvents.add(timeline);
        var bone = new RawYsmModel.RawBoneAnimation(); bone.boneName = "arbitrary";
        for (var channel : List.of(bone.rotation, bone.position, bone.scale)) {
            var key = new RawYsmModel.RawKeyframe();
            key.postData = new Object[]{"v." + variable, 0f, 1f}; channel.add(key);
            // Multiple keyframes and multi-turn values must stay in the existing channel sampler.
            var end = new RawYsmModel.RawKeyframe(); end.timestamp = .023f;
            end.postData = new Object[]{"v." + variable + "+720", 0f, 1f}; channel.add(end);
        }
        animation.boneAnimations.add(bone);
        file.animations.put(name, animation);
        for (int i = 0; i < consumers.length; i++) {
            var other = new RawYsmModel.RawAnimation(); other.name = "other" + i;
            var frame = new RawYsmModel.RawTimelineEvent(); frame.events.add(consumers[i]); other.timelineEvents.add(frame);
            file.animations.put(other.name, other);
        }
        var mapped = AnimationMapper.buildAnimations(file, false);
        var rawController = new RawYsmModel.RawAnimationController();
        rawController.animationName = "controller"; rawController.initialState = "default";
        var state = new RawYsmModel.RawControllerState(); state.name = "default"; state.animations.put(name, ""); rawController.states.add(state);
        return new Fixture(Map.of("main", new AnimationFile(mapped)),
                new ArrayList<>(List.of(new AnimationControllerFile(AnimationMapper.buildControllers(Map.of("controller", rawController), false)))), mapped.get(name));
    }
    private Fixture simple() { return fixture("arbitrary", "motion", "v.motion=ysm.second_order('spring',math.sin(q.life_time*40),2,0.5,1);"); }

    @Test void parseFailureIsNotAValidZeroAndAstIsReadOnly() throws Exception {
        var parser = new MolangParser(Map.of());
        var valid = (MolangValue) parser.parseExpressionUnsafe("0", false);
        var failed = (MolangValue) parser.parseExpression("v.x=unknown.effect(1)", false);
        assertTrue(valid.parsedSuccessfully()); assertFalse(failed.parsedSuccessfully());
        assertEquals(0f, failed.evalAsFloat(ExpressionEvaluator.evaluator()));
        assertFalse(AnimationProgramAnalyzer.inspect(failed).reasons().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> valid.expressions().clear());
    }
    @Test void symbolsRetainOriginalNamespacesAndBindingIdentity() throws Exception {
        var value = (MolangValue) GeckoLibCache.parseSimpleExpression("variable.private_name=ysm.first_order('spring',q.life_time);");
        var expression = (BinaryExpression)value.expressions().getFirst();
        assertEquals("variable.private_name", ((AssignableVariableExpression)expression.left()).symbol());
        assertEquals("ysm.first_order", ((CallExpression)expression.right()).symbol());
        var facts = AnimationProgramAnalyzer.inspect(value);
        assertEquals(Set.of("private_name"), facts.writes()); assertEquals(Set.of("first:spring"), facts.physicsNodes());
        assertTrue(facts.reasons().isEmpty());
    }
    @Test void automaticPlanIgnoresModelAnimationBoneAndPrivateVariableNames() {
        for (String name : List.of("first", "renamed")) {
            var f = fixture(name, name, "variable." + name + "=ysm.first_order('node',math.sin(q.life_time*30),4);");
            assertTrue(f.bind().getFirst().accepted(), () -> f.bind().toString());
            var plan = f.animation.programPlan;
            assertEquals((double).023f, plan.period(), 0);
            assertEquals(6, plan.poseExpressions().size());
            assertEquals(1, f.animation.customInstructionKeyframes.size(), "binding must not erase the event timeline");
            assertThrows(UnsupportedOperationException.class, () -> plan.statements().clear());
        }
    }
    @Test void failedUnknownMixedAndConflictingProgramsAreRejected() {
        var parse = fixture("parse", "motion", "v.motion=ysm.second_order('spring',unknown.x);");
        assertFalse(parse.bind().getFirst().accepted());
        var unknown = fixture("unknown", "motion", "v.motion=ysm.second_order('spring',math.random(0,1));");
        assertFalse(unknown.bind().getFirst().accepted());
        var conflicting = fixture("conflict", "motion", "v.motion=ysm.second_order('spring',1);", "v.motion=2;");
        assertFalse(conflicting.bind().getFirst().accepted());
        var shared = fixture("shared", "motion", "v.motion=ysm.first_order('same',1);", "v.x=ysm.first_order('same',2);");
        assertFalse(shared.bind().getFirst().accepted());
        var mixed = simple();
        mixed.animation.soundKeyFrames.add(new com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame<>(0, "sound"));
        assertFalse(mixed.bind().getFirst().accepted());
        var dynamic = fixture("dynamic", "motion", "v.motion=ysm.first_order(v.motion,1);");
        assertFalse(dynamic.bind().getFirst().accepted());
    }
    @Test void controllerFeedbackHistoryAndConditionalPersistenceAreRejected() {
        var f = simple();
        var controller = f.controllers.getFirst().getAnimationControllers().values().iterator().next();
        var state = controller.getStates().values().iterator().next();
        var stateWithFeedback = new AnimationState("default", new org.apache.commons.lang3.tuple.Pair[]{
                org.apache.commons.lang3.tuple.Pair.of("arbitrary", AnimationMapper.parse("v.motion"))},
                new org.apache.commons.lang3.tuple.Pair[0], new String[0], new IValue[0], new IValue[0], state.getBlendTransition(), false);
        f.controllers.set(0, new AnimationControllerFile(Map.of("controller", new AnimationController("default", new AnimationState[]{stateWithFeedback}))));
        assertFalse(f.bind().getFirst().accepted());
        var history = fixture("history", "motion", "v.motion=ysm.first_order('spring',v.previous);v.previous=q.life_time;");
        assertFalse(history.bind().getFirst().accepted());
        var conditional = fixture("conditional", "motion", "q.is_on_ground ? {v.motion=ysm.first_order('spring',1);};");
        assertFalse(conditional.bind().getFirst().accepted());
    }
    @Test void analysisNeverExecutesAFunction() {
        int[] effects = {0};
        Function unknown = (ctx, args) -> { effects[0]++; return 1f; };
        var value = new MolangValue(List.of(new CallExpression(unknown)), false);
        assertTrue(AnimationProgramAnalyzer.inspect(value).unknownEffect()); assertEquals(0, effects[0]);
    }
    @Test void guiWriterAndUnserviceablePeriodPreventTakeover() {
        var f = simple();
        var diagnostics = AnimationProgramAnalyzer.bind(f.files, f.controllers, Map.of(), "ui",
                List.of(AnimationMapper.parse("v.motion=0")));
        assertFalse(diagnostics.getFirst().accepted());
        var fast = simple(); fast.animation.declaredPeriodSeconds = .001;
        assertTrue(fast.bind().getFirst().reasons().contains("period exceeds the eight-step client-tick budget"));
        var slow = simple(); slow.animation.declaredPeriodSeconds = .5;
        assertFalse(slow.bind().getFirst().accepted());
    }
    @Test void knownEntityInputIsRecordedButUnknownBindingIsNotTrusted() {
        var f = fixture("input", "motion", "v.motion=ysm.first_order('node',q.is_on_ground,2);");
        assertTrue(f.bind().getFirst().accepted());
        assertEquals(Set.of("is_on_ground"), new HashSet<>(f.animation.programPlan.inputs().values()));
        var parser = new MolangParser(Map.of("injected", (com.micaftic.morpher.molang.runtime.binding.ObjectBinding)name -> (Variable)ctx -> 1f));
        assertFalse(AnimationProgramAnalyzer.inspect(parser.parseExpression("v.x=injected.life_time", false)).reasons().isEmpty());
    }
    @Test void knownPhysicsBindingUsesOnlyProgramManager() throws Exception {
        var context = new AnimationContext<>(null, null, null, null);
        context.setStorage(new VariableStorage()); context.programPhysics = new PhysicsManager();
        var evaluator = ExpressionEvaluator.evaluator(context);
        var first = new com.micaftic.morpher.client.animation.molang.functions.ysm.FirstOrderFunction();
        var args = new Function.ArgumentCollection(List.of(new StringExpression("shared"), new FloatExpression(3), new FloatExpression(2)));
        context.programPhysics.updateSimulation(0, true);
        assertEquals(3f, first.eval((ExecutionContext)evaluator, args));
        context.programPhysics.updateSimulation(.02, false);
        var changed = new Function.ArgumentCollection(List.of(new StringExpression("shared"), new FloatExpression(9), new FloatExpression(2)));
        float moved = ((Number)first.eval((ExecutionContext)evaluator, changed)).floatValue();
        assertTrue(moved > 3 && moved < 9);
        assertEquals(moved, first.eval((ExecutionContext)evaluator, args));
    }
    private AnimationProgramPlan runtimePlan(Map<Variable, String> inputs) {
        return new AnimationProgramPlan("test", "test", .023, List.of(), Set.of(), Set.of("value"), Set.of("first:node"), inputs,
                Map.of(), List.of(), AnimationProgramPlan.Phase.TIMELINE_START, AnimationProgramPlan.Recovery.REBUILD_PHYSICS_AND_OVERWRITE);
    }
    @Test void replayAtFiveFrameRatesHasIdenticalAcceptedStepsAndPhysicsTrajectory() {
        List<String> reference = null;
        Variable input = ctx -> 0f;
        for (int fps : new int[]{15,30,60,144,240}) {
            var runtime = new AnimationProgramRuntime(runtimePlan(Map.of(input, "sample")), 5);
            var physics = new PhysicsManager();
            List<String> trajectory = new ArrayList<>();
            double[] lastRecorded = {-1};
            AnimationProgramRuntime.Executor execute = step -> {
                if (step.recovery()) physics.clear();
                physics.updateSimulation(step.time(), step.recovery());
                float value = physics.sampleFirst(7, ((Number)step.inputs().get(input)).floatValue(), 4);
                trajectory.add(step.id()+":"+step.time()+":"+value);
                return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(), Map.of("value", value), Map.of(), step.recovery());
            };
            for (int frame = 0; frame <= fps * 3; frame++) {
                double time = frame / (double)fps;
                for (int tick = (int)lastRecorded[0] + 1; tick <= Math.floor(time * 20 + 1e-7); tick++) {
                    runtime.recordInput(tick / 20.0, Map.of(input, (float)Math.sin(tick * .17)));
                    lastRecorded[0] = tick;
                }
                runtime.advance(time, 5, execute);
                runtime.advance(time, 5, execute);
            }
            if (reference == null) reference = trajectory; else assertEquals(reference, trajectory, "fps="+fps);
            assertEquals(131, trajectory.size());
        }
    }
    @Test void missingHistoryRecoversAndOldGenerationCannotPublish() {
        Variable input = ctx -> 1f;
        var runtime = new AnimationProgramRuntime(runtimePlan(Map.of(input, "sample")), 9);
        List<AnimationProgramRuntime.Step> steps = new ArrayList<>();
        AnimationProgramRuntime.Executor execute = step -> {
            steps.add(step);
            return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(), Map.of(), Map.of(), step.recovery());
        };
        runtime.recordInput(0, Map.of(input, 1f)); runtime.advance(0, 9, execute);
        runtime.recordInput(.1, Map.of(input, 2f)); runtime.advance(.1, 9, execute);
        assertEquals(2, steps.size()); assertTrue(steps.getLast().recovery());
        runtime.advance(.09, 9, execute); assertEquals(2, steps.size());
        runtime.advance(.12, 8, execute); assertEquals(2, steps.size());
        runtime.close(); runtime.advance(1, 9, execute); assertNull(runtime.snapshot());
    }
    @Test void longPauseRebuildsOnceAndCloseDuringEvaluationDiscardsSnapshot() {
        var runtime = new AnimationProgramRuntime(runtimePlan(Map.of()), 1);
        int[] calls = {0};
        AnimationProgramRuntime.Executor execute = step -> {
            calls[0]++;
            return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(), Map.of("value", 1f), Map.of(), step.recovery());
        };
        runtime.advance(0, 1, execute); runtime.advance(100, 1, execute);
        assertEquals(2, calls[0]); assertTrue(runtime.snapshot().recovery());
        runtime.advance(100.03, 1, step -> { runtime.close(); return execute.execute(step); });
        assertNull(runtime.snapshot());
    }
    @Test void evaluatorReusesOriginalOrderedScriptAndSnapshotSupportsEveryChannel() throws Exception {
        var parser = new MolangParser(Map.of());
        var script = parser.parseExpressionUnsafe("v.a=2;v.b=v.a+3;v.a=7;", false);
        var context = new AnimationContext<>(null, null, null, null); context.setStorage(new VariableStorage());
        var evaluator = ExpressionEvaluator.evaluator(context);
        script.evalUnsafe(evaluator);
        assertEquals(5f, parser.parseExpressionUnsafe("v.b", false).evalAsFloat(evaluator));
        var pose = (MolangValue)parser.parseExpressionUnsafe("720+v.a", false);
        context.programPoseValues = Map.of(pose, 727f);
        assertEquals(727f, pose.evalAsFloat(evaluator), 0); assertEquals(727f, pose.evalUnsafe(evaluator));
        assertTrue(pose.evalAsBoolean(evaluator));
    }
    @Test void runtimePublishesOriginalBooleanAndStringStateTypes() throws Exception {
        var f = fixture("typed", "motion", "v.motion=1;v.flag=(1==1);v.label='safe';");
        assertTrue(f.bind().getFirst().accepted());
        var runtime = new AnimationProgramRuntime(f.animation.programPlan, 7);
        var source = AnimationProgramRuntime.class.getDeclaredField("source"); source.setAccessible(true);
        source.set(runtime, new AnimationContext<>(null, null, null, null));
        var evaluate = AnimationProgramRuntime.class.getDeclaredMethod("evaluate", AnimationProgramRuntime.Step.class);
        evaluate.setAccessible(true);
        runtime.advance(0, 7, step -> {
            try { return (AnimationProgramRuntime.Snapshot)evaluate.invoke(runtime, step); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        var snapshot = runtime.snapshot(); assertNotNull(snapshot);
        assertInstanceOf(Boolean.class, snapshot.variables().get("flag"));
        assertEquals(Boolean.TRUE, snapshot.variables().get("flag"));
        assertEquals("safe", com.micaftic.morpher.molang.runtime.binding.ValueConversions.asString(snapshot.variables().get("label")));
        assertEquals(1f, snapshot.variables().get("motion"));
    }
    @Test void readOnlyBoneConsumerInAnotherClipNeedsAProvenPhase() {
        var f = simple(); assertTrue(f.bind().getFirst().accepted());
        var file = new RawYsmModel.RawAnimationFile();
        var consumer = new RawYsmModel.RawAnimation(); consumer.name = "reader";
        var bone = new RawYsmModel.RawBoneAnimation(); bone.boneName = "other-bone";
        var frame = new RawYsmModel.RawKeyframe(); frame.postData = new Object[]{"v.motion", 0f, 0f};
        bone.rotation.add(frame); consumer.boneAnimations.add(bone); file.animations.put(consumer.name, consumer);
        var files = new LinkedHashMap<>(f.files); files.put("other", new AnimationFile(AnimationMapper.buildAnimations(file, false)));
        var diagnostics = AnimationProgramAnalyzer.bind(files, f.controllers, Map.of(), "phase");
        assertFalse(diagnostics.getFirst().accepted());
        assertTrue(diagnostics.getFirst().reasons().stream().anyMatch(r -> r.contains("consumer phase outside")));
    }
    @Test void failedParseCannotShiftTemporaryBindingsInTheNextExpression() throws Exception {
        var parser = new MolangParser(Map.of());
        assertFalse(((MolangValue)parser.parseExpression("t.failed=1;missing.value;", false)).parsedSuccessfully());
        var reused = (MolangValue)parser.parseExpressionUnsafe("t.actual=2;t.actual;", false);
        var fresh = (MolangValue)new MolangParser(Map.of()).parseExpressionUnsafe("t.actual=2;t.actual;", false);
        var actual = ((AssignableVariableExpression)((BinaryExpression)reused.expressions().getFirst()).left()).target();
        var expected = ((AssignableVariableExpression)((BinaryExpression)fresh.expressions().getFirst()).left()).target();
        assertEquals(expected, actual);
    }
}
