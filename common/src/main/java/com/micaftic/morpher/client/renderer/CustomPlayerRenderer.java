package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.event.ClientTickEvent;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.renderer.layer.CustomPlayerArmorLayer;
import com.micaftic.morpher.client.renderer.layer.CustomPlayerElytraLayer;
import com.micaftic.morpher.client.renderer.layer.CustomPlayerItemInHandLayer;
import com.micaftic.morpher.client.renderer.layer.CustomPlayerParrotLayer;
import com.micaftic.morpher.event.api.SpecialPlayerRenderEvent;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CustomPlayerRenderer extends GeoReplacedEntityRenderer<Player, CustomPlayerEntity> {

    private static final int TEXTURE_EVENT_CACHE_TICKS = 20;
    private static final int MAX_TEXTURE_EVENT_CACHE_SIZE = 256;

    private Identifier currentTexture;

    private final Map<UUID, CachedRenderTexture> renderTextureCache = new HashMap<>();

    @Override
    public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }

    public CustomPlayerRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayerRenderer(new CustomPlayerItemInHandLayer(context.getEntityRenderDispatcher().getItemInHandRenderer()));
        addLayerRenderer(new CustomPlayerElytraLayer(context));
        addLayerRenderer(new CustomPlayerParrotLayer(context));
        addLayerRenderer(new CustomPlayerArmorLayer(context));
    }

    public void render(Player player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        render(player, entityYaw, partialTick, poseStack, bufferSource, null, packedLight);
    }

    public void render(Player player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, SubmitNodeCollector collector, int packedLight) {
        if (SWarfareCompat.isPlayerAiming(player)) {
            return;
        }
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability == null) {
            return;
        }
        render(capability, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
    }

    public void render(PlayerCapability capability, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, SubmitNodeCollector collector, int packedLight) {
        Player player = (Player) capability.entity;
        capability.tickModel();
        String modelId = capability.getModelId();
        ClientModelManager.markModelUsed(modelId);
        CachedRenderTexture renderTexture = getRenderTexture(player, capability, modelId);
        if (!renderTexture.allowed) {
            this.currentTexture = null;
            return;
        }
        this.currentTexture = renderTexture.location;
        SubmitRenderContext.set(collector);
        try {
            renderEntityWithTexture(capability, renderTexture.location, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            SubmitRenderContext.set(null);
            this.currentTexture = null;
        }
    }

    private CachedRenderTexture getRenderTexture(Player player, PlayerCapability capability, String modelId) {
        int tick = ClientTickEvent.getTickCount();
        UUID uuid = player.getUUID();
        CachedRenderTexture cached = this.renderTextureCache.get(uuid);
        if (cached != null && Objects.equals(cached.modelId, modelId) && tick - cached.tick < TEXTURE_EVENT_CACHE_TICKS) {
            return cached;
        }
        SpecialPlayerRenderEvent renderEvent = new SpecialPlayerRenderEvent(player, capability, modelId);
        CachedRenderTexture resolved = new CachedRenderTexture(modelId, tick, !SpecialPlayerRenderEvent.post(renderEvent).isFalse(), renderEvent.getTextureLocation());
        if (this.renderTextureCache.size() >= MAX_TEXTURE_EVENT_CACHE_SIZE) {
            this.renderTextureCache.clear();
        }
        this.renderTextureCache.put(uuid, resolved);
        return resolved;
    }

    private static final class CachedRenderTexture {
        private final String modelId;
        private final int tick;
        private final boolean allowed;
        @Nullable
        private final Identifier location;

        private CachedRenderTexture(String modelId, int tick, boolean allowed, @Nullable Identifier location) {
            this.modelId = modelId;
            this.tick = tick;
            this.allowed = allowed;
            this.location = location;
        }
    }

    @Override
    public boolean shouldShowName(Player entity) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        double dDistanceToSqr = Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity);
        float nameRenderDistance = entity.isDiscrete() ? 32.0f : 64.0f;
        if (dDistanceToSqr >= nameRenderDistance * nameRenderDistance || (localPlayer = (minecraft = Minecraft.getInstance()).player) == null) {
            return false;
        }
        // Hide nametag for the local player (self)
        if (entity == localPlayer) {
            return false;
        }
        boolean isVisible = !entity.isInvisibleTo(localPlayer);
        if (entity != localPlayer) {
            Team team = entity.getTeam();
            Team team2 = localPlayer.getTeam();
            if (team != null) {
                switch (team.getNameTagVisibility()) {
                    case ALWAYS:
                        return isVisible;
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return team2 == null ? isVisible : team.isAlliedTo(team2) && (team.canSeeFriendlyInvisibles() || isVisible);
                    case HIDE_FOR_OWN_TEAM:
                        return team2 == null ? isVisible : !team.isAlliedTo(team2) && isVisible;
                    default:
                        throw new IncompatibleClassChangeError();
                }
            }
        }
        return Minecraft.renderNames() && entity != minecraft.getCameraEntity() && isVisible && !entity.isVehicle();
    }

    @NotNull
    public Identifier getTextureLocation(Player player) {
        return this.currentTexture == null ? PlayerCapability.get(player).map((cap) -> cap.getTextureLocation()).orElse(MissingTextureAtlasSprite.getLocation()) : this.currentTexture;
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(LivingEntityRenderState renderState) {
        return this.currentTexture == null ? MissingTextureAtlasSprite.getLocation() : this.currentTexture;
    }

    public void renderNameTag(Player player, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float partialTick) {
        Scoreboard scoreboard = null;
        Objective displayObjective;
        if (PlayerPreviewEntity.isPreviewPlayer(player)) {
            return;
        }
        double dDistanceToSqr = Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(player);
        poseStack.pushPose();
        if (dDistanceToSqr < 100.0d && (displayObjective = (scoreboard = Minecraft.getInstance().getScoreboard()).getDisplayObjective(DisplaySlot.LIST)) != null) {
            // super.renderNameTag changed params in MC 1.26.1.2
            poseStack.translate(0.0d, 0.25875d, 0.0d);
        }
        // super.renderNameTag changed params in MC 1.26.1.2
        poseStack.popPose();
    }

    @Override
    public void preRenderCallback(Player player, PoseStack poseStack, float partialTick) {
        // Match vanilla AvatarRenderer scale so replaced players keep the same mount anchor as the original model.
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public void setupRotations(Player player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTicks);
        Entity vehicle = player.getVehicle();
        if (TouhouLittleMaidCompat.isSimplePlanesEntity(vehicle) || TouhouLittleMaidCompat.isImmersiveAircraftEntity(vehicle)) {
            poseStack.translate(0.0d, 0.5d, 0.0d);
        }
    }
}
