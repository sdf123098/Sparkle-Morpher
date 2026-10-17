package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Bounded chunks, ACK credit and authorized resume. The room control socket is independent. */
public final class PeerAssetWire {
    private PeerAssetWire(){}
    public static void send(PeerAssetStore.Verified asset,DataInputStream in,DataOutputStream out,BooleanSupplier current)throws IOException {
        send(asset,in,out,current,PeerCapabilities.current(),"ysm-v1");
    }
    public static void send(PeerAssetStore.Verified asset,DataInputStream in,DataOutputStream out,BooleanSupplier current,PeerCapabilities policy,String profile)throws IOException {
        policy.requireProfile(profile);policy.requireManifest(asset.manifest());
        try(var buffers=PeerResourceBudget.STREAM.reserve(4L*asset.manifest().canonicalSize()+8L*policy.chunkBytes())) {
            check(current);byte[] metadata=asset.manifest().canonicalBytes();
            new PeerFrame(PeerFrame.Type.ASSET_OFFER,0,ByteBuffer.allocate(4).putInt(metadata.length).array()).write(out);
            for(int offset=0;offset<metadata.length;offset+=PeerLimits.CONTROL_BYTES)
                new PeerFrame(PeerFrame.Type.MANIFEST,offset,Arrays.copyOfRange(metadata,offset,Math.min(metadata.length,offset+PeerLimits.CONTROL_BYTES))).write(out);
            long[] offsets=new long[asset.manifest().resources().size()];
            if(policy.resume()) {
                var request=PeerRoomClient.checkedRead(in,true);if(request.type()!=PeerFrame.Type.BLOCK_REQUEST || request.requestId()!=0)throw new IOException("Expected resume range request");
                offsets=PeerResumeState.decode(request.payload(),asset.manifest(),profile,policy.chunkBytes());check(current);
            }
            byte[] buffer=new byte[policy.chunkBytes()];long sequence=1;
            for(int file=0;file<offsets.length;file++) {
                long offset=offsets[file];PeerDiagnostics.resumedBytes.add(offset);var resource=asset.manifest().resources().get(file);
                try(var input=Files.newInputStream(asset.file(file),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    input.skipNBytes(offset);int n;
                    while((n=input.readNBytes(buffer,0,(int)Math.min(buffer.length,resource.byteSize()-offset)))>0) {
                        check(current);var bytes=new ByteArrayOutputStream(n+44);var block=new DataOutputStream(bytes);
                        byte[] data=Arrays.copyOf(buffer,n);block.writeInt(file);block.writeLong(offset);block.write(PeerContentRoot.digest(data).bytes());block.write(data);
                        PeerBandwidth.GLOBAL.acquire(n,current);PeerDiagnostics.bytesSent.add(n);new PeerFrame(PeerFrame.Type.ASSET_BLOCK,sequence,bytes.toByteArray()).write(out);
                        var ack=PeerRoomClient.checkedRead(in,true);if(ack.type()!=PeerFrame.Type.ACK || ack.requestId()!=sequence || ack.payload().length!=0)throw new IOException("Missing asset credit ACK");
                        sequence++;offset+=n;
                    }
                    if(offset!=resource.byteSize() || input.read()!=-1)throw new IOException("Peer source changed after verification");
                }
            }
            check(current);new PeerFrame(PeerFrame.Type.ACK,sequence,new byte[0]).write(out);
        }
    }
    public static PeerAssetStore.Verified receive(PeerContentRoot root,PeerAssetStore store,DataInputStream in,DataOutputStream out,BooleanSupplier current)throws IOException {
        return receive(root,store,in,out,current,PeerCapabilities.current(),"ysm-v1");
    }
    public static PeerAssetStore.Verified receive(PeerContentRoot root,PeerAssetStore store,DataInputStream in,DataOutputStream out,BooleanSupplier current,PeerCapabilities policy,String profile)throws IOException {
        policy.requireProfile(profile);check(current);
        var offer=PeerRoomClient.checkedRead(in,true);if(offer.type()!=PeerFrame.Type.ASSET_OFFER || offer.payload().length!=4)throw new IOException("Expected asset offer");
        int length=ByteBuffer.wrap(offer.payload()).getInt();if(length<1 || length>PeerLimits.MANIFEST_BYTES)throw new IOException("Invalid manifest length");
        try(var buffers=PeerResourceBudget.STREAM.reserve(4L*length+8L*policy.chunkBytes()+PeerLimits.MANIFEST_BYTES)) {
            byte[] metadata=new byte[length];int offset=0;
            while(offset<length) {
                check(current);var frame=PeerRoomClient.checkedRead(in,true);byte[] data=frame.payload();
                if(frame.type()!=PeerFrame.Type.MANIFEST || frame.requestId()!=offset || data.length<1 || data.length>length-offset)throw new IOException("Invalid manifest fragment");
                System.arraycopy(data,0,metadata,offset,data.length);offset+=data.length;
            }
            var manifest=PeerAssetManifest.decode(metadata);if(!manifest.contentRoot().equals(root))throw new IOException("Asset manifest root mismatch");policy.requireManifest(manifest);
            if(policy.resume())try(var partial=new PeerPartialReceive(store,manifest,profile,policy.chunkBytes(),current)) {
                new PeerFrame(PeerFrame.Type.BLOCK_REQUEST,0,PeerResumeState.encode(manifest,profile,policy.chunkBytes(),partial.offsets())).write(out);
                try {blocks(manifest,in,out,current,policy,partial.offsets(),partial::append,i->{});return partial.commit();}
                catch(java.net.SocketException|java.net.SocketTimeoutException|EOFException interrupted){if(current.getAsBoolean())partial.pause();throw interrupted;}
            }
            else try(var job=store.begin(manifest,current)) {
                blocks(manifest,in,out,current,policy,new long[manifest.resources().size()],job::append,job::finishFile);
                return job.commit();
            }
        }
    }
    @FunctionalInterface private interface Append {void accept(int file,long offset,byte[] bytes)throws IOException;}
    @FunctionalInterface private interface Finish {void accept(int file)throws IOException;}
    private static void blocks(PeerAssetManifest manifest,DataInputStream in,DataOutputStream out,BooleanSupplier current,PeerCapabilities policy,long[] offsets,Append append,Finish finish)throws IOException {
        long sequence=1;
        for(int file=0;file<offsets.length;file++) {
            long position=offsets[file];var resource=manifest.resources().get(file);
            while(position<resource.byteSize()) {
                check(current);var frame=PeerRoomClient.checkedRead(in,true);byte[] payload=frame.payload();
                if(frame.type()!=PeerFrame.Type.ASSET_BLOCK || frame.requestId()!=sequence || payload.length<45 || payload.length>policy.chunkBytes()+44)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CORRUPT_BLOCK);
                var block=new DataInputStream(new ByteArrayInputStream(payload));int index=block.readInt();long at=block.readLong();byte[] hash=new byte[32];block.readFully(hash);byte[] data=block.readAllBytes();
                if(index!=file || at!=position || data.length!=Math.min(policy.chunkBytes(),resource.byteSize()-position)
                        || !java.security.MessageDigest.isEqual(hash,PeerContentRoot.digest(data).bytes()))throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CORRUPT_BLOCK);
                PeerBandwidth.GLOBAL.acquire(data.length,current);PeerDiagnostics.bytesReceived.add(data.length);append.accept(file,position,data);position+=data.length;
                new PeerFrame(PeerFrame.Type.ACK,sequence++,new byte[0]).write(out);
            }
            finish.accept(file);
        }
        check(current);var complete=PeerRoomClient.checkedRead(in,true);
        if(complete.type()!=PeerFrame.Type.ACK || complete.requestId()!=sequence || complete.payload().length!=0)throw new IOException("Incomplete asset transfer");
    }
    private static void check(BooleanSupplier current)throws IOException {if(!current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.REVOKED);}
}
