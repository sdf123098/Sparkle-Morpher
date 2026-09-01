package com.micaftic.morpher.resource.gltf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfAnimationControllerStateTest {

    @Test
    void resolvesExpandedStatesByAlias() {
        GltfAnimationController controller = new GltfAnimationController(
                modelWithAnimations("idle", "fly", "swim", "sneak", "sleep", "ride", "ladder_up", "climb", "elytra_fly", "swim_stand", "attacked"));

        controller.selectState(GltfAnimationController.State.FLY, 1.0f);
        assertEquals("fly", controller.animationName());
        controller.selectState(GltfAnimationController.State.ELYTRA_FLY, 2.0f);
        assertEquals("elytra_fly", controller.animationName());
        controller.selectState(GltfAnimationController.State.SWIM, 3.0f);
        assertEquals("swim", controller.animationName());
        controller.selectState(GltfAnimationController.State.SWIM_STAND, 4.0f);
        assertEquals("swim_stand", controller.animationName());
        controller.selectState(GltfAnimationController.State.SNEAK, 5.0f);
        assertEquals("sneak", controller.animationName());
        controller.selectState(GltfAnimationController.State.SLEEP, 6.0f);
        assertEquals("sleep", controller.animationName());
        controller.selectState(GltfAnimationController.State.RIDE, 7.0f);
        assertEquals("ride", controller.animationName());
        controller.selectState(GltfAnimationController.State.ATTACKED, 8.0f);
        assertEquals("attacked", controller.animationName());
        controller.selectState(GltfAnimationController.State.CLIMB, 9.0f);
        assertEquals("climb", controller.animationName());
        controller.selectState(GltfAnimationController.State.LADDER_UP, 10.0f);
        assertEquals("ladder_up", controller.animationName());
    }

    @Test
    void resolvesNewStatesByCommonAliases() {
        GltfAnimationController controller = new GltfAnimationController(
                modelWithAnimations("idle", "flying", "swimming", "crouch", "sleeping", "riding", "climb_up"));

        controller.selectState(GltfAnimationController.State.FLY, 1.0f);
        assertEquals("flying", controller.animationName());
        controller.selectState(GltfAnimationController.State.SWIM, 2.0f);
        assertEquals("swimming", controller.animationName());
        controller.selectState(GltfAnimationController.State.SNEAK, 3.0f);
        assertEquals("crouch", controller.animationName());
        controller.selectState(GltfAnimationController.State.SLEEP, 4.0f);
        assertEquals("sleeping", controller.animationName());
        controller.selectState(GltfAnimationController.State.RIDE, 5.0f);
        assertEquals("riding", controller.animationName());
        controller.selectState(GltfAnimationController.State.CLIMBING, 6.0f);
        assertEquals("climb_up", controller.animationName());
    }

    @Test
    void fallsBackToFirstPlayableWhenExpandedStateAliasIsAbsent() {
        GltfAnimationController controller = new GltfAnimationController(modelWithAnimations("idle"));

        controller.selectState(GltfAnimationController.State.FLY, 2.0f);

        assertEquals(GltfAnimationController.State.FLY, controller.state());
        assertEquals(0, controller.animationIndex());
        assertEquals("idle", controller.animationName());
    }

    @Test
    void playByNameStillWorksForCustomAnimations() {
        GltfAnimationController controller = new GltfAnimationController(
                modelWithAnimations("idle", "wave", "dance"));

        boolean played = controller.play("dance", 3.0f);

        assertTrue(played);
        assertEquals(GltfAnimationController.State.CUSTOM, controller.state());
        assertEquals("dance", controller.animationName());
        assertFalse(controller.play("missing", 4.0f));
    }

    private static GltfModel modelWithAnimations(String... names) {
        GltfModel.Node node = new GltfModel.Node("root", -1, List.of(), -1, -1,
                new float[]{0, 0, 0}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1}, null);
        List<GltfModel.Animation> animations = java.util.Arrays.stream(names)
                .map(name -> new GltfModel.Animation(name, List.of(), 1.0f))
                .toList();
        return new GltfModel(null,
                List.of(new GltfModel.Scene("scene", List.of(0))), 0,
                List.of(node), List.of(), List.of(), List.of(), List.of(), List.of(), animations);
    }
}
