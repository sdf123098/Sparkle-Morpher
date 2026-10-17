package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Strict Peer-only ZIP validation before the legacy tolerant sniffers/parsers see network content. */
public final class PeerArchivePreflight {
    public record Result(int entryCount,long expandedBytes){}
    private PeerArchivePreflight(){}
    public static Result validate(Path archive,long maxExpandedBytes,int maxEntries)throws IOException {
        if(maxExpandedBytes<1 || maxExpandedBytes>PeerLimits.EXPANDED_BYTES || maxEntries<1 || maxEntries>PeerLimits.RESOURCE_COUNT)
            throw new IllegalArgumentException("Invalid Peer ZIP budget");
        if(Files.size(archive)>PeerLimits.RESOURCE_BYTES)throw new IOException("Peer ZIP source too large");
        rejectLinkEntries(archive);
        long expanded=0;int count=0;Set<String> files=new HashSet<>(),directories=new HashSet<>();Map<String,String> names=new HashMap<>(),spelling=new HashMap<>();
        try(var zip=new ZipFile(archive.toFile(),StandardCharsets.UTF_8)) {
            var entries=zip.entries();byte[] buffer=new byte[64*1024];
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement();if(++count>maxEntries)throw new IOException("Peer ZIP entry budget exceeded");
                String path=entry.getName();if(entry.isDirectory() && path.endsWith("/"))path=path.substring(0,path.length()-1);
                try{PeerPaths.validate(path);}catch(IllegalArgumentException e){throw new IOException("Unsafe Peer ZIP path");}
                if(path.split("/").length>32)throw new IOException("Peer ZIP path depth exceeded");
                String key=PeerPaths.aliasKey(path);
                String previous=names.putIfAbsent(key,path);
                if(previous!=null)throw new IOException("Duplicate or aliased Peer ZIP entry");
                String prefix="";for(String part:path.split("/")) {
                    prefix=prefix.isEmpty()?part:prefix+"/"+part;String old=spelling.putIfAbsent(PeerPaths.aliasKey(prefix),prefix);
                    if(old!=null && !old.equals(prefix))throw new IOException("Aliased Peer ZIP directory spelling");
                }
                if(entry.isDirectory())directories.add(key);else files.add(key);
                String parent=path;while(parent.contains("/")){parent=parent.substring(0,parent.lastIndexOf('/'));directories.add(PeerPaths.aliasKey(parent));}
                if(entry.isDirectory())continue;
                if(entry.getSize()<0 || entry.getSize()>PeerLimits.RESOURCE_BYTES || entry.getSize()>maxExpandedBytes-expanded)
                    throw new IOException("Peer ZIP declared resource budget exceeded");
                long size=0;var crc=new CRC32();
                try(var input=zip.getInputStream(entry)) {
                    int n;while((n=input.read(buffer))!=-1){size=Math.addExact(size,n);expanded=Math.addExact(expanded,n);
                        if(size>PeerLimits.RESOURCE_BYTES || expanded>maxExpandedBytes)throw new IOException("Peer ZIP expansion budget exceeded");crc.update(buffer,0,n);}
                }
                if(size!=entry.getSize() || crc.getValue()!=entry.getCrc())throw new IOException("Peer ZIP resource integrity mismatch");
            }
        } catch(IllegalArgumentException|ArithmeticException e){throw new IOException("Invalid Peer ZIP metadata");}
        if(count==0 || files.stream().anyMatch(directories::contains))throw new IOException("Empty or conflicting Peer ZIP");
        return new Result(count,expanded);
    }
    /** ZipFile does not expose Unix link / Windows reparse attributes on Java21: inspect bounded CEN records. */
    private static void rejectLinkEntries(Path file)throws IOException {
        try(var channel=FileChannel.open(file,StandardOpenOption.READ)) {
            long size=channel.size();if(size<22)throw new IOException("Truncated Peer ZIP");int length=(int)Math.min(size,65557);
            ByteBuffer tail=read(channel,size-length,length);int eocd=-1;
            for(int i=length-22;i>=0;i--)if(tail.getInt(i)==0x06054b50 && i+22+Short.toUnsignedInt(tail.getShort(i+20))==length){eocd=i;break;}
            if(eocd<0)throw new IOException("Missing Peer ZIP directory");
            int entries=Short.toUnsignedInt(tail.getShort(eocd+10));
            long centralSize=Integer.toUnsignedLong(tail.getInt(eocd+12)),offset=Integer.toUnsignedLong(tail.getInt(eocd+16));
            if(tail.getShort(eocd+4)!=0 || tail.getShort(eocd+6)!=0 || Short.toUnsignedInt(tail.getShort(eocd+8))!=entries
                    || entries==65535 || offset==0xffffffffL || centralSize==0xffffffffL || entries>PeerLimits.RESOURCE_COUNT
                    || centralSize>PeerLimits.MANIFEST_BYTES*4L || offset+centralSize!=size-length+eocd)
                throw new IOException("Unsupported or over-budget Peer ZIP directory");
            long end=offset+centralSize;
            for(int i=0;i<entries;i++) {
                if(offset+46>end)throw new IOException("Truncated Peer ZIP entry");ByteBuffer entry=read(channel,offset,46);
                if(entry.getInt(0)!=0x02014b50)throw new IOException("Invalid Peer ZIP directory record");
                long attributes=Integer.toUnsignedLong(entry.getInt(38));int mode=(int)(attributes>>>16)&0xf000;
                if(mode!=0 && mode!=0x8000 && mode!=0x4000 || (attributes&0x400)!=0)throw new IOException("Peer ZIP links/devices rejected");
                if((entry.getShort(8)&1)!=0)throw new IOException("Encrypted Peer ZIP unsupported");
                offset+=46L+Short.toUnsignedInt(entry.getShort(28))+Short.toUnsignedInt(entry.getShort(30))+Short.toUnsignedInt(entry.getShort(32));
                if(offset>end)throw new IOException("Peer ZIP directory overflow");
            }
            if(offset!=end)throw new IOException("Trailing Peer ZIP directory records");
        }
    }
    private static ByteBuffer read(FileChannel channel,long offset,int length)throws IOException {
        ByteBuffer buffer=ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);channel.position(offset);
        while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new EOFException("Truncated Peer ZIP");buffer.flip();return buffer;
    }
}
