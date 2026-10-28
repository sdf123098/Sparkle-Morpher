package com.micaftic.morpher.geckolib3.util.json;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.EasingType;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.MolangParser;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluatorImpl;
import com.micaftic.morpher.resource.YsmAnimationParsing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimationJsonCompatibilityTest {
    @Test
    void stringLoopModesKeepTheDeclaredPlaybackLifecycle() {
        assertSame(ILoopType.EDefaultLoopTypes.LOOP, ILoopType.fromJson(new JsonPrimitive("loop")));
        assertSame(ILoopType.EDefaultLoopTypes.LOOP, ILoopType.fromJson(new JsonPrimitive("true")));
        assertSame(ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME,
                ILoopType.fromJson(new JsonPrimitive("hold_on_last_frame")));
        var file = YsmAnimationParsing.parseAnimationFile(
                "{\"animations\":{\"looping\":{\"loop\":\"loop\"}}}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, file.animations.get("looping").loopMode);
    }

    @Test
    void explicitCatmullRomIsRetained() {
        var frames = new ArrayList<RawBoneKeyFrame>();
        JsonKeyFrameUtils.getKeyFrames(frames,
                JsonParser.parseString("{\"0\":{\"post\":[1,2,3],\"lerp_mode\":\"catmullrom\"}}"),
                new MolangParser(Map.of()));
        assertSame(EasingType.CATMULLROM, frames.getFirst().easingType);
    }

    @Test
    void channelExpressionsAreParsedAsMolangInsteadOfJsonText() {
        var frames = new ArrayList<RawBoneKeyFrame>();
        JsonKeyFrameUtils.getKeyFrames(frames,
                JsonParser.parseString("{\"0\":[\"1 + 2\",\"2 * 3\",\"9 - 2\"]}"),
                new MolangParser(Map.of()));
        var evaluator = new ExpressionEvaluatorImpl<>(null);
        assertEquals(3f, frames.getFirst().preXValue.evalAsFloat(evaluator));
        assertEquals(6f, frames.getFirst().preYValue.evalAsFloat(evaluator));
        assertEquals(7f, frames.getFirst().preZValue.evalAsFloat(evaluator));
    }
}
