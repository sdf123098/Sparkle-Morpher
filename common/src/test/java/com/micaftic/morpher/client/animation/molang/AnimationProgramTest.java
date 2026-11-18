package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.core.HostAnimationAssets.*;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AnimationProgramTest {
    private static MolangContext emptyContext() {
        return new MolangContext(Map.of(), Map.of(), Map.of(),
                new ScriptBudget(10_000, 16, new CancellationToken()));
    }

    private record Fixture(
            Map<String, AnimationClipSet> files,
            List<AnimationControllerSet> controllers,
            ClipSpec animation) {
        List<AnimationProgramPlan.Diagnostic> bind() {
            return AnimationProgramAnalyzer.bind(files, controllers, Map.of(), "test");
        }
    }

    private Fixture fixture(String name, String variable, String script, String... consumers) {
        var file = new RawYsmModel.RawAnimationFile();
        var animation = new RawYsmModel.RawAnimation();
        animation.name = name;
        animation.length = .023f;
        animation.loopMode = 1;
        var timeline = new RawYsmModel.RawTimelineEvent();
        timeline.events.add(script);
        animation.timelineEvents.add(timeline);
        var bone = new RawYsmModel.RawBoneAnimation();
        bone.boneName = "arbitrary";
        for (var channel : List.of(bone.rotation, bone.position, bone.scale)) {
            var key = new RawYsmModel.RawKeyframe();
            key.postData = new Object[]{"v." + variable, 0f, 1f};
            channel.add(key);
            // Multiple keyframes and multi-turn values must stay in the existing channel sampler.
            var end = new RawYsmModel.RawKeyframe();
            end.timestamp = .023f;
            end.postData = new Object[]{"v." + variable + "+720", 0f, 1f};
            channel.add(end);
        }
        animation.boneAnimations.add(bone);
        file.animations.put(name, animation);
        for (int i = 0; i < consumers.length; i++) {
            var other = new RawYsmModel.RawAnimation();
            other.name = "other" + i;
            var frame = new RawYsmModel.RawTimelineEvent();
            frame.events.add(consumers[i]);
            other.timelineEvents.add(frame);
            file.animations.put(other.name, other);
        }
        var mapped = AnimationMapper.buildAnimations(file, false);
        var rawController = new RawYsmModel.RawAnimationController();
        rawController.animationName = "controller";
        rawController.initialState = "default";
        var state = new RawYsmModel.RawControllerState();
        state.name = "default";
        state.animations.add(Map.entry(name, ""));
        rawController.states.add(state);
        return new Fixture(
                Map.of("main", new AnimationClipSet(mapped)),
                new ArrayList<>(List.of(new AnimationControllerSet(
                        AnimationMapper.buildControllers(Map.of("controller", rawController), false)))),
                mapped.get(name));
    }

    private Fixture simple() {
        return fixture("arbitrary", "motion", "v.motion=ysm.second_order('spring',math.sin(q.life_time*40),2,0.5,1);");
    }

    @Test void parseFailureIsNotAValidZeroAndSyntaxIsRejected() {
        HostMolangScript valid = HostMolangScript.compile("0");
        HostMolangScript failedSyntax = HostMolangScript.compile("v.x=(");
        HostMolangScript unknownEffect = HostMolangScript.compile("v.x=unknown.effect(1)");
        assertTrue(valid.parsedSuccessfully());
        assertFalse(failedSyntax.parsedSuccessfully());
        assertEquals(0f, failedSyntax.evaluateAsFloat(emptyContext()));
        assertFalse(AnimationProgramAnalyzer.inspect(failedSyntax).reasons().isEmpty());
        assertFalse(AnimationProgramAnalyzer.inspect(unknownEffect).reasons().isEmpty());
    }

    @Test void symbolsRetainOriginalNamespacesAndBindingIdentity() {
        HostMolangScript value = HostMolangScript.compile("variable.private_name=ysm.first_order('spring',q.life_time);");
        var facts = AnimationProgramAnalyzer.inspect(value);
        assertEquals(Set.of("private_name"), facts.writes());
        assertEquals(Set.of("first:spring"), facts.physicsNodes());
        assertTrue(facts.reasons().isEmpty());
    }

    @Test void automaticPlanIgnoresModelAnimationBoneAndPrivateVariableNames() {
        for (String name : List.of("first", "renamed")) {
            var f = fixture(name, name, "variable." + name + "=ysm.first_order('node',math.sin(q.life_time*30),4);");
            assertTrue(f.bind().getFirst().accepted(), () -> f.bind().toString());
            var plan = f.animation.programPlan();
            assertNotNull(plan);
            assertEquals((double) .023f, plan.period(), 0);
            assertEquals(6, plan.poseExpressions().size());
            assertEquals(1, f.animation.raw().timelineEvents.size(), "binding must not erase the event timeline");
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
        var sfx = new RawYsmModel.RawSoundEffect();
        sfx.timestamp = 0f;
        sfx.effectName = "sound";
        mixed.animation.raw().soundEffects.add(sfx);
        assertFalse(mixed.bind().getFirst().accepted());
        var dynamic = fixture("dynamic", "motion", "v.motion=ysm.first_order(v.motion,1);");
        assertFalse(dynamic.bind().getFirst().accepted());
    }

    @Test void controllerFeedbackHistoryAndConditionalPersistenceAreRejected() {
        var f = simple();
        var controller = f.controllers.getFirst().getAnimationControllers().values().iterator().next();
        var state = controller.states().values().iterator().next();
        var stateWithFeedback = new ControllerStateSpec(
                "default",
                state.raw(),
                List.of(new BlendEntry("arbitrary", HostMolangScript.compile("v.motion"))),
                List.of(),
                List.of(),
                List.of());
        var replaced = new ControllerSpec("controller", "default", Map.of("default", stateWithFeedback), controller.raw());
        f.controllers.set(0, new AnimationControllerSet(Map.of("controller", replaced)));
        assertFalse(f.bind().getFirst().accepted());
        var history = fixture("history", "motion", "v.motion=ysm.first_order('spring',v.previous);v.previous=q.life_time;");
        assertFalse(history.bind().getFirst().accepted());
        var conditional = fixture("conditional", "motion", "q.is_on_ground ? {v.motion=ysm.first_order('spring',1);};");
        assertFalse(conditional.bind().getFirst().accepted());
    }

    @Test void analysisNeverExecutesAFunction() {
        HostMolangScript value = HostMolangScript.compile("v.x=custom.side_effect();");
        var facts = AnimationProgramAnalyzer.inspect(value);
        assertTrue(facts.unknownEffect());
    }

    @Test void guiWriterAndUnserviceablePeriodPreventTakeover() {
        var f = simple();
        var diagnostics = AnimationProgramAnalyzer.bind(f.files, f.controllers, Map.of(), "ui",
                List.of(HostMolangScript.compile("v.motion=0")));
        assertFalse(diagnostics.getFirst().accepted());
        var fast = simple();
        fast.animation.raw().length = .001f;
        assertTrue(fast.bind().getFirst().reasons().contains("period exceeds the eight-step client-tick budget"));
        var slow = simple();
        slow.animation.raw().length = .5f;
        assertFalse(slow.bind().getFirst().accepted());
    }

    @Test void knownEntityInputIsRecordedButUnknownBindingIsNotTrusted() {
        var f = fixture("input", "motion", "v.motion=ysm.first_order('node',q.is_on_ground,2);");
        assertTrue(f.bind().getFirst().accepted());
        assertEquals(Set.of("is_on_ground"), new HashSet<>(f.animation.programPlan().inputs().values()));
        HostMolangScript injected = HostMolangScript.compile("v.x=injected.life_time");
        assertFalse(AnimationProgramAnalyzer.inspect(injected).reasons().isEmpty());
    }

    @Test void knownPhysicsBindingUsesOnlyProgramManager() {
        var physics = new PhysicsManager();
        int shared = com.micaftic.morpher.client.render.BoneIds.id("shared");
        physics.updateSimulation(0, true);
        assertEquals(3f, physics.sampleFirst(shared, 3f, 2f));
        physics.updateSimulation(.02, false);
        float moved = physics.sampleFirst(shared, 9f, 2f);
        assertTrue(moved > 3 && moved < 9);
        assertEquals(moved, physics.sampleFirst(shared, 3f, 2f));
    }

    private AnimationProgramPlan runtimePlan(Map<String, String> inputs) {
        return new AnimationProgramPlan("test", "test", .023, List.of(), Set.of(), Set.of("value"), Set.of("first:node"), inputs,
                Map.of(), List.of(), AnimationProgramPlan.Phase.TIMELINE_START, AnimationProgramPlan.Recovery.REBUILD_PHYSICS_AND_OVERWRITE);
    }

    @Test void replayAtFiveFrameRatesHasIdenticalAcceptedStepsAndPhysicsTrajectory() {
        List<String> reference = null;
        for (int fps : new int[]{15, 30, 60, 144, 240}) {
            var runtime = new AnimationProgramRuntime(runtimePlan(Map.of("q.sample", "sample")), 5);
            var physics = new PhysicsManager();
            List<String> trajectory = new ArrayList<>();
            double[] lastRecorded = {-1};
            AnimationProgramRuntime.Executor execute = step -> {
                if (step.recovery()) physics.clear();
                physics.updateSimulation(step.time(), step.recovery());
                float value = physics.sampleFirst(7, step.inputs().get("q.sample").floatValue(), 4);
                trajectory.add(step.id() + ":" + step.time() + ":" + value);
                return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(),
                        Map.of("value", MolangValue.number(value)), Map.of(), step.recovery());
            };
            for (int frame = 0; frame <= fps * 3; frame++) {
                double time = frame / (double) fps;
                for (int tick = (int) lastRecorded[0] + 1; tick <= Math.floor(time * 20 + 1e-7); tick++) {
                    runtime.recordInput(tick / 20.0, Map.of("q.sample", Math.sin(tick * .17)));
                    lastRecorded[0] = tick;
                }
                runtime.advance(time, 5, execute);
                runtime.advance(time, 5, execute);
            }
            if (reference == null) reference = trajectory; else assertEquals(reference, trajectory, "fps=" + fps);
            assertEquals(131, trajectory.size());
        }
    }

    @Test void missingHistoryRecoversAndOldGenerationCannotPublish() {
        var runtime = new AnimationProgramRuntime(runtimePlan(Map.of("q.sample", "sample")), 9);
        List<AnimationProgramRuntime.Step> steps = new ArrayList<>();
        AnimationProgramRuntime.Executor execute = step -> {
            steps.add(step);
            return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(), Map.of(), Map.of(), step.recovery());
        };
        runtime.recordInput(0, Map.of("q.sample", 1.0));
        runtime.advance(0, 9, execute);
        runtime.recordInput(.1, Map.of("q.sample", 2.0));
        runtime.advance(.1, 9, execute);
        assertEquals(2, steps.size());
        assertTrue(steps.getLast().recovery());
        runtime.advance(.09, 9, execute);
        assertEquals(2, steps.size());
        runtime.advance(.12, 8, execute);
        assertEquals(2, steps.size());
        runtime.close();
        runtime.advance(1, 9, execute);
        assertNull(runtime.snapshot());
    }

    @Test void longPauseRebuildsOnceAndCloseDuringEvaluationDiscardsSnapshot() {
        var runtime = new AnimationProgramRuntime(runtimePlan(Map.of()), 1);
        int[] calls = {0};
        AnimationProgramRuntime.Executor execute = step -> {
            calls[0]++;
            return new AnimationProgramRuntime.Snapshot(step.generation(), step.id(), step.time(),
                    Map.of("value", MolangValue.number(1.0)), Map.of(), step.recovery());
        };
        runtime.advance(0, 1, execute);
        runtime.advance(100, 1, execute);
        assertEquals(2, calls[0]);
        assertTrue(runtime.snapshot().recovery());
        runtime.advance(100.03, 1, step -> { runtime.close(); return execute.execute(step); });
        assertNull(runtime.snapshot());
    }

    @Test void evaluatorReusesOriginalOrderedScriptAndSnapshotSupportsEveryChannel() {
        var script = HostMolangScript.compile("v.a=2;v.b=v.a+3;v.a=7;");
        var context = emptyContext();
        script.evaluateValue(context);
        assertEquals(5f, HostMolangScript.compile("v.b").evaluateAsFloat(context));
        var pose = HostMolangScript.compile("720+v.a");
        assertEquals(727f, pose.evaluateAsFloat(context), 0);
        assertTrue(pose.evaluateAsBoolean(context));
    }

    @Test void runtimePublishesOriginalBooleanAndStringStateTypes() throws Exception {
        var f = fixture("typed", "motion", "v.motion=1;v.flag=(1==1);v.label='safe';");
        assertTrue(f.bind().getFirst().accepted());
        var runtime = new AnimationProgramRuntime(f.animation.programPlan(), 7);
        var evaluate = AnimationProgramRuntime.class.getDeclaredMethod("evaluate", AnimationProgramRuntime.Step.class);
        evaluate.setAccessible(true);
        runtime.advance(0, 7, step -> {
            try { return (AnimationProgramRuntime.Snapshot) evaluate.invoke(runtime, step); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        var snapshot = runtime.snapshot();
        assertNotNull(snapshot);
        assertInstanceOf(MolangValue.BooleanValue.class, snapshot.variables().get("flag"));
        assertTrue(snapshot.variables().get("flag").truth());
        assertEquals("safe", ((MolangValue.TextValue) snapshot.variables().get("label")).value());
        assertEquals(1.0, snapshot.variables().get("motion").number());
    }

    @Test void readOnlyBoneConsumerInAnotherClipNeedsAProvenPhase() {
        var f = simple();
        assertTrue(f.bind().getFirst().accepted());
        var file = new RawYsmModel.RawAnimationFile();
        var consumer = new RawYsmModel.RawAnimation();
        consumer.name = "reader";
        var bone = new RawYsmModel.RawBoneAnimation();
        bone.boneName = "other-bone";
        var frame = new RawYsmModel.RawKeyframe();
        frame.postData = new Object[]{"v.motion", 0f, 0f};
        bone.rotation.add(frame);
        consumer.boneAnimations.add(bone);
        file.animations.put(consumer.name, consumer);
        var files = new LinkedHashMap<>(f.files);
        files.put("other", new AnimationClipSet(AnimationMapper.buildAnimations(file, false)));
        var diagnostics = AnimationProgramAnalyzer.bind(files, f.controllers, Map.of(), "phase");
        assertFalse(diagnostics.getFirst().accepted());
        assertTrue(diagnostics.getFirst().reasons().stream().anyMatch(r -> r.contains("consumer phase outside")));
    }

    @Test void failedParseDoesNotPoisonSubsequentCompilation() {
        assertFalse(HostMolangScript.compile("t.failed=1;(;").parsedSuccessfully());
        var reused = HostMolangScript.compile("t.actual=2;t.actual;");
        assertTrue(reused.parsedSuccessfully());
        assertEquals(2f, reused.evaluateAsFloat(emptyContext()));
    }
}
