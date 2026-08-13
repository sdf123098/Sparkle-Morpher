package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class})
public class InventoryScreenMixin {
    @Inject(at = {@At("HEAD")}, method = {"extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"})
    private static void renderEntityInInventoryPre(GuiGraphicsExtractor extractor, int x, int y, int scale, int xDiff, int yDiff, float f1, float f2, float f3, LivingEntity entity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
        // 记录 GUI 预览实体：26.2 该方法用自建 extractRenderState（不经 EntityRenderDispatcher.extractEntity），
        // 供 EntityRenderDispatcherMixin 在 submit（captured==null）时补做 maid 替换。
        ModelPreviewRenderer.setGuiPreviewEntity(entity, f3);
    }

    @Inject(at = {@At("RETURN")}, method = {"extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"})
    private static void renderEntityInInventoryPost(GuiGraphicsExtractor extractor, int x, int y, int scale, int xDiff, int yDiff, float f1, float f2, float f3, LivingEntity entity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }
}