package com.micaftic.morpher.resource;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AnimationParticleImportTest {
    @Test
    void effectsAndQuotedLocatorNamesRemainValidMolangWithOrWithoutAStartupScript() {
        var root = new com.google.gson.JsonObject();
        var effects = new com.google.gson.JsonObject();
        var events = new com.google.gson.JsonArray();
        for (String script : java.util.List.of("", "v.particle=7;", "v.particle=7")) {
            var event = new com.google.gson.JsonObject();
            event.addProperty("effect", "minecraft:dust{color:[1,0,0],scale:1}");
            event.addProperty("locator", "Hand'Locator");
            event.addProperty("pre_effect_script", script);
            events.add(event);
        }
        effects.add("0", events);
        root.add("particle_effects", effects);
        var frames = AnimationParticleEvents.read(root);
        assertEquals(3, frames.size());
        var function = new com.micaftic.morpher.client.animation.molang.functions.ysm.AnimationParticleFunction();
        com.micaftic.morpher.molang.runtime.binding.ObjectBinding binding = name ->
                "animation_particle".equals(name) ? function : null;
        var parser = new com.micaftic.morpher.geckolib3.core.molang.MolangParser(java.util.Map.of("ysm", binding));
        for (var frame : frames) {
            var value = (com.micaftic.morpher.geckolib3.core.molang.value.MolangValue)
                    parser.parseExpression(frame.events.getFirst(), false);
            assertTrue(value.parsedSuccessfully(), value.failure());
        }
    }

    @Test
    void importedParticleInstructionsSurviveTheExistingBinaryFormat() {
        var file = YsmAnimationParsing.parseAnimationFile("""
                {"animations":{"test":{"animation_length":1,
                  "particle_effects":{"0.5":{"effect":"minecraft:smoke"}}
                }}}
                """.getBytes(StandardCharsets.UTF_8));
        file.animType = 1;
        var model = new com.micaftic.morpher.resource.pojo.RawYsmModel();
        model.mainEntity.animationFiles.put("main", file);
        byte[] bytes;
        try (var buffer = YSMBinarySerializer.serialize(model, 32, false)) {
            bytes = buffer.toArray();
        }
        var restored = new YSMBinaryDeserializer(bytes, 32).deserialize();
        var before = file.animations.get("test").timelineEvents.getFirst();
        var after = restored.mainEntity.animationFiles.get("main").animations.get("test").timelineEvents.getFirst();
        assertEquals(before.timestamp, after.timestamp);
        assertEquals(before.events, after.events);
    }

    @Test
    void importsParticleEventsIntoThePersistedTimelineBeforeLaterInstructions() {
        String json = """
                {"animations":{"test":{"animation_length":2,
                  "timeline":{"1.0":"v.tail=1;"},
                  "particle_effects":{"0.5":{
                    "effect":"minecraft:smoke",
                    "locator":"HandLocator",
                    "pre_effect_script":"v.particle=7;"
                  }}
                }}}
                """;
        var animation = YsmAnimationParsing.parseAnimationFile(json.getBytes(StandardCharsets.UTF_8))
                .animations.get("test");

        assertEquals(2, animation.timelineEvents.size(), "particle event must survive JSON import");
        assertEquals(.5f, animation.timelineEvents.getFirst().timestamp);
        assertEquals(1f, animation.timelineEvents.getLast().timestamp);
        assertEquals("v.tail=1;", animation.timelineEvents.getLast().events.getFirst());
    }
}
