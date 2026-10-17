package com.micaftic.morpher.peer;

import com.micaftic.morpher.resource.*;
import com.micaftic.morpher.resource.bbmodel.*;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.core.security.YsmCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Legal synthetic geometry/texture fixtures; invokes the production format adapters, without GL or a game. */
class PeerProfileFixturesTest {
    @TempDir Path temporary;
    private static final String GEO="""
        {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.peer","texture_width":16,"texture_height":16},"bones":[{"name":"root","pivot":[0,0,0],"cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]}]}
        """;
    private static final String YSM="""
        {"metadata":{"name":"Peer fixture"},"properties":{"default_texture":"texture"},"files":{"player":{"model":{"main":"geometry.geo.json"},"texture":{"uv":"texture.png"}}}}
        """;
    private static final String BB="""
        {"meta":{"format_version":"4.5","model_format":"free"},"resolution":{"width":16,"height":16},"name":"Peer fixture","elements":[{"uuid":"cube","type":"cube","name":"cube","from":[0,0,0],"to":[1,1,1],"faces":{"north":{"uv":[0,0,1,1],"texture":0}}}],"outliner":[{"uuid":"bone","name":"root","origin":[0,0,0],"children":["cube"]}],"textures":[{"name":"texture","source":"data:image/png;base64,%s"}],"animations":[]}
        """;
    private static byte[] png()throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"png",out);return out.toByteArray();}
    private static byte[] zip(Map<String,byte[]> entries)throws Exception {var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){for(var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return bytes.toByteArray();}
    private PeerAssetStore.Verified fixture(Path source,String entry,String profile)throws Exception {
        Map<String,PeerAssetManifest.Role> resources=new HashMap<>();try(var files=Files.walk(source)){for(var path:files.filter(Files::isRegularFile).toList())resources.put(source.relativize(path).toString().replace('\\','/'),path.equals(source.resolve(entry))?PeerAssetManifest.Role.MODEL:PeerAssetManifest.Role.TEXTURE);}
        try(var store=new PeerAssetStore(temporary.resolve("cache-"+profile));var snapshot=store.freeze(new LocalShareSourceResolver.Selection(source,entry,profile,resources),()->true)){return store.copy(snapshot,()->true);}
    }
    @Test void allNineNegotiatedProfilesHaveLegalDependencyAndParserFixtures()throws Exception {
        byte[] texture=png();String bb=BB.formatted(Base64.getEncoder().encodeToString(texture));Path folder=Files.createDirectory(temporary.resolve("folder"));Files.writeString(folder.resolve("ysm.json"),YSM);Files.writeString(folder.resolve("geometry.geo.json"),GEO);Files.write(folder.resolve("texture.png"),texture);
        RawYsmModel raw;try(var parser=new YSMFolderDeserializer(folder)){raw=parser.deserialize();}assertFalse(raw.mainEntity.mainModel.bones.isEmpty());
        var directory=fixture(folder,"ysm.json","ysm-directory-v1");try(var scope=new PeerDecodeScope()){PeerModelPreflight.validate(new PeerModelIdentity(directory.manifest().contentRoot(),"ysm-directory-v1"),directory);PeerModelPreflight.validateParsed(raw);}
        Map<String,byte[]> models=new LinkedHashMap<>();models.put("ysm-zip-v1",zip(Map.of("ysm.json",YSM.getBytes(StandardCharsets.UTF_8),"geometry.geo.json",GEO.getBytes(StandardCharsets.UTF_8),"texture.png",texture)));
        models.put("bbmodel-zip-v1",zip(Map.of("model.bbmodel",bb.getBytes(StandardCharsets.UTF_8))));models.put("figura-geometry-v1",zip(Map.of("avatar.json","{\"name\":\"Peer fixture\"}".getBytes(StandardCharsets.UTF_8),"model.bbmodel",bb.getBytes(StandardCharsets.UTF_8))));
        models.put("bedrock-v1",zip(Map.of("geometry.geo.json",GEO.getBytes(StandardCharsets.UTF_8),"texture.png",texture)));models.put("bbmodel-v1",bb.getBytes(StandardCharsets.UTF_8));
        try(var body=YSMBinarySerializer.serialize(raw,27,true)){byte[] content=body.toArray();var header=ByteBuffer.allocate(content.length+4).order(ByteOrder.LITTLE_ENDIAN).putInt(27).put(content);models.put("ysm-v1",YsmCrypt.encryptYsmFile(header.array()));}
        for(var entry:models.entrySet()) {
            String profile=entry.getKey();Path source=Files.createDirectory(temporary.resolve(profile));String filename=profile.equals("ysm-v1")?"model.ysm":profile.equals("bbmodel-v1")?"model.bbmodel":"model.zip";Files.write(source.resolve(filename),entry.getValue());var asset=fixture(source,filename,profile);
            try(var scope=new PeerDecodeScope()) {
                PeerModelPreflight.validate(new PeerModelIdentity(asset.manifest().contentRoot(),profile),asset);RawYsmModel parsed;
                if(profile.equals("ysm-v1")){try(var decoder=new YSMBinaryDeserializer(YsmCrypt.decryptYsmFile(entry.getValue()))){parsed=decoder.deserializeKeepOpen();decoder.parseYSMFooter(parsed);}}
                else if(profile.equals("ysm-zip-v1")){try(var decoder=new YSMFolderDeserializer(asset.entrypoint())){parsed=decoder.deserialize();}}
                else if(profile.equals("bbmodel-v1"))parsed=BBToRawConverter.convert(BBModelParser.parse(bb));
                else {var sniff=ZipModelSniffer.sniff(entry.getValue(),PeerLimits.EXPANDED_BYTES);if(profile.equals("bedrock-v1")){parsed=new RawYsmModel();parsed.mainEntity.mainModel=YSMFolderDeserializer.parseBedrockGeometry(sniff.bedrockGeoBytes,"geometry.peer");parsed.mainEntity.textures.put("texture",YSMFolderDeserializer.parseBedrockTexture(texture,"texture.png"));}else parsed=BBToRawConverter.convert(BBModelParser.parse(new String(sniff.bbmodelBytes,StandardCharsets.UTF_8)),sniff.sideTextures);}
                PeerModelPreflight.validateParsed(parsed);assertFalse(parsed.mainEntity.mainModel.bones.isEmpty(),profile);
            }
        }
        String mesh="""
            {"asset":{"version":"2.0"},"buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],"bufferViews":[{"buffer":0,"byteLength":36}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"}],"meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],"nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
            """.formatted(Base64.getEncoder().encodeToString(ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).putFloat(0).putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(0).putFloat(1).putFloat(0).array()));
        for(String profile:List.of("gltf-v1","glb-v1")){Path source=Files.createDirectory(temporary.resolve(profile));String file=profile.equals("gltf-v1")?"model.gltf":"model.glb";
            if(profile.equals("gltf-v1"))Files.writeString(source.resolve(file),mesh);else{byte[] json=mesh.getBytes(StandardCharsets.UTF_8);int padded=(json.length+3)&~3;var bytes=ByteBuffer.allocate(20+padded).order(ByteOrder.LITTLE_ENDIAN);bytes.putInt(0x46546c67).putInt(2).putInt(20+padded).putInt(padded).putInt(0x4e4f534a).put(json);while(bytes.hasRemaining())bytes.put((byte)' ');Files.write(source.resolve(file),bytes.array());}
            var asset=fixture(source,file,profile);try(var scope=new PeerDecodeScope()){PeerModelPreflight.validate(new PeerModelIdentity(asset.manifest().contentRoot(),profile),asset);var parsed=GltfLoader.loadWithManifest(asset.entrypoint()).model();PeerModelPreflight.validateGltf(parsed);assertEquals(1,parsed.meshes().getFirst().primitives().getFirst().triangleCount());}
        }
        assertEquals(9,PeerProfiles.ENABLED.size());
    }
}
