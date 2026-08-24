package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.google.common.cache.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import com.micaftic.morpher.core.api.PlatformAPI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EntityJoinCallbackEvent {
    private static final Cache<Integer, List<Consumer<Entity>>> cache = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.SECONDS).build();
    private EntityJoinCallbackEvent() {}
    public static void register() { if (!PlatformAPI.isServer()) NeoForge.EVENT_BUS.addListener(EntityJoinCallbackEvent::onJoin); }
    private static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!YesSteveModel.isAvailable() || !event.getLevel().isClientSide()) return;
        List<Consumer<Entity>> list = cache.getIfPresent(e.getId());
        if (list != null) for (Consumer<Entity> c : list) c.accept(e);
        cache.invalidate(e.getId());
    }
    public static void addCallback(int id, Consumer<Entity> consumer) {
        Minecraft.getInstance().execute(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                Entity entity = level.getEntity(id);
                if (entity != null) { consumer.accept(entity); } else { List<Consumer<Entity>> l = cache.getIfPresent(id); if (l == null) { l = new ArrayList<>(3); cache.put(id, l); } l.add(consumer); }
            }
        });
    }
}