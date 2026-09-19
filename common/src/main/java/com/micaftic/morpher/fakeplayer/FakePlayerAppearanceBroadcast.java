package com.micaftic.morpher.fakeplayer;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Cloud-ready appearance event seam.
 *
 * <p>1.2.x keeps Minecraft tracking packets authoritative.  SPM Cloud can
 * install a publisher later without changing the GUI or the Carpet adapters;
 * the event shape already carries the stable fake-player subject and a local
 * monotonic revision.</p>
 */
public final class FakePlayerAppearanceBroadcast {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static volatile Consumer<AppearanceDelta> publisher = ignored -> { };

    private FakePlayerAppearanceBroadcast() {
    }

    public static void setPublisher(Consumer<AppearanceDelta> nextPublisher) {
        publisher = nextPublisher == null ? ignored -> { } : nextPublisher;
    }

    public static void publish(ServerPlayer target, String modelId, String textureId, boolean disabled) {
        if (target == null) {
            return;
        }
        AppearanceDelta delta = new AppearanceDelta(
                target.getUUID(),
                FakePlayerTargetRules.providerId(target),
                modelId,
                textureId,
                disabled,
                SEQUENCE.incrementAndGet());
        try {
            publisher.accept(delta);
        } catch (RuntimeException ignored) {
            // Cloud is optional.  A publisher failure must never break the MC
            // server-side appearance update or tracking broadcast.
        }
    }

    public record AppearanceDelta(UUID targetUuid, String providerId, String modelId,
                                  String textureId, boolean disabled, long sequence) {
    }
}
