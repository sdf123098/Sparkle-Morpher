package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.core.security.YsmCrypt;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KipfelCompatibilityTest {
    @Test void contentFingerprintIncludesGeometryScriptsAndControllers() {
        RawYsmModel raw = new RawYsmModel();
        String original=KipfelCompatibility.fingerprint(raw);
        raw.modelId="other-instance";
        assertEquals(original,KipfelCompatibility.fingerprint(raw));
        raw.mainEntity.mainModel=new RawYsmModel.RawGeometry();
        assertNotEquals(original,KipfelCompatibility.fingerprint(raw));
        assertFalse(KipfelCompatibility.matches(raw));
    }

    @Test void actualBinaryImportMatchesOnlyTheVerifiedModel() throws Exception {
        String dir=System.getenv("SPM_KIPFEL_REFERENCE_DIR");
        assumeTrue(dir!=null,"optional local copyrighted model corpus");
        try(var paths=Files.list(Path.of(dir))) {
            for(Path file:paths.filter(p->p.getFileName().toString().equals("kipfel-v2.ysm")).toList()) {
                RawYsmModel raw;
                try(var parser=new YSMBinaryDeserializer(YsmCrypt.decryptYsmFile(Files.readAllBytes(file)))) {
                    raw=parser.deserializeKeepOpen();
                    parser.parseYSMFooter(raw);
                }
                System.out.println(file.getFileName()+" fingerprint="+KipfelCompatibility.fingerprint(raw));
                assertTrue(KipfelCompatibility.matches(raw),file.toString());
                var animations=new java.util.LinkedHashMap<String,com.micaftic.morpher.geckolib3.file.AnimationFile>();
                raw.mainEntity.animationFiles.forEach((name,data)->animations.put(name,new com.micaftic.morpher.geckolib3.file.AnimationFile(AnimationMapper.buildAnimations(data,raw.properties.mergeMultilineExpr))));
                KipfelCompatibility.bind(raw,animations,"regression-kipfel");
                var all=animations.values().stream().flatMap(f->f.getAnimations().values().stream()).toList();
                assertEquals(3,all.stream().filter(a->a.stableKipfelAttachments).count());
                assertTrue(all.stream().filter(a->a.animationName.equals("头发物理集合")).allMatch(a->a.customInstructionKeyframes.isEmpty()));
                assertTrue(all.stream().filter(a->a.animationName.equals("腿部挂饰粗略物理")).allMatch(a->!a.customInstructionKeyframes.isEmpty()));
                raw.mainEntity.animationFiles.values().iterator().next().animations.values().iterator().next().length+=1;
                assertFalse(KipfelCompatibility.matches(raw));
            }
        }
    }
}
