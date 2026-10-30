package com.micaftic.morpher.resource;

import com.micaftic.morpher.geckolib3.core.molang.MolangParser;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class ControllerEntryOrderTest {
    private Queue<MolangParser> pool;
    private List<MolangParser> saved;

    @SuppressWarnings("unchecked")
    @BeforeEach void isolateBindings() throws Exception {
        var field = GeckoLibCache.class.getDeclaredField("PARSER_POOL");
        field.setAccessible(true);
        pool = (Queue<MolangParser>) field.get(null);
        saved = new ArrayList<>(pool);
        pool.clear();
        pool.add(new MolangParser(Map.of()));
    }

    @AfterEach void restoreBindings() { pool.clear(); pool.addAll(saved); }

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

    private com.micaftic.morpher.geckolib3.core.builder.AnimationState mapped(RawYsmModel raw) {
        var controllers = raw.mainEntity.animationControllerFiles.get(0).controllers;
        return AnimationMapper.buildControllers(controllers, false).get("player.pre_parallel_7")
                .getStates().get(StringPool.computeIfAbsent("walk"));
    }

    @Test void repeatedAnimationKeepsOrdinaryAndWeaponConditions() {
        var clips = mapped(fixture()).getAnimations();
        assertEquals(List.of("walk", "other", "walk", "always"), clips.stream().map(p -> p.getKey()).toList());
        assertTrue(clips.get(0).getValue().evalAsBoolean(ExpressionEvaluator.evaluator()));
        assertFalse(clips.get(2).getValue().evalAsBoolean(ExpressionEvaluator.evaluator()));
        assertNull(clips.get(3).getValue());
    }

    @Test void repeatedTransitionKeepsFirstMatchingPriority() {
        var transitions = mapped(fixture()).getTransitions();
        assertEquals(List.of("idle", "jump", "idle"), transitions.stream()
                .map(p -> StringPool.getString(p.leftInt())).toList());
        assertTrue(transitions.get(0).right().evalAsBoolean(ExpressionEvaluator.evaluator()));
        assertFalse(transitions.get(2).right().evalAsBoolean(ExpressionEvaluator.evaluator()));
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
            assertEquals(4, result.getAnimations().size(), "format " + format);
            assertEquals(3, result.getTransitions().size(), "format " + format);
            assertTrue(result.getAnimations().get(0).getValue().evalAsBoolean(ExpressionEvaluator.evaluator()));
        }
    }
}
