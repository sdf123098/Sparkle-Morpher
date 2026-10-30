package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.core.render.SmGraphicsBackend;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.feature.submit.BatchableSubmit;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/** GPU skinning is a normal feature: Minecraft owns the world/hand/PIP phase and target. */
public final class VulkanModelFeatureRenderer implements FeatureRenderer<VulkanModelFeatureRenderer.Draw> {
    public static final FeatureRendererType<Draw> TYPE = FeatureRendererType.create("SparkleMorpherVulkanSkinning");
    private final List<Draw> prepared = new ArrayList<>();

    public record Draw(RenderType renderType, Blaze3DRenderPath.PreparedDraw prepared) implements BatchableSubmit {
        @Override public Object batchKey() { return renderType; }
        @Override public FeatureRendererType<Draw> featureType() { return TYPE; }
    }

    public static boolean trySubmit(SubmitNodeCollector collector, RenderType renderType,
            PoseStack.Pose pose, GeoModel model, float[] bones, float[] state, int packedLight,
            int packedOverlay, float r, float g, float b, float a, Identifier texture, BoneRenderPass bonePass) {
        return trySubmit(collector, renderType, pose, model, bones, state, packedLight, packedOverlay,
                r, g, b, a, texture, bonePass, 0);
    }

    public static boolean trySubmit(SubmitNodeCollector collector, RenderType renderType,
            PoseStack.Pose pose, GeoModel model, float[] bones, float[] state, int packedLight,
            int packedOverlay, float r, float g, float b, float a, Identifier texture, BoneRenderPass bonePass,
            int renderPartMask) {
        if (SmGraphicsBackendDetector.currentBackend() != SmGraphicsBackend.VULKAN
                || !Blaze3DRenderPath.isExperimentalEnabled() || OculusCompat.isShaderPackInUse()
                || !(collector.order(0) instanceof SubmitNodeCollection collection)) return false;
        var prepared = Blaze3DRenderPath.prepare(model, pose, bones, state, renderPartMask, packedLight, packedOverlay,
                r, g, b, a, texture, renderType.hasBlending(), bonePass, renderType.isOutline());
        if (prepared == null) return false;
        Draw draw = new Draw(renderType, prepared);
        if (renderType.isOutline()) collection.outline.submit(draw);
        else if (renderType.hasBlending()) collection.translucentCustomGeometry.submit(draw);
        else collection.solid.submit(draw);
        return true;
    }

    @Override public void prepareGroup(FeatureFrameContext context, List<Draw> submits, boolean ordered) {
        prepared.addAll(submits);
        for (Draw draw : submits) draw.prepared.prepareView();
    }

    @Override public void executeGroup(FeatureFrameContext context, int group, List<Draw> submits, boolean ordered) {
        for (Draw draw : submits) {
            draw.prepared.draw(draw.renderType.outputTarget().getRenderTarget());
        }
    }

    @Override public void finishExecute(FeatureFrameContext context) {
        for (Draw draw : prepared) draw.prepared.close();
        prepared.clear();
    }
}
