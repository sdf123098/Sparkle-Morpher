package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.ItemTagsConstants;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.micaftic.morpher.core.api.PlatformAPI;

public class InputStateKey {

    public static volatile boolean[] keyStates = new boolean[349];

    public static volatile boolean[] mouseStates = new boolean[8];

    private static final int INTERACTION_PULSE_TICKS = 10;
    private static final int USE_PENDING_TICKS = 2;

    private static volatile InteractionHand usePulseHand = InteractionHand.MAIN_HAND;
    private static volatile ItemStack usePulseItem = ItemStack.EMPTY;

    private static volatile InteractionHand swingPulseHand = InteractionHand.MAIN_HAND;

    private static volatile int usePulseTicks;

    private static volatile int usePulseAge;

    private static volatile int swingPulseTicks;

    private static volatile int swingPulseAge;

    private static volatile int swingPulseSequence;

    private static volatile boolean lastAttackKeyDown;

    private static volatile boolean lastUseKeyDown;

    private InputStateKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            onKeyInput(keyCode, action);
            return EventResult.pass();
        });
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, button, action, mods) -> {
            onMouseInput(button, action);
            return EventResult.pass();
        });
    }

    private static void onKeyInput(int keyCode, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 32 <= keyCode && keyCode <= 348) {
            if (action == 1) {
                keyStates[keyCode] = true;
            } else if (action == 0) {
                keyStates[keyCode] = false;
            }
        }
    }

    private static void onMouseInput(int button, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 0 <= button && button <= 7) {
            if (action == 1) {
                mouseStates[button] = true;
            } else if (action == 0) {
                mouseStates[button] = false;
            }
            logInputSnapshot("mouse button=" + button + " action=" + action);
            triggerHandAnimation(button, action);
        }
    }

    public static void tick() {
        tickAttackKey();
        if (usePulseTicks > 0) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isUsingItem() || !isUsePulseValid(player, usePulseHand)) clearUsePulse();
            else { usePulseTicks--; usePulseAge++; if (usePulseTicks <= 0) clearUsePulse(); }
        }
        if (swingPulseTicks > 0) {
            swingPulseTicks--;
            swingPulseAge++;
        }
    }

    private static void tickAttackKey() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !YesSteveModel.isAvailable() || !InputUtil.isPlayerReady()) {
            return;
        }
        boolean attackDown = minecraft.options.keyAttack.isDown();
        boolean useDown = minecraft.options.keyUse.isDown();
        boolean attackChanged = attackDown != lastAttackKeyDown;
        boolean useChanged = useDown != lastUseKeyDown;
        if (attackChanged) {
            lastAttackKeyDown = attackDown;
        }
        if (useChanged) {
            lastUseKeyDown = useDown;
        }
        if (attackChanged || useChanged) {
            logInputSnapshot("key attack=" + attackDown + " use=" + useDown);
        }
        if (attackDown && attackChanged && !isUsingOffhandShield(player)) {
            recordSwingPulse(InteractionHand.MAIN_HAND);
        }
    }

    public static boolean isUsingItem(LivingEntity entity, InteractionHand hand) {
        if (entity == null || entity.isSleeping()) {
            return false;
        }
        if (entity.isUsingItem() && entity.getUsedItemHand() == hand) {
            return true;
        }
        return hasValidUsePulse(entity, hand);
    }

    public static InteractionHand getUsedItemHand(LivingEntity entity) {
        if (entity.isUsingItem()) {
            return entity.getUsedItemHand();
        }
        return hasValidUsePulse(entity, usePulseHand) ? usePulseHand : InteractionHand.MAIN_HAND;
    }

    public static int getTicksUsingItem(LivingEntity entity) {
        if (entity.isUsingItem()) {
            return entity.getTicksUsingItem();
        }
        return hasValidUsePulse(entity, usePulseHand) ? Math.max(1, usePulseAge) : 0;
    }

    public static float getTicksUsingItem(LivingEntity entity, float partialTick) {
        if (entity.isUsingItem()) {
            return entity.getTicksUsingItem(partialTick);
        }
        return hasValidUsePulse(entity, usePulseHand) ? Math.max(1.0f, usePulseAge + partialTick) : 0.0f;
    }

    public static float getUseAnimationTicks(LivingEntity entity, float partialTick) {
        return Math.max(0.0f, getTicksUsingItem(entity, partialTick) - 1.0f);
    }

    public static float getSwingTicks(LivingEntity entity, float partialTick) {
        if (entity == null || entity.isSleeping()) {
            return 0.0f;
        }
        if (isUsingLocalOffhandShield(entity)) {
            return 0.0f;
        }
        if (entity.swinging) {
            return Math.max(0.0f, entity.swingTime + partialTick);
        }
        if (isLocalPlayer(entity) && swingPulseTicks > 0) {
            return Math.max(1.0f, swingPulseAge + partialTick);
        }
        return 0.0f;
    }

    public static float getSwingAnimationTicks(LivingEntity entity, float partialTick) {
        return Math.max(0.0f, getSwingTicks(entity, partialTick) - 1.0f);
    }

    public static float getAttackProgress(LivingEntity entity, float partialTick) {
        if (entity == null || entity.isSleeping()) {
            return 0.0f;
        }
        if (isUsingLocalOffhandShield(entity)) {
            return 0.0f;
        }
        float attackAnim = entity.getAttackAnim(partialTick);
        if (attackAnim > 0.0f) {
            return attackAnim;
        }
        if (isLocalPlayer(entity) && swingPulseTicks > 0 && swingPulseHand == InteractionHand.MAIN_HAND) {
            return Math.min(1.0f, getSwingTicks(entity, partialTick) / 6.0f);
        }
        return 0.0f;
    }

    public static boolean isSwinging(LivingEntity entity, InteractionHand hand) {
        if (entity == null || entity.isSleeping()) {
            return false;
        }
        if (hand == InteractionHand.MAIN_HAND && isUsingLocalOffhandShield(entity)) {
            return false;
        }
        if (entity.swinging && entity.swingingArm == hand) {
            return true;
        }
        if (!isLocalPlayer(entity) && hand == InteractionHand.MAIN_HAND && entity.getAttackAnim(0.0f) > 0.0f) {
            return true;
        }
        return isLocalPlayer(entity) && swingPulseTicks > 0 && swingPulseHand == hand;
    }

    public static InteractionHand getSwingingHand(LivingEntity entity) {
        if (entity.swinging) {
            return entity.swingingArm;
        }
        return swingPulseHand;
    }

    public static boolean isAnyHandSwinging(LivingEntity entity) {
        return isSwinging(entity, InteractionHand.MAIN_HAND) || isSwinging(entity, InteractionHand.OFF_HAND);
    }

    public static boolean isLocalPlayerEntity(LivingEntity entity) {
        return isLocalPlayer(entity);
    }

    public static boolean isLocalSwinging(InteractionHand hand) {
        return swingPulseTicks > 0 && swingPulseHand == hand;
    }

    public static boolean isLocalAnyHandSwinging() {
        return swingPulseTicks > 0;
    }

    public static InteractionHand getLocalSwingingHand() {
        return swingPulseHand;
    }

    public static int getLocalSwingPulseTicks() {
        return swingPulseTicks;
    }

    public static int getLocalSwingPulseAge() {
        return swingPulseAge;
    }

    public static int getLocalSwingPulseSequence() {
        return swingPulseSequence;
    }

    public static boolean hasLocalInteractionState() {
        LocalPlayer player = Minecraft.getInstance().player;
        return (player != null && hasValidUsePulse(player, usePulseHand)) || swingPulseTicks > 0 || (player != null && (player.isUsingItem() || player.swinging));
    }

    private static void triggerHandAnimation(int button, int action) {
        if (button != 0 && button != 1) {
            return;
        }
        if (button == 1 && action == 0) { clearUsePulse(); return; }
        if (action != 1) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        InteractionHand hand = resolveClickHand(player, button);
        if (button == 0) {
            if (isUsingOffhandShield(player)) {
                return;
            }
            recordSwingPulse(hand);
            return;
        }
        recordUsePulse(player, hand);
    }

    private static InteractionHand resolveClickHand(LocalPlayer player, int button) {
        if (button == 0) {
            return InteractionHand.MAIN_HAND;
        }
        if (button == 1 && isShield(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        if (button == 1 && player.getMainHandItem().isEmpty() && shouldSwingOffhandOnRightClick(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private static void recordUsePulse(LocalPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canStartContinuousUse(stack)) { clearUsePulse(); return; }
        boolean wasActive = isUsePulseValid(player, hand);
        usePulseHand = hand;
        usePulseItem = stack.copy();
        usePulseTicks = USE_PENDING_TICKS;
        usePulseAge = 1;
        if (!wasActive) {
            logInputSnapshot("use-pulse hand=" + hand);
        }
    }

    private static void recordSwingPulse(InteractionHand hand) {
        boolean wasActive = swingPulseTicks > 0 && swingPulseHand == hand;
        swingPulseHand = hand;
        swingPulseTicks = INTERACTION_PULSE_TICKS;
        swingPulseAge = 1;
        swingPulseSequence = swingPulseSequence == Integer.MAX_VALUE ? 1 : swingPulseSequence + 1;
        if (!wasActive) {
            logInputSnapshot("swing-pulse hand=" + hand);
        }
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return entity == player || (player != null && entity instanceof Player other && other.getUUID().equals(player.getUUID()));
    }

    private static boolean shouldSwingOffhandOnRightClick(ItemStack offhandItem) {
        return !offhandItem.isEmpty() && !offhandItem.is(Items.TOTEM_OF_UNDYING);
    }

    private static boolean canStartContinuousUse(ItemStack stack) { return !stack.isEmpty() && stack.getUseAnimation() != ItemUseAnimation.NONE; }
    private static boolean hasValidUsePulse(LivingEntity entity, InteractionHand hand) { return isLocalPlayer(entity) && isUsePulseValid(entity, hand); }
    private static boolean isUsePulseValid(LivingEntity entity, InteractionHand hand) {
        if (entity == null || hand == null || usePulseTicks <= 0 || usePulseHand != hand || usePulseItem.isEmpty()) return false;
        ItemStack current = entity.getItemInHand(hand);
        return !current.isEmpty() && current.getItem() == usePulseItem.getItem();
    }
    private static void clearUsePulse() { usePulseTicks = 0; usePulseAge = 0; usePulseItem = ItemStack.EMPTY; }

    private static boolean isUsingOffhandShield(LocalPlayer player) {
        return player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.OFF_HAND
                && isShield(player.getUseItem());
    }

    private static boolean isUsingLocalOffhandShield(LivingEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && isLocalPlayer(entity) && isUsingOffhandShield(player);
    }

    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.SHIELD) || stack.is(ItemTagsConstants.SHIELDS));
    }

    private static void logInputSnapshot(String reason) {
        if (!GeneralConfig.safeGet(GeneralConfig.INPUT_STATE_DEBUG_LOG, false)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            YesSteveModel.LOGGER.info("[SM-INPUT] {} player=null mouse0={} mouse1={} usePulse={} useAge={} useHand={} swingPulse={} swingAge={} swingHand={}",
                    reason, mouseStates[0], mouseStates[1], usePulseTicks, usePulseAge, usePulseHand, swingPulseTicks, swingPulseAge, swingPulseHand);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        YesSteveModel.LOGGER.info("[SM-INPUT] {} mouse0={} mouse1={} attackKey={} useKey={} vanillaSwinging={} vanillaSwingArm={} attackAnim={} usingItem={} usedHand={} usePulse={} useAge={} useHand={} swingPulse={} swingAge={} swingHand={} mainItem={} offItem={}",
                reason,
                mouseStates[0],
                mouseStates[1],
                minecraft.options.keyAttack.isDown(),
                minecraft.options.keyUse.isDown(),
                player.swinging,
                player.swingingArm,
                player.getAttackAnim(0.0f),
                player.isUsingItem(),
                player.isUsingItem() ? player.getUsedItemHand() : "none",
                usePulseTicks,
                usePulseAge,
                usePulseHand,
                swingPulseTicks,
                swingPulseAge,
                swingPulseHand,
                player.getMainHandItem().getItem(),
                player.getOffhandItem().getItem());
    }
}
