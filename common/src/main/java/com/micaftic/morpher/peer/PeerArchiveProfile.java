package com.micaftic.morpher.peer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Same shape precedence as the existing local ZIP importer, inspected without allocating entry bodies. */
public final class PeerArchiveProfile {
    private PeerArchiveProfile(){}
    public static String detect(Path path)throws IOException {
        PeerArchivePreflight.validate(path,PeerLimits.EXPANDED_BYTES,PeerLimits.RESOURCE_COUNT);
        boolean ysm=false,avatar=false,bb=false,bedrock=false;
        try(var zip=new ZipFile(path.toFile())) {
            var entries=zip.entries();while(entries.hasMoreElements()) {
                var entry=entries.nextElement();if(entry.isDirectory())continue;
                String name=entry.getName().toLowerCase(Locale.ROOT),base=name.substring(name.lastIndexOf('/')+1);
                ysm|=base.equals("ysm.json")||base.equals("main.json")||base.equals("arm.json");
                avatar|=base.equals("avatar.json");bb|=name.endsWith(".bbmodel");bedrock|=name.endsWith(".geo.json")||base.equals("geometry.json");
            }
        }
        if(ysm)return "ysm-zip-v1";if(bb)return avatar?"figura-geometry-v1":"bbmodel-zip-v1";if(bedrock)return "bedrock-v1";
        throw new IOException("Unsupported Peer archive shape");
    }
}
