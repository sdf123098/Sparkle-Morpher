package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.storage.LocalModelImportStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0 gate: every Cloud display path must finish without creating, modifying or deleting files.
 * The audit watches the sandbox roots and cross-checks them with a before/after tree snapshot,
 * so transient files (staging copies, .part downloads) are still observed.
 */
class CloudMemoryOnlyDiskAuditTest {
    private static final byte[] CONTENT = "memory-only-model-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG_1X1 = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void auditDetectsCacheImportAndTransientWrites() throws Exception {
        Sandbox sandbox = Sandbox.create();
        DiskAudit audit;
        try (DiskAudit running = DiskAudit.start(sandbox.monitored())) {
            audit = running;
            writeCurrentDiskPipeline(sandbox);
            Path transientFile = Files.createTempFile("audit-probe-", ".tmp");
            Files.writeString(transientFile, "transient");
            Files.delete(transientFile);
        } finally {
            sandbox.close();
        }

        List<Path> created = audit.created();
        assertTrue(created.stream().anyMatch(path -> path.toString().endsWith(".bin")),
                () -> "audit missed the Cloud cache .bin: " + audit.report());
        assertTrue(created.stream().anyMatch(path -> path.toString().endsWith(".ysm")),
                () -> "audit missed the imported model copy: " + audit.report());
        assertTrue(created.stream().anyMatch(path -> {
            String name = path.getFileName().toString();
            return name.contains(".part-") || name.endsWith(".tmp");
        }), () -> "audit missed transient staging files: " + audit.report());
        assertTrue(audit.deleted().stream().anyMatch(path -> path.toString().endsWith(".tmp")),
                () -> "audit missed the transient delete: " + audit.report());
    }

    /** Every common-side Cloud display step stays in memory; the audit also fails on transient files. */
    @Test
    void cloudDisplayPipelineMustNotWriteDisk() throws Exception {
        Sandbox sandbox = Sandbox.create();
        DiskAudit audit;
        try (DiskAudit running = DiskAudit.start(sandbox.monitored())) {
            audit = running;
            runMemoryDisplayPipeline();
        } finally {
            sandbox.close();
        }
        assertFalse(audit.touchedAnything(), () -> "Cloud display wrote to disk: " + audit.report());
    }

    /** M6 format gate: every Cloud parser entry reaches a parsed model without touching the filesystem. */
    @Test
    void everyCloudFormatParsesWithoutWritingDisk() throws Exception {
        Sandbox sandbox = Sandbox.create();
        DiskAudit audit;
        try (DiskAudit running = DiskAudit.start(sandbox.monitored())) {
            audit = running;
            parseEveryFormatInMemory();
        } finally {
            sandbox.close();
        }
        assertFalse(audit.touchedAnything(), () -> "Cloud parsing wrote to disk: " + audit.report());
    }

    @Test
    void fullPublicationAllowsOnlyDigestMetadataAndRetainsPathFreeSource() throws Exception {
        Sandbox sandbox = Sandbox.create();
        Path peer = sandbox.root().resolve("cache/peer");
        Path previews = sandbox.root().resolve("previews");
        Path instances = sandbox.root().resolve("instances");
        Files.createDirectories(peer); Files.createDirectories(previews); Files.createDirectories(instances);
        Path provenance = peer.resolve("local-origins.sha256");
        var roots = new ArrayList<>(sandbox.monitored());
        roots.add(peer); roots.add(previews); roots.add(instances);
        var library = new com.micaftic.morpher.core.model.catalog.LocalModelLibrary(provenance);
        var sources = new java.util.HashMap<String, com.micaftic.morpher.core.model.catalog.LocalModelCatalog.Entry>();
        var pending = new ArrayList<Object>();
        DiskAudit audit;
        byte[] bytes = memoryModelZip();
        String sha = CloudAssetCache.sha256(bytes);
        String id = new com.micaftic.morpher.core.model.CloudAssetIdentity("audit", "catalog", "model", "1", sha).runtimeModelId();
        try (DiskAudit running = DiskAudit.start(roots)) {
            audit = running;
            Object parsed = com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(bytes, "model.zip");
            com.micaftic.morpher.client.CloudMemoryPublication.publish(id, parsed, bytes, "model.zip", sha,
                    library, sources::put, pending::add, failure -> { throw new AssertionError(failure); });
            org.junit.jupiter.api.Assertions.assertEquals(1, pending.size());
            org.junit.jupiter.api.Assertions.assertSame(parsed, pending.getFirst());
            var reused = com.micaftic.morpher.client.CloudRuntimeReuse.materialize(() -> true,
                    () -> sources.containsKey(id),
                    () -> java.util.concurrent.CompletableFuture.failedFuture(new AssertionError("source fetched twice")),
                    () -> java.util.concurrent.CompletableFuture.completedFuture(true)).join();
            org.junit.jupiter.api.Assertions.assertNull(reused.bytes());
            org.junit.jupiter.api.Assertions.assertEquals(sha, Files.readString(provenance).trim());
            org.junit.jupiter.api.Assertions.assertNull(sources.get(id).path);
            assertFalse(library.canShare(library.source(id), sandbox.custom(), true));
        } finally { sandbox.close(); }
        Set<Path> allowed = new LinkedHashSet<>();
        allowed.add(provenance);
        // Atomic provenance staging contains only sorted SHA-256 lines, never model data.
        audit.touched().stream().filter(path -> path.getParent().equals(peer)
                && path.getFileName().toString().matches("origins-[0-9]+\\.tmp")).forEach(allowed::add);
        assertTrue(audit.touched().contains(provenance), () -> "metadata publication was not observed: " + audit.report());
        var forbidden = new LinkedHashSet<>(audit.touched()); forbidden.removeAll(allowed);
        assertTrue(forbidden.isEmpty(), () -> "Model resource create/write/delete events: " + forbidden + "; " + audit.report());
    }

    @Test
    void malformedInputsAndExternalGltfLeaveNoTemporaryResources() throws Exception {
        Sandbox sandbox = Sandbox.create(); DiskAudit audit;
        try (DiskAudit running = DiskAudit.start(sandbox.monitored())) {
            audit = running;
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(new byte[]{1,2,3}, "bad.zip"));
            byte[] external = "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"file:///private.bin\",\"byteLength\":1}]}".getBytes(StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    com.micaftic.morpher.resource.MemoryModelParser.parseGltf(external, "external.gltf"));
        } finally { sandbox.close(); }
        assertFalse(audit.touchedAnything(), audit::report);
    }

    private static void parseEveryFormatInMemory() throws Exception {
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(ysmFile(), "model.ysm"), "ysm");
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(memoryModelZip(), "model.zip"), "folder zip");
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(
                BBMODEL_JSON.getBytes(StandardCharsets.UTF_8), "model.bbmodel"), "bbmodel");
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(figuraZip(), "avatar.zip"), "figura");
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(
                GEO_JSON.getBytes(StandardCharsets.UTF_8), "audit.geo.json"), "bedrock geometry");
        requireParsed(com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(bedrockPack(), "pack.zip"), "bedrock pack");
        com.micaftic.morpher.resource.gltf.GltfLoadResult gltf =
                com.micaftic.morpher.resource.MemoryModelParser.parseGltf(glbFile(), "model.glb");
        if (gltf.model() == null) throw new IllegalStateException("Self-contained GLB did not parse");
    }

    private static void requireParsed(com.micaftic.morpher.resource.pojo.RawYsmModel model, String kind) {
        if (model == null || model.mainEntity.mainModel == null) {
            throw new IllegalStateException(kind + " did not produce a geometry");
        }
    }

    private static byte[] ysmFile() throws Exception {
        com.micaftic.morpher.resource.pojo.RawYsmModel model =
                new com.micaftic.morpher.resource.pojo.RawYsmModel();
        model.modelId = "audit-ysm";
        model.formatVersion = 32;
        com.micaftic.morpher.resource.pojo.RawYsmModel.RawGeometry geometry =
                new com.micaftic.morpher.resource.pojo.RawYsmModel.RawGeometry();
        geometry.modelType = 1;
        geometry.identifier = "geometry.audit";
        geometry.textureWidth = 16;
        geometry.textureHeight = 16;
        model.mainEntity.mainModel = geometry;
        byte[] serialized;
        try (com.micaftic.morpher.core.security.YSMByteBuf buffer =
                     com.micaftic.morpher.resource.YSMBinarySerializer.serialize(model, 32, true)) {
            serialized = new byte[buffer.getRawBuf().readableBytes()];
            buffer.getRawBuf().readBytes(serialized);
        }
        byte[] withHeader = new byte[serialized.length + 4];
        java.nio.ByteBuffer.wrap(withHeader).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(32).put(serialized);
        return com.micaftic.morpher.core.security.YsmCrypt.encryptYsmFile(withHeader);
    }

    private static final String BBMODEL_JSON = """
            {
              "meta": { "format_version": "4.5", "model_format": "free" },
              "resolution": { "width": 16, "height": 16 },
              "name": "Audit Model",
              "elements": [],
              "outliner": [],
              "textures": [],
              "animations": []
            }
            """;

    private static final String GEO_JSON = """
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{
              "identifier":"geometry.audit","texture_width":16,"texture_height":16},
              "bones":[{"name":"body","pivot":[0,0,0]}]}]}
            """;

    private static byte[] figuraZip() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("avatar.json", "{\"name\":\"AuditAvatar\"}".getBytes(StandardCharsets.UTF_8));
        files.put("avatar.bbmodel", BBMODEL_JSON.getBytes(StandardCharsets.UTF_8));
        files.put("tex.png", PNG_1X1);
        return zip(files);
    }

    private static byte[] bedrockPack() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("audit.geo.json", GEO_JSON.getBytes(StandardCharsets.UTF_8));
        files.put("tex.png", PNG_1X1);
        return zip(files);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] glbFile() {
        byte[] json = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[]}],
                 "nodes":[],"meshes":[],"buffers":[],"images":[]}
                """.getBytes(StandardCharsets.UTF_8);
        int padded = (json.length + 3) & ~3;
        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(12 + 8 + padded)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546C67).putInt(2).putInt(12 + 8 + padded)
                .putInt(padded).putInt(0x4E4F534A).put(json);
        while (out.position() < out.capacity()) out.put((byte) ' ');
        return out.array();
    }

    private static void runMemoryDisplayPipeline() throws Exception {
        byte[] model = memoryModelZip();
        String sha = CloudAssetCache.sha256(model);
        CloudAssetRef ref = new CloudAssetRef("audit-asset", 1, sha);
        CloudAssetCache.verify(ref, model);
        com.micaftic.morpher.resource.pojo.RawYsmModel parsed =
                com.micaftic.morpher.resource.MemoryModelParser.parseLegacy(model, "model.zip");
        if (parsed.mainEntity.mainModel == null) throw new IllegalStateException("Memory model did not parse");
        com.micaftic.morpher.core.model.catalog.LocalModelCatalog.Entry entry =
                com.micaftic.morpher.core.model.catalog.LocalModelCatalog.Entry.memory(
                        model, sha, "model.zip", false,
                        com.micaftic.morpher.core.model.catalog.LocalModelCatalog.fingerprint(model),
                        null, null, com.micaftic.morpher.core.model.ModelSourceType.CLOUD);
        if (!entry.isMemory() || entry.path != null) throw new IllegalStateException("Memory entry retained a path");
    }

    private static byte[] memoryModelZip() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("model/ysm.json"));
            zip.write("""
                    {"files":{"player":{"model":{"main":"model.geo.json"},"texture":"tex.png"}}}
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("model/model.geo.json"));
            zip.write("""
                    {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.model","texture_width":16,"texture_height":16},"bones":[{"name":"body","pivot":[0,0,0]}]}]}
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("model/tex.png"));
            zip.write(java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void writeCurrentDiskPipeline(Sandbox sandbox) throws IOException {
        String sha = CloudAssetCache.sha256(CONTENT);
        Files.write(sandbox.cloudCache().resolve("audit-asset-r1-" + sha + ".bin"), CONTENT);
        new LocalModelImportStore(sandbox.custom()).persist("audit-model", "model.ysm", CONTENT);
    }

    private record Sandbox(Path root, Path cloudCache, Path custom, Path cloudModels,
                           Path serverCache, Path clientCache, Path export, Path tmp, String previousTmp) {
        static Sandbox create() throws IOException {
            Path root = Files.createTempDirectory("sm-memory-only-audit-");
            Sandbox sandbox = new Sandbox(root,
                    Files.createDirectories(root.resolve("cloud-cache")),
                    Files.createDirectories(root.resolve("custom")),
                    Files.createDirectories(root.resolve("cloud_models")),
                    Files.createDirectories(root.resolve("cache/server")),
                    Files.createDirectories(root.resolve("cache/client")),
                    Files.createDirectories(root.resolve("export")),
                    Files.createDirectories(root.resolve("tmp")),
                    System.getProperty("java.io.tmpdir"));
            System.setProperty("java.io.tmpdir", sandbox.tmp().toString());
            return sandbox;
        }

        List<Path> monitored() {
            return List.of(cloudCache, custom, cloudModels, serverCache, clientCache, export, tmp);
        }

        void close() {
            if (previousTmp == null) System.clearProperty("java.io.tmpdir");
            else System.setProperty("java.io.tmpdir", previousTmp);
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record Stamp(long size, FileTime modified) {}

    private static final class DiskAudit implements AutoCloseable {
        private final List<Path> roots;
        private final Map<Path, Stamp> before;
        private final Set<Path> created = ConcurrentHashMap.newKeySet();
        private final Set<Path> modified = ConcurrentHashMap.newKeySet();
        private final Set<Path> deleted = ConcurrentHashMap.newKeySet();
        private final WatchService service;
        private final Thread watcher;
        private volatile boolean running = true;

        private DiskAudit(List<Path> roots) throws IOException {
            this.roots = List.copyOf(roots);
            this.before = snapshot(roots);
            this.service = FileSystems.getDefault().newWatchService();
            for (Path root : roots) registerTree(root);
            this.watcher = new Thread(this::watch, "cloud-memory-only-audit");
            this.watcher.setDaemon(true);
            this.watcher.start();
        }

        static DiskAudit start(List<Path> roots) throws IOException { return new DiskAudit(roots); }

        private void registerTree(Path root) {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isDirectory).toList()) {
                    try {
                        path.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                    } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }

        private void watch() {
            while (running) {
                WatchKey key;
                try {
                    key = service.poll(50L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ClosedWatchServiceException closed) {
                    return;
                }
                if (key == null) continue;
                Path directory = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        modified.add(directory.resolve("AUDIT_WATCH_OVERFLOW")); continue;
                    }
                    Path path = directory.resolve((Path) event.context());
                    if (Files.isDirectory(path)) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) registerTree(path);
                        continue;
                    }
                    record(event.kind(), path);
                }
                if (!key.reset()) break;
            }
        }

        private void record(WatchEvent.Kind<?> kind, Path path) {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) created.add(path);
            else if (kind == StandardWatchEventKinds.ENTRY_DELETE) deleted.add(path);
            else modified.add(path);
        }

        private static Map<Path, Stamp> snapshot(List<Path> roots) throws IOException {
            Map<Path, Stamp> stamps = new LinkedHashMap<>();
            for (Path root : roots) {
                if (!Files.exists(root)) continue;
                try (Stream<Path> paths = Files.walk(root)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        stamps.put(path, new Stamp(Files.size(path), Files.getLastModifiedTime(path)));
                    }
                }
            }
            return stamps;
        }

        Set<Path> touched() {
            Set<Path> all = new LinkedHashSet<>(created); all.addAll(modified); all.addAll(deleted);
            return all;
        }

        boolean touchedAnything() {
            return !created.isEmpty() || !modified.isEmpty() || !deleted.isEmpty();
        }

        List<Path> created() {
            Set<Path> all = new LinkedHashSet<>(created);
            all.addAll(afterDiff().created);
            return new ArrayList<>(all);
        }

        List<Path> deleted() {
            Set<Path> all = new LinkedHashSet<>(deleted);
            all.addAll(afterDiff().deleted);
            return new ArrayList<>(all);
        }

        private Diff afterDiff() {
            Diff diff = new Diff();
            Map<Path, Stamp> after;
            try {
                after = snapshot(roots);
            } catch (IOException failure) {
                return diff;
            }
            for (Map.Entry<Path, Stamp> entry : after.entrySet()) {
                Stamp previous = before.get(entry.getKey());
                if (previous == null) diff.created.add(entry.getKey());
                else if (previous.size != entry.getValue().size
                        || !previous.modified.equals(entry.getValue().modified)) diff.modified.add(entry.getKey());
            }
            for (Path path : before.keySet()) if (!after.containsKey(path)) diff.deleted.add(path);
            return diff;
        }

        String report() {
            Diff diff = afterDiff();
            Set<Path> allCreated = new LinkedHashSet<>(created); allCreated.addAll(diff.created);
            Set<Path> allModified = new LinkedHashSet<>(modified); allModified.addAll(diff.modified);
            Set<Path> allDeleted = new LinkedHashSet<>(deleted); allDeleted.addAll(diff.deleted);
            return "created=" + allCreated + " modified=" + allModified + " deleted=" + allDeleted;
        }

        @Override
        public void close() {
            try { Thread.sleep(100); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            running = false;
            watcher.interrupt();
            try {
                service.close();
            } catch (IOException ignored) { }
            try {
                watcher.join(1_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static final class Diff {
            final Set<Path> created = new LinkedHashSet<>();
            final Set<Path> modified = new LinkedHashSet<>();
            final Set<Path> deleted = new LinkedHashSet<>();
        }
    }
}
