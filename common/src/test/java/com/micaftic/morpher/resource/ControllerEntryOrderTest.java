package com.micaftic.morpher.resource;

import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerStateSpec;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import dev.spm.core.api.CancellationToken;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControllerEntryOrderTest {
    private static MolangContext emptyContext() {
        return new MolangContext(Map.of(), Map.of(), Map.of(),
                new ScriptBudget(10_000, 16, new CancellationToken()));
    }

    private RawYsmModel fixture() {
        String controller = """
            {"animation_controllers":{"player.pre_parallel_7":{
              "initial_state":"walk","states":{"walk":{
                "animations":[{"walk":"1"},{"other":"0"},{"walk":"0"},"always"],
                "transitions":[{"idle":"1"},{"jump":"0"},{"idle":"0"}]
              },"idle":{},"jump":{}}}}}
            """;
        try (var parser = new YSMFolderDeserializer(Map.of(
                "ysm.json", "{\"files\":{\"player\":{\"animation_controllers\":[\"controller.json\"]}}}".getBytes(StandardCharsets.UTF_8),
                "controller.json", controller.getBytes(StandardCharsets.UTF_8)))) {
            return parser.deserialize();
        }
    }

    private ControllerStateSpec mapped(RawYsmModel raw) {
        var controllers = raw.mainEntity.animationControllerFiles.get(0).controllers;
        return AnimationMapper.buildControllers(controllers, false).get("player.pre_parallel_7")
                .states().get("walk");
    }

    @Test void repeatedAnimationKeepsOrdinaryAndWeaponConditions() throws Exception {
        var clips = mapped(fixture()).animations();
        assertEquals(List.of("walk", "other", "walk", "always"), clips.stream().map(e -> e.animationName()).toList());
        MolangContext ctx = emptyContext();
        assertTrue(clips.get(0).weight().evaluateAsBoolean(ctx));
        assertFalse(clips.get(2).weight().evaluateAsBoolean(ctx));
        assertNull(clips.get(3).weight());
    }

    @Test void repeatedTransitionKeepsFirstMatchingPriority() throws Exception {
        var transitions = mapped(fixture()).transitions();
        assertEquals(List.of("idle", "jump", "idle"), transitions.stream()
                .map(t -> t.targetState()).toList());
        MolangContext ctx = emptyContext();
        assertTrue(transitions.get(0).condition().evaluateAsBoolean(ctx));
        assertFalse(transitions.get(2).condition().evaluateAsBoolean(ctx));
    }

    @Test void serverBinaryRoundTripKeepsRepeatedEntries() throws Exception {
        for (int format : List.of(26, 31, 32)) {
            var raw = fixture();
            raw.mainEntity.mainModel = new RawYsmModel.RawGeometry();
            raw.mainEntity.mainModel.modelType = 1;
            byte[] bytes;
            try (var buf = YSMBinarySerializer.serialize(raw, format, true)) {
                bytes = new byte[buf.getRawBuf().readableBytes()];
                buf.getRawBuf().readBytes(bytes);
            }
            var result = mapped(new YSMBinaryDeserializer(bytes, format).deserialize());
            assertEquals(4, result.animations().size(), "format " + format);
            assertEquals(3, result.transitions().size(), "format " + format);
            assertTrue(result.animations().get(0).weight().evaluateAsBoolean(emptyContext()));
        }
    }
}
