package com.micaftic.morpher.peer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

/** Peer-only filesystem boundaries, never applied to Cloud or existing local importers. */
public final class PeerSafeFiles {
    private PeerSafeFiles(){}
    public static Path privateDirectory(Path root,String relative)throws IOException {
        PeerPaths.validate(relative);root=root.toRealPath();Path directory=root.resolve(relative).normalize();
        if(!directory.startsWith(root))throw new IOException("Peer directory escapes private root");
        Files.createDirectories(directory);Path cursor=root;
        for(Path segment:root.relativize(directory)) {
            cursor=cursor.resolve(segment);var attrs=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther() || !cursor.toRealPath().startsWith(root))throw new IOException("Peer private directory is a link/reparse point");
        }
        return directory;
    }
    public static Path existing(Path root,String relative)throws IOException {
        PeerPaths.validate(relative);root=root.toAbsolutePath().normalize();Path file=root.resolve(relative).normalize();
        if(!file.startsWith(root))throw new IOException("Peer file escapes selected root");
        Path cursor=root;
        for(Path segment:root.relativize(file)) {
            cursor=cursor.resolve(segment);var attrs=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(attrs.isSymbolicLink() || attrs.isOther())throw new IOException("Peer links/reparse points are unsupported");
            if(!cursor.toRealPath().startsWith(root.toRealPath()))throw new IOException("Peer real path escapes selected root");
        }
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Peer source is not a regular file");return file;
    }
    public static Path destination(Path trustedRoot,String relative)throws IOException {
        PeerPaths.validate(relative);Path root=trustedRoot.toRealPath();Path target=root.resolve(relative).normalize();
        if(!target.startsWith(root))throw new IOException("Peer destination escapes private root");
        Path parent=target.getParent();Files.createDirectories(parent);
        if(!parent.toRealPath().startsWith(root))throw new IOException("Peer destination escapes private root");
        return target;
    }
    public static void deletePrivateTree(Path privateParent,Path directory)throws IOException {
        Path parent=privateParent.toRealPath();Path target=directory.toAbsolutePath().normalize();
        if(target.equals(parent) || !target.startsWith(parent))throw new IOException("Refusing to remove non-Peer-owned root");
        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))return;
        // Walk does not follow links. The deletion target is an owned staging root, never a model source root.
        try(var paths=Files.walk(target)) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) {
                if(!path.toAbsolutePath().normalize().startsWith(target))throw new IOException("Peer cleanup path escape");
                Files.deleteIfExists(path);
            }
        }
    }
}
