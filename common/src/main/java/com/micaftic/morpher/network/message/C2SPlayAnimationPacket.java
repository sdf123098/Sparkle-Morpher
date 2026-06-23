package com.micaftic.morpher.network.message;

import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.util.data.OrderedStringMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.StringUtils;
import com.micaftic.morpher.core.api.network.PacketContext;

import java.util.Map;

public class C2SPlayAnimationPacket {

    private final int animationIndex;

    private final String category;

    private final int entityId;

    public C2SPlayAnimationPacket(int animationIndex, String category, int entityId) {
        this.animationIndex = animationIndex;
        this.category = category;
        this.entityId = entityId;
    }

    public C2SPlayAnimationPacket(int animationIndex, String category) {
        this(animationIndex, category, -1);
    }

    public static C2SPlayAnimationPacket createDefault() {
        return new C2SPlayAnimationPacket(-1, StringPool.EMPTY);
    }

    public static C2SPlayAnimationPacket createWithIndex(int entityId) {
        return new C2SPlayAnimationPacket(-1, StringPool.EMPTY, entityId);
    }

    public static void encode(C2SPlayAnimationPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.animationIndex);
        buf.writeUtf(message.category);
        buf.writeVarInt(message.entityId);
    }

    public static C2SPlayAnimationPacket decode(FriendlyByteBuf buf) {
        return new C2SPlayAnimationPacket(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(C2SPlayAnimationPacket message, PacketContext ctx) {
        if (ctx.isServerSide()) {
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender == null) {
                    return;
                }
                handleCapability(message, sender);
            });
        }
    }

    private static void handleCapability(C2SPlayAnimationPacket message, ServerPlayer sender) {
        if (message.entityId != -1) {
            Entity entity = sender.level().getEntity(message.entityId);
            if (TouhouMaidCompat.isMaidEntity(entity)) {
                TouhouMaidCompat.registerAnimationRoulette(entity, message.category, message.animationIndex);
                return;
            }
            return;
        }

        ModelInfoCapability.get(sender).ifPresent(modelInfoCap -> {
            if (message.animationIndex == -1) {
                modelInfoCap.stopAnimation(sender);
            } else {
                ServerModelManager.getModelDefinition(modelInfoCap.getModelId()).ifPresent(serverModelCap -> {
                    OrderedStringMap<String, String> extraAnimations;
                    ModelProperties modelProperties = serverModelCap.getLoadedModelData().getModelProperties();
                    Map<String, OrderedStringMap<String, String>> extraAnimationClassify = modelProperties.getExtraAnimationClassify();
                    if (StringUtils.isNotBlank(message.category) && extraAnimationClassify.containsKey(message.category)) {
                        extraAnimations = extraAnimationClassify.get(message.category);
                    } else {
                        extraAnimations = modelProperties.getExtraAnimation();
                    }
                    if (extraAnimations.size() > message.animationIndex) {
                        modelInfoCap.playAnimation(sender, extraAnimations.getKeyAt(message.animationIndex));
                    }
                });
            }
        });
    }
}