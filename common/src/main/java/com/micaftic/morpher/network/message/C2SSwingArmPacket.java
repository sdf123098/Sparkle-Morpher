package com.micaftic.morpher.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.micaftic.morpher.core.api.item.ToolActionBridge;
import com.micaftic.morpher.core.api.network.PacketContext;

public class C2SSwingArmPacket {

    private final InteractionHand hand;

    public C2SSwingArmPacket(InteractionHand hand) {
        this.hand = hand;
    }

    public static void encode(C2SSwingArmPacket message, FriendlyByteBuf buf) {
        buf.writeEnum(message.hand);
    }

    public static C2SSwingArmPacket decode(FriendlyByteBuf buf) {
        return new C2SSwingArmPacket(buf.readEnum(InteractionHand.class));
    }

    public static void handle(C2SSwingArmPacket message, PacketContext ctx) {
        ServerPlayer sender = ctx.getSender();
        if (ctx.isServerSide() && sender != null) {
            ctx.enqueueWork(() -> processSwingArm(message, sender));
        }
    }

    public static void processSwingArm(C2SSwingArmPacket message, ServerPlayer sender) {
        InteractionHand interactionHand = message.hand;
        ItemStack itemInHand = sender.getItemInHand(interactionHand);
        if (itemInHand.isEmpty() || !ToolActionBridge.onEntitySwing(itemInHand, sender)) {
            if (!sender.swinging || sender.swingTime >= getSwingDuration(sender) / 2 || sender.swingTime < 0) {
                sender.swingTime = -1;
                sender.swinging = true;
                sender.swingingArm = interactionHand;
                if (sender.level() instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().sendToTrackingPlayers(sender, new ClientboundAnimatePacket(sender, interactionHand == InteractionHand.MAIN_HAND ? 0 : 3));
                }
            }
        }
    }
    private static int getSwingDuration(LivingEntity entity) {
        if (MobEffectUtil.hasDigSpeed(entity)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(entity));
        }
        if (entity.hasEffect(MobEffects.MINING_FATIGUE)) {
            return 6 + ((1 + entity.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2);
        }
        return 6;
    }
}
