package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.config.ConfigPolicies;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;

public class EntityRenderCache {

    private static final ReferenceArrayList<WeakReference<GeoEntity<?>>> weakRefs = new ReferenceArrayList<>(64);

    public static void register(GeoEntity<?> entity) {
        weakRefs.add(new WeakReference<>(entity));
    }

    public static void tick(float partialTick) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        ObjectListIterator<WeakReference<GeoEntity<?>>> it = weakRefs.iterator();
        while (it.hasNext()) {
            GeoEntity geoEntity = (GeoEntity) ((WeakReference<?>) it.next()).get();
            if (geoEntity == null) {
                it.remove();
            } else if (!geoEntity.isDebugMode()) {
                it.remove();
            } else {
                geoEntity.tickModel();
                Entity entity = geoEntity.getEntity();
                if (entity instanceof AbstractClientPlayer) {
                    if (entity instanceof LocalPlayer) {
                        if (!ConfigPolicies.render().disableSelfModel()) {
                            capturePlayerState(geoEntity, (AbstractClientPlayer) entity, partialTick);
                        }
                    } else if (!ConfigPolicies.render().disableOtherModel()) {
                        capturePlayerState(geoEntity, (AbstractClientPlayer) entity, partialTick);
                    }
                }
            }
        }
    }

    private static void capturePlayerState(GeoEntity<?> geoEntity, AbstractClientPlayer player, float partialTick) {
        if (geoEntity instanceof PlayerCapability capability) {
            capability.captureFrameRenderState(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot), partialTick);
        }
    }

    public static void clear() {
        // R1.5：strongRefs 从未被 register 填充（恒空），其 awaitAsyncResult 等待从未执行，
        // 删除 dead path。异步动画任务等待的实际语义见 R10（生命周期收敛）。
        weakRefs.clear();
    }

    public static boolean isModelAssemblyInUse(ModelAssembly assembly) {
        if (assembly == null) return false;
        ObjectListIterator<WeakReference<GeoEntity<?>>> it = weakRefs.iterator();
        while (it.hasNext()) {
            GeoEntity<?> geoEntity = it.next().get();
            if (geoEntity == null) {
                it.remove();
            } else if (geoEntity.referencesModelAssembly(assembly)) {
                return true;
            }
        }
        return false;
    }
}
