package com.micaftic.morpher.peer;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Receiving-side budgets before the existing format adapters and before runtime assembly creation. */
public final class PeerModelPreflight {
    private PeerModelPreflight() {}
    public static void validate(PeerModelIdentity identity, PeerAssetStore.Verified asset) throws IOException {
        if(!identity.contentRoot().equals(asset.manifest().contentRoot())) throw new IOException("Peer root differs from manifest");
        String name = asset.manifest().entrypoint().toLowerCase(Locale.ROOT), profile = identity.importerProfile();
        boolean supported = switch(profile) {
            case "ysm-directory-v1" -> name.equals("ysm.json") || name.equals("main.json");
            case "ysm-v1" -> name.endsWith(".ysm");
            case "ysm-zip-v1", "bbmodel-zip-v1", "figura-geometry-v1" -> name.endsWith(".zip") && PeerArchiveProfile.detect(asset.entrypoint()).equals(profile);
            case "bedrock-v1" -> name.endsWith(".geo.json") || name.endsWith("geometry.json") || name.endsWith(".zip") && PeerArchiveProfile.detect(asset.entrypoint()).equals(profile);
            case "bbmodel-v1" -> name.endsWith(".bbmodel");
            case "gltf-v1" -> name.endsWith(".gltf");
            case "glb-v1" -> name.endsWith(".glb");
            default -> false;
        };
        if(!supported) throw new IOException("Unsupported Peer profile/entrypoint combination");
        for(int i = 0; i < asset.manifest().resources().size(); i++) {
            Path path = asset.file(i); String lower = path.toString().toLowerCase(Locale.ROOT);
            if(lower.endsWith(".zip")) {
                PeerArchivePreflight.validate(path, PeerLimits.EXPANDED_BYTES, PeerLimits.RESOURCE_COUNT);
                try(var zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    while(entries.hasMoreElements()) {
                        var entry = entries.nextElement(); if(entry.isDirectory()) continue;
                        if(entry.getSize() < 0 || entry.getSize() > PeerLimits.RESOURCE_BYTES) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
                        if(entry.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IOException("Nested Peer archive");
                        try(var input = zip.getInputStream(entry)) { inspect(entry.getName(), input); }
                    }
                }
            } else if(lower.endsWith(".glb")) {
                try(var input = new DataInputStream(Files.newInputStream(path))) {
                    if(Integer.reverseBytes(input.readInt()) != 0x46546C67 || Integer.reverseBytes(input.readInt()) != 2
                            || Integer.toUnsignedLong(Integer.reverseBytes(input.readInt())) != Files.size(path)) throw new IOException("Invalid Peer GLB header");
                    int size = Integer.reverseBytes(input.readInt()), type = Integer.reverseBytes(input.readInt());
                    if(type != 0x4E4F534A || size < 1 || size > 8 * 1024 * 1024 || size > Files.size(path) - 20) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
                    json(new ByteArrayInputStream(input.readNBytes(size)));
                }
            } else try(var input = Files.newInputStream(path)) { inspect(lower, input); }
        }
    }
    private static void inspect(String name, InputStream input) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".json") || lower.endsWith(".gltf") || lower.endsWith(".bbmodel")) json(input);
        else if(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) image(input);
        else if(lower.endsWith(".ogg")) PeerAudioPreflight.pcmBytes(input);
        else if(lower.endsWith(".molang")) PeerMolangLimits.read(new InputStreamReader(input,StandardCharsets.UTF_8));
    }
    public static void json(InputStream input) throws IOException {
        byte[] bytes = PeerDecodeScope.boundedBytes(input,8 * 1024 * 1024);
        if(bytes.length > 8 * 1024 * 1024) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        try(var reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            int depth = 0, nodes = 0;
            while(reader.peek() != JsonToken.END_DOCUMENT) {
                if(++nodes > 262144) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
                PeerDecodeScope.read();
                switch(reader.peek()) {
                    case BEGIN_ARRAY -> { reader.beginArray(); if(++depth > 64) throw new IOException("Peer JSON depth exceeded"); }
                    case BEGIN_OBJECT -> { reader.beginObject(); if(++depth > 64) throw new IOException("Peer JSON depth exceeded"); }
                    case END_ARRAY -> { reader.endArray(); depth--; }
                    case END_OBJECT -> { reader.endObject(); depth--; }
                    case NAME -> { if(reader.nextName().length() > 4096) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED); }
                    case STRING, NUMBER -> { if(reader.nextString().length() > 1_048_576) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED); }
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new IOException("Invalid Peer JSON");
                }
            }
        } catch(RuntimeException malformed) { throw new IOException("Invalid Peer JSON", malformed); }
    }
    public static void image(InputStream input) throws IOException {
        imagePixels(input);
    }
    public static long imagePixelCount(byte[] data)throws IOException {return imagePixels(new ByteArrayInputStream(data));}
    private static long imagePixels(InputStream input) throws IOException {
        var buffered=new BufferedInputStream(input);buffered.mark(8);byte[] signature=buffered.readNBytes(6);buffered.reset();
        if(signature.length==6&&signature[0]=='G'&&signature[1]=='I'&&signature[2]=='F')return PeerGifPreflight.pixels(buffered);
        // Header inspection does not allocate a decoded image or upload any texture.
        try(var stream = ImageIO.createImageInputStream(buffered)) {
            var readers = ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new IOException("Unsupported Peer image encoding");
            var reader = readers.next();
            try {
                reader.setInput(stream);int frames=reader.getNumImages(true);if(frames<1 || frames>256)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
                long pixels=0;for(int i=0;i<frames;i++){int w=reader.getWidth(i),h=reader.getHeight(i);dimensions(w,h);pixels+= (long)w*h;if(pixels>32_000_000L)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);}return pixels;
            }
            finally { reader.dispose(); }
        }
    }
    private static void dimensions(int width, int height) throws IOException {
        if(width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_777_216)
            throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
    }
    public static void validateParsed(RawYsmModel raw) throws IOException {
        if(raw == null || raw.mainEntity == null || raw.mainEntity.mainModel == null) throw new IOException("Peer model has no player geometry");
        long actions=raw.properties.extraAnimations.size();for(var group:raw.properties.extraAnimationClassifies)actions+=group.extras.size();
        if(actions>256||raw.properties.extraAnimationButtons.size()>128||raw.metadata.authors.size()>64||raw.metadata.extraAvatars.size()>64||raw.properties.backgroundImages.size()>64)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        if(raw.vehicles.size() + raw.projectiles.size() > 64) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        var geometries = new ArrayList<RawYsmModel.RawGeometry>(); geometries.add(raw.mainEntity.mainModel); geometries.add(raw.mainEntity.armModel);
        var textures = new ArrayList<RawYsmModel.RawTexture>(raw.mainEntity.textures.values());
        raw.vehicles.values().forEach(sub -> { geometries.add(sub.model); textures.addAll(sub.textures.values()); });
        raw.projectiles.values().forEach(sub -> { geometries.add(sub.model); textures.addAll(sub.textures.values()); });
        long bones = 0, cubes = 0, faces = 0, pixels = 0;
        for(var geometry : geometries) if(geometry != null) {
            bones += geometry.bones.size();
            Map<String,String> parents = new HashMap<>();
            for(var bone : geometry.bones) {
                if(parents.containsKey(bone.name)) throw new IOException("Duplicate Peer bone"); parents.put(bone.name, bone.parentName);
                cubes += bone.cubes.size(); for(var cube : bone.cubes) faces += cube.faces.size();
            }
            for(String bone : parents.keySet()) {
                var visited = new HashSet<String>(); String parent = bone;
                while(parent != null && !parent.isBlank()) {
                    if(!visited.add(parent) || visited.size() > 128) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED); parent = parents.get(parent);
                }
            }
        }
        if(bones > 4096 || cubes > 65536 || faces > 131072 || textures.size() > 128) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        for(var texture : textures) {
            pixels += texturePixels(texture.width, texture.height, texture.imageFormat, texture.data);
            for(var sub : texture.subTextures) pixels += texturePixels(sub.width, sub.height, sub.imageFormat, sub.data);
        }
        for(var author:raw.metadata.authors)if(author.avatarImage!=null)pixels+=imagePixels(author.avatarImage);
        for(var avatar:raw.metadata.extraAvatars)pixels+=imagePixels(avatar);
        for(var background:raw.properties.backgroundImages)pixels+=imagePixels(background);
        if(pixels > 32_000_000L || raw.functionFiles.size() > 128 || raw.soundFiles.size() > 128) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        PeerDecodeScope.allocate(Math.multiplyExact(pixels,8));
        long functions = raw.functionFiles.values().stream().mapToLong(file -> file.data == null ? 0 : file.data.length).sum();
        if(functions > 1_048_576) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        for(var function:raw.functionFiles.values())if(function.data!=null){if(function.data.length>65536)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);PeerMolangLimits.syntax(new String(function.data,StandardCharsets.UTF_8));}
        long pcm=0;for(var sound:raw.soundFiles.values())if(sound.data!=null&&sound.data.length>0){pcm+=PeerAudioPreflight.pcmBytes(sound.data);if(pcm>64L*1024*1024)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);}
        var expressions=new ExpressionBudget();expressions.entity(raw.mainEntity.animationFiles.values(),raw.mainEntity.animationControllerFiles);
        for(var sub:raw.vehicles.values())expressions.entity(sub.animationFiles.values(),sub.animationControllerFiles);
        for(var sub:raw.projectiles.values())expressions.entity(sub.animationFiles.values(),sub.animationControllerFiles);
    }
    private static long imagePixels(RawYsmModel.RawImage image)throws IOException {return texturePixels(image.width,image.height,image.format,image.data);}
    /** Inspect raw expressions even when an equivalent AST was already compiled by another source. */
    private static final class ExpressionBudget {
        long characters;int entries;
        void check(Object value)throws IOException {if(!(value instanceof String text))return;if(++entries>16384||(characters+=text.length())>1_048_576L)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);PeerMolangLimits.syntax(text);}
        void entity(Collection<RawYsmModel.RawAnimationFile> files,List<RawYsmModel.RawAnimationControllerFile> controllers)throws IOException {
            for(var file:files)for(var animation:file.animations.values()) {
                check(animation.blendWeight);for(var event:animation.timelineEvents)for(var text:event.events)check(text);
                for(var bone:animation.boneAnimations){frames(bone.rotation);frames(bone.position);frames(bone.scale);}
            }
            for(var file:controllers)for(var controller:file.controllers.values())for(var state:controller.states){for(var text:state.animations.values())check(text);for(var text:state.transitions.values())check(text);for(var text:state.onEntry)check(text);for(var text:state.onExit)check(text);}
        }
        void frames(List<RawYsmModel.RawKeyframe> frames)throws IOException {for(var frame:frames){for(var value:frame.preData)check(value);for(var value:frame.postData)check(value);}}
    }
    private static long texturePixels(int width, int height, int format, byte[] bytes) throws IOException {
        dimensions(width, height); long pixels = (long)width * height;
        if(format == -1) {
            // Legacy YSM textures are raw RGBA, not PNG/JPEG/ImageIO streams.
            if(bytes == null || bytes.length != pixels * 4) throw new IOException("Peer RGBA texture byte count mismatch");
        } else if(bytes != null && bytes.length > 0) pixels = Math.max(pixels, imagePixels(new ByteArrayInputStream(bytes)));
        return pixels;
    }
    public static long texturePixelCount(int width,int height,int format,byte[] bytes)throws IOException {return texturePixels(width,height,format,bytes);}
    public static void validateGltf(com.micaftic.morpher.resource.gltf.GltfModel model) throws IOException {
        if(model.images().size() > 128 || model.nodes().size() > 4096 || model.meshes().size() > 1024 || model.animations().size() > 1024)
            throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        long pixels = 0;
        for(var image : model.images()) { pixels += imagePixels(new ByteArrayInputStream(image.data())); if(pixels > 32_000_000L) throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED); }
    }
}
