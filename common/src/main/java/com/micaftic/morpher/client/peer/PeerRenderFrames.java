package com.micaftic.morpher.client.peer;

import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.peer.PeerFrameEvidence;
import com.micaftic.morpher.peer.PeerFrameWork;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;

/** GPU handles are created, polled with timeout zero, and closed only on the render thread. */
public final class PeerRenderFrames {
    private interface Fence extends AutoCloseable {boolean complete();void close();}
    private record Pending(PeerFrameEvidence.Batch batch,Fence fence,long began) {}
    private static final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private static long frame;
    private PeerRenderFrames(){}
    public static boolean executing(OuterFileTexture texture){return PeerFrameWork.PEER.executing(texture);}
    public static void schedule(OuterFileTexture texture,Runnable action) {
        PeerFrameWork.PEER.enqueue(texture,texture.peerUploadBytes(),()->!texture.isSourceReleased(),()->{
            try{action.run();}catch(RuntimeException failure){com.micaftic.morpher.peer.PeerDiagnostics.uploadFailed(texture.peerModelId(),failure);throw failure;}
        });
    }
    public static void begin() {
        RenderSystem.assertOnRenderThread();frame++;
        var iterator=pending.iterator();while(iterator.hasNext()){
            var item=iterator.next();boolean done=false,success=false;
            try {if(item.batch.epoch()!=PeerFrameEvidence.GLOBAL.epoch()||System.nanoTime()-item.began>10_000_000_000L)done=true;
                else if(item.fence.complete()){done=true;success=true;}}
            catch(RuntimeException failed){done=true;}
            if(done){try{item.fence.close();}catch(RuntimeException ignored){}iterator.remove();PeerFrameEvidence.GLOBAL.complete(item.batch,success,frame);}
        }
        PeerFrameWork.PEER.frame();
    }
    public static void end() {
        RenderSystem.assertOnRenderThread();var batch=PeerFrameEvidence.GLOBAL.seal(frame);if(batch==null)return;
        if(pending.size()>=64){PeerFrameEvidence.GLOBAL.complete(batch,false);return;}
        try {pending.addLast(new Pending(batch,newFence(),System.nanoTime()));}
        catch(RuntimeException unavailable){PeerFrameEvidence.GLOBAL.complete(batch,false);}
    }
    private static Fence newFence() {
        var fence=RenderSystem.getDevice().createCommandEncoder().createFence();
        return new Fence(){public boolean complete(){return fence.awaitCompletion(0L);}public void close(){fence.close();}};
    }
    public static void close() {
        RenderSystem.assertOnRenderThread();while(!pending.isEmpty())try{pending.removeFirst().fence.close();}catch(RuntimeException ignored){}
    }
}
