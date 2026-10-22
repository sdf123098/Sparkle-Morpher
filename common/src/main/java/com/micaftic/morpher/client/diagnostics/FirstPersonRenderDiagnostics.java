package com.micaftic.morpher.client.diagnostics;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.compat.punchy.PunchyCompat;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * [FP-COMPAT] first-person arm render diagnostics (investigation FP-COMPAT-PUNCHY-001).
 *
 * <p>Every early-return on the custom first-person arm path reports its exact reason, so the
 * "custom model active but vanilla arm visible" symptom can be attributed to a specific gate
 * instead of being guessed.
 *
 * <p>Output is off by default ({@code general.FirstPersonRenderDiagnostics}) and only prints when
 * the reported state changes, plus a slow heartbeat so a steady state is still observable after
 * re-entering a world or reloading resources.
 *
 * <p>{@code punchyRenderObserved} stays {@code unknown} on purpose: Punchy!'s own render path has
 * not been probed yet, and the plan forbids inferring it from mod presence.
 */
public final class FirstPersonRenderDiagnostics {

    public enum Outcome {
        REPLACED,
        REPLACED_GLTF,
        SKIPPED_MOD_UNAVAILABLE,
        SKIPPED_SELF_MODEL_DISABLED,
        SKIPPED_SELF_HANDS_DISABLED,
        SKIPPED_NOT_LOCAL_PLAYER,
        SKIPPED_NO_CAPABILITY,
        SKIPPED_MODEL_INACTIVE,
        SKIPPED_NO_ASSEMBLY,
        SKIPPED_NO_ANIMATION_BUNDLE,
        SKIPPED_NO_ARM_BONE,
        SKIPPED_GLTF_INVALID,
        SKIPPED_GLTF_NO_ARM_NODE,
        SKIPPED_ANIMATION_NOT_READY,
        SKIPPED_VETOED_BY_EVENT,
        /** The arm hook never ran while a custom model was active in first person. */
        HOOK_NOT_INVOKED,
        FAILED_EXCEPTION;

        boolean customGeometrySubmitted() {
            return this == REPLACED || this == REPLACED_GLTF;
        }

        boolean customModelActive() {
            return switch (this) {
                case SKIPPED_MOD_UNAVAILABLE, SKIPPED_SELF_MODEL_DISABLED, SKIPPED_SELF_HANDS_DISABLED,
                     SKIPPED_NOT_LOCAL_PLAYER, SKIPPED_NO_CAPABILITY, SKIPPED_MODEL_INACTIVE,
                     SKIPPED_NO_ASSEMBLY -> false;
                default -> true;
            };
        }
    }

    private static final long HEARTBEAT_NANOS = 5_000_000_000L;
    private static final long HOOK_SILENCE_NANOS = 1_000_000_000L;
    private static final Map<HumanoidArm, String> LAST_SIGNATURE = new EnumMap<>(HumanoidArm.class);
    private static final Map<HumanoidArm, Long> LAST_LOG_NANOS = new EnumMap<>(HumanoidArm.class);
    private static volatile long lastHookObservationNanos;

    private FirstPersonRenderDiagnostics() {
    }

    public static boolean enabled() {
        return ConfigPolicies.firstPersonRenderDiagnostics();
    }

    /**
     * Liveness probe for the arm hook itself: when a model is active in first person but the
     * render hook has not run for a second, no early-return inside SPM can explain the missing
     * arm. That is the signature of another mod (e.g. Punchy!) owning the hand render path.
     */
    public static void tick(Minecraft client) {
        if (!enabled()) {
            return;
        }
        LocalPlayer player = client == null ? null : client.player;
        if (player == null || client.isPaused() || !InputUtil.isPlayerReady()
                || !client.options.getCameraType().isFirstPerson()) {
            lastHookObservationNanos = 0L;
            return;
        }
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability == null || !capability.isModelActive()) {
            lastHookObservationNanos = 0L;
            return;
        }
        ModelAssembly assembly = capability.getModelAssembly();
        if (assembly == null) {
            lastHookObservationNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        if (lastHookObservationNanos == 0L) {
            lastHookObservationNanos = now;
            return;
        }
        if (now - lastHookObservationNanos < HOOK_SILENCE_NANOS) {
            return;
        }
        report(HumanoidArm.RIGHT, Outcome.HOOK_NOT_INVOKED, capability.getModelId(),
                assembly.isGltf() ? "gltf" : "geckolib", null);
    }

    /**
     * @param modelId       active model id, or {@code null} when no model is active
     * @param format        {@code geckolib} / {@code gltf}, or {@code null} when not resolved
     * @param armBoneExists whether the model has the required arm bone, or {@code null} when unknown
     */
    public static void report(HumanoidArm arm, Outcome outcome, String modelId, String format, Boolean armBoneExists) {
        if (!enabled() || arm == null || outcome == null) {
            return;
        }
        String signature = outcome + "|" + modelId + "|" + format + "|" + armBoneExists;
        Map<HumanoidArm, String> signatures = LAST_SIGNATURE;
        Map<HumanoidArm, Long> times = LAST_LOG_NANOS;
        long now = System.nanoTime();
        if (outcome != Outcome.HOOK_NOT_INVOKED) {
            lastHookObservationNanos = now;
        }
        synchronized (FirstPersonRenderDiagnostics.class) {
            String previous = signatures.put(arm, signature);
            Long lastAt = times.get(arm);
            if (signature.equals(previous) && lastAt != null && now - lastAt < HEARTBEAT_NANOS) {
                return;
            }
            times.put(arm, now);
        }
        String vanillaArmCancelled = outcome == Outcome.HOOK_NOT_INVOKED
                ? "unknown"
                : Boolean.toString(outcome.customGeometrySubmitted());
        YesSteveModel.LOGGER.info(
                "[FP-COMPAT] punchyDetected={} punchyVersion={} punchyModId={} customModelActive={} modelId={} format={} arm={} armBoneExists={} outcome={} customGeometrySubmitted={} vanillaArmCancelled={} punchyRenderObserved=unknown",
                PunchyCompat.isLoaded(),
                valueOrUnknown(PunchyCompat.version()),
                valueOrUnknown(PunchyCompat.modId()),
                outcome.customModelActive(),
                valueOrUnknown(modelId),
                valueOrUnknown(format),
                arm.name().toLowerCase(Locale.ROOT),
                armBoneExists == null ? "unknown" : armBoneExists.toString(),
                outcome.name(),
                outcome.customGeometrySubmitted(),
                vanillaArmCancelled);
    }

    private static String valueOrUnknown(String value) {
        return value == null ? "unknown" : value;
    }
}
