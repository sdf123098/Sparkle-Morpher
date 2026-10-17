package com.micaftic.morpher.peer;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import java.io.IOException;
import java.util.*;

/** Conservative admission estimates, held through deferred assembly release and GPU trimming. */
public final class PeerResidentReservation implements AutoCloseable {
    public static final PeerResourceBudget CPU=new PeerResourceBudget(512L*1024*1024);
    public static final PeerResourceBudget GPU=new PeerResourceBudget(256L*1024*1024);
    private final PeerResourceBudget.Lease cpu,gpu;
    private int references=1;private boolean ownerClosed;
    public synchronized AutoCloseable retain(){if(ownerClosed)throw new IllegalStateException("Peer reservation closed");references++;var released=new java.util.concurrent.atomic.AtomicBoolean();return ()->{if(released.compareAndSet(false,true))releaseReference();};}
    private synchronized void releaseReference(){if(--references==0){cpu.close();gpu.close();}}
    private PeerResidentReservation(long cpuBytes,long gpuBytes)throws IOException {
        cpu=CPU.reserve(cpuBytes);try{gpu=GPU.reserve(gpuBytes);}catch(IOException|RuntimeException rejected){cpu.close();throw rejected;}
    }
    public static PeerResidentReservation reserve(long sourceBytes,long pixels,long vertices,long bones)throws IOException {
        return reserve(sourceBytes,pixels,vertices,bones,0);
    }
    private static PeerResidentReservation reserve(long sourceBytes,long pixels,long vertices,long bones,long extraCpu)throws IOException {
        long cpu=Math.addExact(16L*1024*1024,Math.addExact(Math.multiplyExact(sourceBytes,2),Math.addExact(Math.multiplyExact(pixels,8),Math.addExact(Math.multiplyExact(vertices,128),Math.multiplyExact(bones,2048)))));
        long gpu=Math.addExact(Math.multiplyExact(pixels,6),Math.multiplyExact(vertices,128));
        return new PeerResidentReservation(Math.addExact(cpu,extraCpu),gpu);
    }
    public static PeerResidentReservation raw(RawYsmModel raw,long sourceBytes)throws IOException {
        long pixels=0,vertices=0,bones=0;var geometries=new ArrayList<RawYsmModel.RawGeometry>();geometries.add(raw.mainEntity.mainModel);geometries.add(raw.mainEntity.armModel);
        var textures=new ArrayList<>(raw.mainEntity.textures.values());for(var sub:raw.vehicles.values()){geometries.add(sub.model);textures.addAll(sub.textures.values());}for(var sub:raw.projectiles.values()){geometries.add(sub.model);textures.addAll(sub.textures.values());}
        for(var geometry:geometries)if(geometry!=null){bones+=geometry.bones.size();for(var bone:geometry.bones)for(var cube:bone.cubes)vertices+=(long)cube.faces.size()*4;}
        for(var texture:textures){pixels+=PeerModelPreflight.texturePixelCount(texture.width,texture.height,texture.imageFormat,texture.data);for(var sub:texture.subTextures)pixels+=PeerModelPreflight.texturePixelCount(sub.width,sub.height,sub.imageFormat,sub.data);}
        for(var author:raw.metadata.authors)if(author.avatarImage!=null)pixels+=image(author.avatarImage);
        for(var image:raw.metadata.extraAvatars)pixels+=image(image);for(var image:raw.properties.backgroundImages)pixels+=image(image);
        long pcm=0;for(var sound:raw.soundFiles.values())if(sound.data!=null&&sound.data.length>0)pcm+=PeerAudioPreflight.pcmBytes(sound.data);
        return reserve(sourceBytes,pixels,vertices,bones,Math.multiplyExact(pcm,2));
    }
    private static long image(RawYsmModel.RawImage image)throws IOException {return PeerModelPreflight.texturePixelCount(image.width,image.height,image.format,image.data);}
    public static PeerResidentReservation gltf(com.micaftic.morpher.resource.gltf.GltfModel model,long sourceBytes)throws IOException {
        long pixels=0,vertices=0;for(var image:model.images())pixels+=PeerModelPreflight.imagePixelCount(image.data());
        for(var mesh:model.meshes())for(var primitive:mesh.primitives())vertices+=Math.max(primitive.vertexCount(),(long)primitive.triangleCount()*3);
        return reserve(sourceBytes,pixels,vertices,model.nodes().size());
    }
    @Override public synchronized void close(){if(ownerClosed)return;ownerClosed=true;releaseReference();}
}
