package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.geckolib3.file.*;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional local corpus: no copyrighted assets are shipped in the repository. */
class AnimationProgramCorpusTest {
    @Test void analyzeActualImportedModelsWithoutExecutingTheirScripts() throws Exception {
        String directory = System.getenv("SPM_PROGRAM_CORPUS");
        assumeTrue(directory != null, "optional local model corpus");
        int limit = Integer.parseInt(System.getenv().getOrDefault("SPM_PROGRAM_CORPUS_LIMIT", "40"));
        List<Map<String, Object>> results = new ArrayList<>();
        try (var paths = Files.walk(Path.of(directory))) {
            var candidates = paths.filter(p -> p.toString().endsWith(".ysm")).sorted().limit(limit).toList();
            for (Path path : candidates) {
                Map<String, Object> result = new LinkedHashMap<>(); result.put("file", path.toString());
                try {
                    RawYsmModel raw;
                    try (var parser = new YSMBinaryDeserializer(YsmCrypt.decryptYsmFile(Files.readAllBytes(path)))) {
                        raw = parser.deserializeKeepOpen(); parser.parseYSMFooter(raw);
                    }
                    Map<String, AnimationFile> animations = new LinkedHashMap<>();
                    raw.mainEntity.animationFiles.forEach((name, file) -> animations.put(name, new AnimationFile(AnimationMapper.buildAnimations(file, raw.properties.mergeMultilineExpr))));
                    List<AnimationControllerFile> controllers = new ArrayList<>();
                    raw.mainEntity.animationControllerFiles.forEach(file -> controllers.add(new AnimationControllerFile(AnimationMapper.buildControllers(file.controllers, raw.properties.mergeMultilineExpr))));
                    Map<String, IValue> functions = new LinkedHashMap<>();
                    raw.functionFiles.forEach((name, file) -> functions.put(name, GeckoLibCache.getMolangParser().parseExpression(new String(file.data, StandardCharsets.UTF_8), true)));
                    var diagnostics = AnimationProgramAnalyzer.bind(animations, controllers, functions, path.getFileName().toString(),
                            com.micaftic.morpher.resource.bundle.GuiConfigMapper.programExpressions(raw.properties));
                    result.put("programs", diagnostics.stream().map(d -> Map.of("resource", d.resource(),
                            "period", Double.isFinite(d.period()) ? (Object)d.period() : "non-finite",
                            "reads", d.reads(), "writes", d.writes(), "physicsNodes", d.physicsNodes(), "reasons", d.reasons().stream().limit(12).toList())).toList());
                    result.put("accepted", diagnostics.stream().filter(AnimationProgramPlan.Diagnostic::accepted).count());
                } catch (Exception | LinkageError error) { result.put("error", error.toString()); }
                results.add(result);
                String checkpoint = System.getenv("SPM_PROGRAM_CORPUS_OUTPUT");
                if (checkpoint != null) Files.writeString(Path.of(checkpoint), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
            }
        }
        String output = System.getenv("SPM_PROGRAM_CORPUS_OUTPUT");
        if (output != null) Files.writeString(Path.of(output), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
        System.out.println("Corpus models=" + results.size() + " accepted=" + results.stream().mapToLong(r -> ((Number)r.getOrDefault("accepted", 0)).longValue()).sum());
    }
}
