package com.micaftic.morpher.peer;

import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Optional read-only probe of the user's verified received bytes; no game/GL/model copying. */
class PeerReceivedModelProbeTest {
    @Test void receivedYsmPassesTheSamePeerValidationAndParsingAsTheClient() throws Exception {
        String directory = System.getenv("SPM_PEER_FIXTURE_ROOT"); assumeTrue(directory != null);
        Path root = Path.of(directory); int count = 0;
        try(var entries = Files.list(root.resolve("assets"))) {
            for(Path entry : entries.toList()) {
                if(!entry.getFileName().toString().matches("[0-9a-f]{64}")) continue;
                var metadata=PeerSafeFiles.existing(entry,"manifest.bin");assertTrue(Files.size(metadata)<=PeerLimits.MANIFEST_BYTES);
                var manifest=PeerAssetManifest.decode(Files.readAllBytes(metadata));assertEquals(entry.getFileName().toString(),manifest.contentRoot().sha256());
                var asset=new PeerAssetStore.Verified(manifest,entry.resolve("files"));PeerAssetStore.verify(asset,()->true);
                if(!asset.manifest().entrypoint().endsWith(".ysm")) continue;
                try(var scope = new PeerDecodeScope()) {
                    var identity = new PeerModelIdentity(asset.manifest().contentRoot(), "ysm-v1");
                    PeerModelPreflight.validate(identity, asset);
                    byte[] bytes = Files.readAllBytes(asset.entrypoint()); RawYsmModel raw;
                    int crypto = LegacyCompatModelFormat.detectCryptoVersion(bytes);
                    if(crypto == 1 || crypto == 2) {
                        try(var decoder = new YSMFolderDeserializer(LegacyCompatModelFormat.read(bytes))) { raw = decoder.deserialize(); }
                    } else {
                        try(var decoder = new YSMBinaryDeserializer(YsmCrypt.decryptYsmFile(bytes))) { raw = decoder.deserializeKeepOpen(); decoder.parseYSMFooter(raw); }
                    }
                    System.out.println("Peer received root=" + identity.contentRoot().sha256() + " textures=" + raw.mainEntity.textures.size());
                    PeerModelPreflight.validateParsed(raw);
                    try(var reservation=PeerResidentReservation.raw(raw,manifest.totalBytes())) {assertTrue(PeerResidentReservation.CPU.used()>0);}
                    assertNotNull(raw.mainEntity.mainModel); count++;
                }
            }
        }
        assertTrue(count > 0, "No received YSM fixtures");
    }
}
