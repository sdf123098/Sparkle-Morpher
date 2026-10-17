package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerArchivePreflightTest {
    @TempDir Path temp;
    private Path zip(String...names)throws IOException {
        Path p=Files.createTempFile(temp,"model-",".zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(p))){for(String name:names){out.putNextEntry(new ZipEntry(name));out.write(new byte[1024]);out.closeEntry();}}return p;
    }
    @Test void legalArchiveAndEntryExpansionLimitsCountActualBytes()throws Exception {
        Path p=zip("ysm.json","textures/a.png");assertEquals(2048,PeerArchivePreflight.validate(p,4096,3).expandedBytes());
        assertThrows(IOException.class,()->PeerArchivePreflight.validate(p,1000,3));
        assertThrows(IOException.class,()->PeerArchivePreflight.validate(p,4096,1));
    }
    @Test void rejectsTraversalCaseAliasesAndFileDirectoryConflicts()throws Exception {
        for(Path p:new Path[]{zip("../outside"),zip("A.png","a.png"),zip("A/1.png","a/2.png"),zip("model","model/ysm.json"),zip("NUL.txt")})
            assertThrows(IOException.class,()->PeerArchivePreflight.validate(p,4096,10));
        assertFalse(Files.exists(temp.getParent().resolve("outside")));
    }
    @Test void centralDirectorySymlinkAttributeIsRejectedBeforeParser()throws Exception {
        Path p=zip("link");byte[] bytes=Files.readAllBytes(p);var data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<bytes.length-46;i++)if(data.getInt(i)==0x02014b50){data.putInt(i+38,0xa1ff0000);break;}
        Files.write(p,bytes);assertThrows(IOException.class,()->PeerArchivePreflight.validate(p,4096,10));
    }
    @Test void truncatedAndBadDirectoryFailWholeRevision()throws Exception {
        Path p=zip("ysm.json");byte[] bytes=Files.readAllBytes(p);Files.write(p,java.util.Arrays.copyOf(bytes,bytes.length-5));
        assertThrows(IOException.class,()->PeerArchivePreflight.validate(p,4096,10));
    }
}
