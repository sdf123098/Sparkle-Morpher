package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.cloud.client.CloudProjectileModelSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the entity after vanilla has restored native ownership and motion. */
@Mixin(ClientPacketListener.class)
public final class ProjectileSpawnObservationMixin {
    @Inject(method="handleAddEntity",at=@At("TAIL"))
    private void spm$observeProjectileSpawn(ClientboundAddEntityPacket packet,CallbackInfo ci){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(packet.getId()) instanceof Projectile projectile)
            CloudProjectileModelSync.nativeSpawn(projectile);
    }
}
