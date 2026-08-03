package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.model.ModelAssembly;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;

import java.lang.ref.WeakReference;

public class EntityRenderCache {

    private static final ReferenceArrayList<WeakReference<GeoEntity<?>>> weakRefs = new ReferenceArrayList<>(64);

    private static final ReferenceArrayList<GeoEntity<?>> strongRefs = new ReferenceArrayList<>(16);

    public static void register(GeoEntity<?> entity) {
        weakRefs.add(new WeakReference<>(entity));
    }

    public static void tick(float partialTick) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        AnimationFrameProfiler.beginRenderFrame(partialTick);
        ObjectListIterator<WeakReference<GeoEntity<?>>> it = weakRefs.iterator();
        while (it.hasNext()) {
            GeoEntity geoEntity = (GeoEntity) ((WeakReference<?>) it.next()).get();
            if (geoEntity == null) {
                it.remove();
            } else if (!geoEntity.isDebugMode()) {
                it.remove();
            } else {
                geoEntity.tickModel();
            }
        }
    }

    public static void clear() {
        ObjectListIterator<GeoEntity<?>> it = strongRefs.iterator();
        while (it.hasNext()) {
            try {
                it.next().awaitAsyncResult();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        strongRefs.clear();
    }

    public static boolean isModelAssemblyInUse(ModelAssembly assembly) {
        if (assembly == null) return false;
        for (GeoEntity<?> geoEntity : strongRefs) {
            if (geoEntity.referencesModelAssembly(assembly)) {
                return true;
            }
        }
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
