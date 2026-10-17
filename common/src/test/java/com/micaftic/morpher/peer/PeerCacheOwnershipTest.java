package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PeerCacheOwnershipTest {
    @TempDir Path temporary;
    private int externalLock(Path root)throws Exception {
        Path source=temporary.resolve("OwnershipProbe.java");Files.writeString(source,"""
            import java.nio.file.*;import java.nio.channels.*;
            class OwnershipProbe {public static void main(String[] args)throws Exception {
                try(var channel=FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
                    var lock=channel.tryLock();if(lock==null)System.exit(73);else lock.release();
                }
            }}
            """);
        var executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        var process=new ProcessBuilder(executable.toString(),"-Xmx32m",source.toString(),root.resolve(".store.lock").toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10,TimeUnit.SECONDS));return process.exitValue();
    }
    @Test void sameJvmStoresShareOwnershipButExternalProcessCannotCleanOrEvictAnActiveRoot()throws Exception {
        Path root=temporary.resolve("cache");var first=new PeerAssetStore(root);var second=new PeerAssetStore(root);var lease=second.retain(PeerContentRoot.digest(new byte[]{1}));
        assertEquals(73,externalLock(root));first.close();assertEquals(73,externalLock(root));second.close();assertEquals(73,externalLock(root));lease.close();assertEquals(0,externalLock(root));
        try(var replacement=new PeerAssetStore(root)){assertEquals(73,externalLock(root));}assertEquals(0,externalLock(root));
    }
}
