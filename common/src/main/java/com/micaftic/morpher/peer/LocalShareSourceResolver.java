package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.model.catalog.LocalModelLibrary;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.gltf.ResourceDependencyManifest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit local share selection and supported dependency closure; no native export or Cloud upload. */
public final class LocalShareSourceResolver {
    public record Selection(Path root, String entrypoint, String profile, Map<String, PeerAssetManifest.Role> resources) {
        public Selection { resources = Map.copyOf(resources); }
    }
    private LocalShareSourceResolver() {}
    public static Selection resolve(LocalModelLibrary library, String runtimeId, Path customRoot, boolean approved) throws IOException {
        var source = library.source(runtimeId); if(!library.canShare(source, customRoot, approved)) throw new IOException("This model source is not approved for Peer sharing");
        Path path = source.path().toRealPath(); Map<String, PeerAssetManifest.Role> resources = new LinkedHashMap<>();
        if(Files.isDirectory(path)) {
            String entry = Files.isRegularFile(path.resolve("ysm.json")) ? "ysm.json" : "main.json";
            if(!Files.isRegularFile(path.resolve(entry))) throw new IOException("Unsupported local model directory");
            try(var files = Files.walk(path)) {
                var iterator = files.iterator(); int entries = 0;
                while(iterator.hasNext()) {
                    Path file = iterator.next(); if(++entries > 8192) throw new IOException("Local model directory exceeds scan budget");
                    if(!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    String relative = path.relativize(file).toString().replace('\\', '/'); PeerAssetManifest.Role role = role(relative, entry);
                    if(role != null) { PeerSafeFiles.existing(path, relative); resources.put(relative, role); }
                }
            }
            if(entry.equals("ysm.json")) {
                try(var input = Files.newInputStream(path.resolve(entry))) { PeerModelPreflight.json(input); }
                var document = com.google.gson.JsonParser.parseString(Files.readString(path.resolve(entry))).getAsJsonObject();
                if(document.has("files")) dependencies(document.get("files"), path, resources);
            }
            for(String relative : resources.keySet()) if(!library.canShareFile(PeerSafeFiles.existing(path, relative)))
                throw new IOException("Restricted resource in local dependency closure");
            return new Selection(path, entry, "ysm-directory-v1", resources);
        }
        String name = path.getFileName().toString(), lower = name.toLowerCase(Locale.ROOT); resources.put(name, PeerAssetManifest.Role.MODEL);
        String profile;
        if(lower.endsWith(".gltf") || lower.endsWith(".glb")) {
            profile = lower.endsWith(".glb") ? "glb-v1" : "gltf-v1";
            var result = GltfLoader.loadWithManifest(path);
            for(var dependency : result.dependencies().entries()) resources.put(dependency.relativePath(),
                    dependency.roles().contains(ResourceDependencyManifest.Role.IMAGE) ? PeerAssetManifest.Role.TEXTURE : PeerAssetManifest.Role.BUFFER);
        } else if(lower.endsWith(".ysm")) profile = "ysm-v1";
        else if(lower.endsWith(".zip")) profile = PeerArchiveProfile.detect(path);
        else if(lower.endsWith(".bbmodel")) profile = "bbmodel-v1";
        else if(lower.endsWith(".geo.json") || lower.endsWith("geometry.json")) profile="bedrock-v1";
        else throw new IOException("Unsupported Peer source format");
        for(String relative : resources.keySet()) if(!library.canShareFile(PeerSafeFiles.existing(path.getParent(), relative)))
            throw new IOException("Restricted resource in local dependency closure");
        return new Selection(path.getParent(), name, profile, resources);
    }
    private static void dependencies(com.google.gson.JsonElement node, Path root, Map<String,PeerAssetManifest.Role> resources) throws IOException {
        if(node.isJsonObject()) { for(var child : node.getAsJsonObject().entrySet()) dependencies(child.getValue(), root, resources); }
        else if(node.isJsonArray()) { for(var child : node.getAsJsonArray()) dependencies(child, root, resources); }
        else if(node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
            String value = node.getAsString().replace('\\', '/'); String lower = value.toLowerCase(Locale.ROOT);
            if(lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".ogg") || lower.endsWith(".molang")) {
                PeerPaths.validate(value); PeerSafeFiles.existing(root, value);
                resources.put(value, lower.endsWith(".json") ? PeerAssetManifest.Role.MODEL : lower.endsWith(".ogg") ? PeerAssetManifest.Role.SOUND
                        : lower.endsWith(".molang") ? PeerAssetManifest.Role.FUNCTION : PeerAssetManifest.Role.TEXTURE);
            }
        }
    }
    private static PeerAssetManifest.Role role(String relative, String entry) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if(lower.equals(entry.toLowerCase(Locale.ROOT)) || lower.equals("arm.json") || lower.equals("main.json")) return PeerAssetManifest.Role.MODEL;
        if(lower.startsWith("functions/") && lower.endsWith(".molang")) return PeerAssetManifest.Role.FUNCTION;
        if((lower.startsWith("sounds/") || !lower.contains("/")) && lower.endsWith(".ogg")) return PeerAssetManifest.Role.SOUND;
        if(lower.startsWith("lang/") && lower.endsWith(".json")) return PeerAssetManifest.Role.LANGUAGE;
        if(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".avif") || lower.endsWith(".gif")) return PeerAssetManifest.Role.TEXTURE;
        if(lower.endsWith(".json") && (lower.startsWith("animations/") || lower.startsWith("models/") || lower.startsWith("animation")
                || lower.startsWith("controllers/") || lower.startsWith("animation_controllers/") || lower.startsWith("geometry/"))) return PeerAssetManifest.Role.ANIMATION;
        return null;
    }
    public static PeerAssetSnapshot freeze(Selection selection, Path staging, BooleanSupplier current) throws IOException {
        return new PeerAssetSnapshotBuilder(staging).freeze(selection.root(), selection.entrypoint(), selection.resources(), current);
    }
    /** Background-only full content probe catches rewrites preserving mtime and size. Freeze re-verifies before publish. */
    public static PeerContentRoot fingerprint(Selection selection,BooleanSupplier current)throws IOException {
        var resources=new ArrayList<PeerAssetManifest.Resource>();long total=0;
        try(var bufferLease=PeerResourceBudget.STREAM.reserve(PeerLimits.CHUNK_BYTES)) {
            byte[] buffer=new byte[PeerLimits.CHUNK_BYTES];
            for(var entry:new TreeMap<>(selection.resources()).entrySet()) {
                if(!current.getAsBoolean())throw new IOException("Peer source probe cancelled");
                Path path=PeerSafeFiles.existing(selection.root(),entry.getKey());long size=Files.size(path),read=0;var digest=PeerContentRoot.newDigest();
                if(size<0||size>PeerLimits.RESOURCE_BYTES||size>PeerLimits.SOURCE_BYTES-total)throw new IOException("Peer source probe exceeds budget");
                try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
                    int count;while((count=input.read(buffer))!=-1){if(!current.getAsBoolean())throw new IOException("Peer source probe cancelled");read+=count;if(read>size)throw new IOException("Peer source changed during probe");digest.update(buffer,0,count);}
                }
                if(read!=size)throw new IOException("Peer source changed during probe");total+=size;
                resources.add(new PeerAssetManifest.Resource(entry.getKey(),entry.getValue(),size,new PeerContentRoot(HexFormat.of().formatHex(digest.digest()))));
            }
        }
        return new PeerAssetManifest(1,selection.entrypoint(),resources).contentRoot();
    }
}
