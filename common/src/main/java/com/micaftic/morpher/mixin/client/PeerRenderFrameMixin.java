package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.peer.PeerRenderFrames;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PeerRenderFrameMixin {
    @Inject(method="renderFrame",at=@At("HEAD"))
    private void sparkleMorpher$peerFrameBegin(CallbackInfo ci){PeerRenderFrames.begin();}
    @Inject(method="renderFrame",at=@At("RETURN"))
    private void sparkleMorpher$peerFrameEnd(CallbackInfo ci){PeerRenderFrames.end();}
    @Inject(method="close",at=@At("HEAD"))
    private void sparkleMorpher$peerFrameClose(CallbackInfo ci){PeerRenderFrames.close();}
}
