package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.core.display.PlayerDisplayState;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class PlayerEntityFrameState extends LivingEntityFrameState<Player> {

    private final boolean isLocalPlayer;

    private PlayerDisplayState displayState = PlayerDisplayState.UNKNOWN;

    private static float headYawDelta;

    private static float lastYRot;

    public PlayerEntityFrameState(Player player, boolean isLocalPlayer) {
        super(player);
        this.isLocalPlayer = isLocalPlayer;
    }

    @Override
    public void reset() {
        super.reset();
        clearDisplayState();
    }

    public void applyDisplayState(PlayerDisplayState state) {
        if (!isLocalPlayer) displayState = Objects.requireNonNull(state);
    }
    public PlayerDisplayState displayState() { return displayState; }
    public void clearDisplayState() { displayState = PlayerDisplayState.UNKNOWN; }

    /** Only this client's own player can supply hidden display values. */
    public PlayerDisplayState snapshot(int maxEffects) {
        if (!isLocalPlayer) return PlayerDisplayState.UNKNOWN;
        Map<String, Integer> effects = new LinkedHashMap<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            int amplifier = effect.getAmplifier();
            if (amplifier < 0 || amplifier > 255 || effects.size() >= Math.min(256, maxEffects)) {
                effects = null;
                break;
            }
            effects.put(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(), amplifier + 1);
        }
        return new PlayerDisplayState(Math.max(0, entity.experienceLevel), health(entity.getHealth()),
                health(entity.getMaxHealth()), Mth.clamp(entity.getFoodData().getFoodLevel(), 0, 20),
                effects, entity.getAbilities().flying, input(entity.xxa), input(entity.yya),
                input(entity.zza), entity.isBlocking());
    }
    private static Float health(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1_000_000 ? value : null;
    }
    private static Float input(float value) { return Float.isFinite(value) ? Mth.clamp(value, -1, 1) : null; }
    private static float inputOrZero(Float value) { return value == null ? 0 : value; }
    public boolean isFlying() { return isLocalPlayer ? entity.getAbilities().flying : Boolean.TRUE.equals(displayState.flying()); }
    public int getExperienceLevel() { return isLocalPlayer ? entity.experienceLevel : displayState.experienceLevel() == null ? 0 : displayState.experienceLevel(); }
    public int getHealth() { return (int) (isLocalPlayer ? entity.getHealth() : displayState.health() == null ? 0 : displayState.health()); }
    public int getMaxHealth() { return (int) (isLocalPlayer ? entity.getMaxHealth() : displayState.maxHealth() == null ? 0 : displayState.maxHealth()); }
    public int getFoodLevel() { return isLocalPlayer ? entity.getFoodData().getFoodLevel() : displayState.foodLevel() == null ? 0 : displayState.foodLevel(); }
    public float getStrafeInput() { return inputOrZero(isLocalPlayer ? input(entity.xxa) : displayState.strafeInput()); }
    public float getVerticalInput() { return inputOrZero(isLocalPlayer ? input(entity.yya) : displayState.verticalInput()); }
    public float getForwardInput() { return inputOrZero(isLocalPlayer ? input(entity.zza) : displayState.forwardInput()); }
    public boolean isLocalPlayer() { return isLocalPlayer; }
    public boolean hasMovementInput() { return Math.abs(getStrafeInput()) > 1.0E-4f || Math.abs(getVerticalInput()) > 1.0E-4f || Math.abs(getForwardInput()) > 1.0E-4f; }
    public boolean isShieldBlocking() { return isLocalPlayer ? entity.isBlocking() : Boolean.TRUE.equals(displayState.shieldBlocking()); }
    public int getEffectAmplifier(Holder<MobEffect> mobEffect) {
        if (isLocalPlayer) {
            MobEffectInstance effect = entity.getEffect(mobEffect);
            return effect == null ? 0 : Mth.clamp(effect.getAmplifier(), 0, 255) + 1;
        }
        var effects = displayState.effectAmplifiers();
        return effects == null ? 0 : effects.getOrDefault(BuiltInRegistries.MOB_EFFECT.getKey(mobEffect.value()).toString(), 0);
    }

    @Override
    public void onTickUpdate(int currentTick, int previousTick) {
        if (this.isLocalPlayer) {
            updateHeadYaw(this.entity, currentTick, previousTick);
        }
        super.onTickUpdate(currentTick, previousTick);
    }

    private static void updateHeadYaw(Player player, int currentTick, int previousTick) {
        float yRot = player.getYRot();
        if (previousTick > 0) {
            headYawDelta = (Mth.wrapDegrees(yRot - lastYRot) * 20.0f) / Math.max(1, currentTick - previousTick);
        }
        lastYRot = yRot;
    }

    public static float getHeadYawDelta() {
        return headYawDelta;
    }
}
