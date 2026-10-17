package com.micaftic.morpher.peer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Canonical source identity does not include profile, timestamps, local paths or transport chunk size. */
public record PeerAssetManifest(int schema, String entrypoint, List<Resource> resources) {
    public enum Role { MODEL, TEXTURE, ANIMATION, BUFFER, SOUND, LANGUAGE, FUNCTION }
    public record Resource(String path, Role role, long byteSize, PeerContentRoot digest) {
        public Resource {
            PeerPaths.validate(path); Objects.requireNonNull(role); Objects.requireNonNull(digest);
            if (byteSize < 0 || byteSize > PeerLimits.RESOURCE_BYTES) throw new IllegalArgumentException("Peer resource too large");
        }
    }
    public PeerAssetManifest {
        if (schema != 1) throw new IllegalArgumentException("Unsupported Peer manifest schema");
        PeerPaths.validate(entrypoint);
        if (resources == null || resources.isEmpty() || resources.size() > PeerLimits.RESOURCE_COUNT)
            throw new IllegalArgumentException("Invalid Peer resource count");
        Set<String> aliases = new HashSet<>(),directories=new HashSet<>();Map<String,String> spelling=new HashMap<>(); long total=0; boolean hasEntry=false;
        List<Resource> sorted = new ArrayList<>(resources);
        sorted.sort((a,b) -> Arrays.compareUnsigned(a.path().getBytes(StandardCharsets.UTF_8),b.path().getBytes(StandardCharsets.UTF_8)));
        for (Resource r : sorted) {
            if (!aliases.add(PeerPaths.aliasKey(r.path()))) throw new IllegalArgumentException("Aliased Peer paths");
            String prefix="";String[] parts=r.path().split("/");
            for(int i=0;i<parts.length;i++) {
                prefix=prefix.isEmpty()?parts[i]:prefix+"/"+parts[i];String key=PeerPaths.aliasKey(prefix);
                String existing=spelling.putIfAbsent(key,prefix);
                if(existing!=null && !existing.equals(prefix))throw new IllegalArgumentException("Aliased Peer directory spelling");
                if(i<parts.length-1)directories.add(key);
            }
            total = Math.addExact(total,r.byteSize());
            if (total > PeerLimits.EXPANDED_BYTES) throw new IllegalArgumentException("Peer model too large");
            hasEntry |= r.path().equals(entrypoint) && r.role()==Role.MODEL;
        }
        if(aliases.stream().anyMatch(directories::contains))throw new IllegalArgumentException("Peer file/directory collision");
        if (!hasEntry) throw new IllegalArgumentException("Missing Peer model entrypoint");
        resources = List.copyOf(sorted);
        long canonicalSize = 4+"SPM-PEER-MANIFEST".length()+4+4+entrypoint.getBytes(StandardCharsets.UTF_8).length+4;
        for (Resource r : resources) canonicalSize += 4+r.path().getBytes(StandardCharsets.UTF_8).length+4+r.role().name().length()+8+32;
        if (canonicalSize > PeerLimits.MANIFEST_BYTES) throw new IllegalArgumentException("Peer manifest too large");
    }
    public long totalBytes() { return resources.stream().mapToLong(Resource::byteSize).sum(); }
    public long canonicalSize() {
        long size=4+"SPM-PEER-MANIFEST".length()+4+4+entrypoint.getBytes(StandardCharsets.UTF_8).length+4;
        for(Resource resource:resources)size+=4+resource.path().getBytes(StandardCharsets.UTF_8).length+4+resource.role().name().length()+8+32;
        return size;
    }
    public byte[] canonicalBytes() {
        try {
            var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
            field(out,"SPM-PEER-MANIFEST"); out.writeInt(schema); field(out,entrypoint); out.writeInt(resources.size());
            for (Resource r : resources) { field(out,r.path()); field(out,r.role().name()); out.writeLong(r.byteSize()); out.write(r.digest().bytes()); }
            out.flush(); return bytes.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    private static void field(DataOutputStream out,String value) throws IOException {
        byte[] data=value.getBytes(StandardCharsets.UTF_8); out.writeInt(data.length); out.write(data);
    }
    public PeerContentRoot contentRoot() { return PeerContentRoot.digest(canonicalBytes()); }

    public static PeerAssetManifest decode(byte[] bytes) throws IOException {
        if (bytes.length > PeerLimits.MANIFEST_BYTES) throw new IOException("Peer manifest too large");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            if (!readField(in, 32).equals("SPM-PEER-MANIFEST")) throw new IOException("Invalid manifest domain");
            int schema = in.readInt(); String entry = readField(in, PeerLimits.PATH_BYTES); int count = in.readInt();
            if (count < 1 || count > PeerLimits.RESOURCE_COUNT) throw new IOException("Invalid manifest count");
            var resources = new ArrayList<Resource>();
            for (int i = 0; i < count; i++) {
                String path = readField(in, PeerLimits.PATH_BYTES); Role role = Role.valueOf(readField(in, 32));
                long size = in.readLong(); byte[] hash = new byte[32]; in.readFully(hash);
                resources.add(new Resource(path, role, size, new PeerContentRoot(HexFormat.of().formatHex(hash))));
            }
            if (in.available() != 0) throw new IOException("Trailing manifest bytes");
            var result = new PeerAssetManifest(schema, entry, resources);
            if (!Arrays.equals(bytes, result.canonicalBytes())) throw new IOException("Noncanonical manifest");
            return result;
        } catch (IllegalArgumentException | ArithmeticException e) { throw new IOException("Invalid Peer manifest", e); }
    }
    private static String readField(DataInputStream in, int max) throws IOException {
        int count = in.readInt(); if (count < 0 || count > max || count > in.available()) throw new IOException("Invalid manifest field length");
        byte[] data = new byte[count]; in.readFully(data);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(data)).toString();
    }
}
