package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.fakeplayer.FakePlayerListCache;
import com.micaftic.morpher.fakeplayer.FakePlayerListEntry;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestFakePlayerListPacket;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Unified target picker for fake-player and maid action wheels.
 *
 * <p>The picker deliberately does not inspect Minecraft.hitResult. It uses the
 * server-authoritative fake-player snapshot plus loaded nearby maid entities,
 * then hands the selected entity to the existing unified action roulette.</p>
 */
public final class TargetActionSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final List<TargetRow> targets = new ArrayList<>();
    private int page;
    private int refreshTicks;
    private int lastFingerprint = Integer.MIN_VALUE;

    private TargetActionSelectionScreen() {
        super(Component.literal("SPM Target Action Wheel"));
    }

    public static void open() {
        InputUtil.setScreen(new TargetActionSelectionScreen());
        if (NetworkHandler.isClientConnected()) {
            NetworkHandler.sendToServer(new C2SRequestFakePlayerListPacket());
        }
    }

    @Override
    protected void init() {
        rebuildTargets();
        rebuildTargetWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            int fingerprint = fingerprint();
            if (fingerprint != lastFingerprint) {
                rebuildTargets();
                rebuildTargetWidgets();
            }
        }
    }

    private int fingerprint() {
        int result = FakePlayerListCache.entries().hashCode();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            result = 31 * result + minecraft.level.getEntitiesOfClass(
                    LivingEntity.class,
                    minecraft.player.getBoundingBox().inflate(64.0),
                    entity -> TouhouMaidCompat.isMaidEntity(entity)).size();
        }
        return result;
    }

    private void rebuildTargets() {
        targets.clear();
        Set<UUID> seen = new HashSet<>();
        for (FakePlayerListEntry entry : FakePlayerListCache.entries()) {
            if (seen.add(entry.uuid())) {
                String name = entry.displayName().isBlank() ? entry.name() : entry.displayName();
                targets.add(new TargetRow(entry.uuid(), "[假人] " + name, true));
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player local = minecraft.player;
        if (level != null && local != null) {
            AABB area = local.getBoundingBox().inflate(64.0);
            for (LivingEntity maid : level.getEntitiesOfClass(
                    LivingEntity.class, area, entity -> TouhouMaidCompat.isMaidEntity(entity))) {
                if (seen.add(maid.getUUID())) {
                    targets.add(new TargetRow(maid.getUUID(),
                            "[女仆] " + maid.getDisplayName().getString(), false));
                }
            }
        }
        targets.sort(Comparator.comparing(TargetRow::label, String.CASE_INSENSITIVE_ORDER));
        int pageCount = Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pageCount - 1);
        lastFingerprint = fingerprint();
    }

    private void rebuildTargetWidgets() {
        clearWidgets();
        int left = width / 2 - 190;
        int top = Math.max(32, height / 2 - 118);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < targets.size(); i++) {
            TargetRow row = targets.get(start + i);
            int col = i / 4;
            int line = i % 4;
            addRenderableWidget(Button.builder(Component.literal(row.label()),
                    ignored -> openActionWheel(row))
                    .bounds(left + col * 194, top + line * 28, 186, 24).build());
        }

        int pageCount = Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        addRenderableWidget(Button.builder(Component.literal("上一页"),
                ignored -> { if (page > 0) { page--; rebuildTargetWidgets(); } })
                .bounds(left, top + 132, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"),
                ignored -> { if (page + 1 < pageCount) { page++; rebuildTargetWidgets(); } })
                .bounds(left + 100, top + 132, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"),
                ignored -> { page = 0; rebuildTargets(); rebuildTargetWidgets(); open(); })
                .bounds(left + 204, top + 132, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),
                ignored -> onClose())
                .bounds(left + 304, top + 132, 82, 20).build());
    }

    private void openActionWheel(TargetRow row) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity entity = resolve(level, row);
        if (entity == null) {
            fail("目标尚未加载到客户端，无法打开动作轮盘。");
            return;
        }
        if (row.fakePlayer()) {
            PlayerCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail("假人的 YSM 模型能力尚未同步。"));
        } else {
            MaidCapability.get(entity).ifPresentOrElse(
                    cap -> openRoulette(cap.getModelId(), cap.getModelAssembly(), cap),
                    () -> fail("女仆的 YSM 模型能力尚未就绪。"));
        }
    }

    private static Entity resolve(ClientLevel level, TargetRow row) {
        if (level == null) {
            return null;
        }
        if (row.fakePlayer()) {
            return level.getPlayerByUUID(row.uuid());
        }
        Player local = Minecraft.getInstance().player;
        if (local == null) {
            return null;
        }
        List<LivingEntity> maids = level.getEntitiesOfClass(
                LivingEntity.class,
                local.getBoundingBox().inflate(96.0),
                entity -> row.uuid().equals(entity.getUUID()) && TouhouMaidCompat.isMaidEntity(entity));
        return maids.isEmpty() ? null : maids.get(0);
    }

    private void openRoulette(String modelId, com.micaftic.morpher.client.model.ModelAssembly assembly,
                              com.micaftic.morpher.geckolib3.core.AnimatableEntity<?> target) {
        if (assembly == null || assembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
            fail("目标模型没有可用的额外动作。");
            return;
        }
        InputUtil.setScreen(new UnifiedRouletteScreen(modelId, assembly, target));
    }

    private void fail(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("SPM: " + message));
        }
    }

    private record TargetRow(UUID uuid, String label, boolean fakePlayer) {
    }
}
