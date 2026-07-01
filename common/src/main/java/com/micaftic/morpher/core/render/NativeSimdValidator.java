package com.micaftic.morpher.core.render;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 2 validation diagnostics for the Native SIMD rollout
 * (NATIVE_SIMD_26X_AGGRESSIVE_ROLLOUT_PLAN Phase 2).
 *
 * Compares Java-reference model state against the expectations the corrected
 * native renderer is built to match, and emits compact one-line diagnostics.
 *
 * The direct Native SIMD vertex path does not expose per-bone state back to Java,
 * so this validator performs Java-side checks: input well-formedness (short
 * boneParams, broken parent chains), the hidden-rule agreement surface (bones
 * where the legacy any-zero rule and the corrected all-zero rule disagree, i.e.
 * the Phase 1.1 fix surface), and a visible/part-mask summary. It logs the
 * bug-hunt context line when anything notable is found.
 *
 * STRICT_FALLBACK flips a session kill-switch that {@link RenderBackendDecision}
 * honors; CRASH_TEST throws so CI/manual runs fail loudly on real divergence
 * risk (malformed inputs). The any-zero-only surface is logged as info only,
 * because it is the intended Phase 1.1 behavior change, not a bug.
 */
public final class NativeSimdValidator {
    private NativeSimdValidator() {
    }

    private static final AtomicBoolean SESSION_FORCE_JAVA = new AtomicBoolean(false);

    public static boolean shouldForceJavaForSession() {
        return SESSION_FORCE_JAVA.get();
    }

    public static void forceJavaForSession() {
        SESSION_FORCE_JAVA.set(true);
    }

    /** Reset the session kill-switch (e.g. on resource reload or mode change). */
    public static void resetSession() {
        SESSION_FORCE_JAVA.set(false);
    }

    public static GeneralConfig.NativeSimdValidationMode mode() {
        return GeneralConfig.safeGet(GeneralConfig.NATIVE_SIMD_VALIDATION_MODE, GeneralConfig.NativeSimdValidationMode.OFF);
    }

    /**
     * Called from the direct Native SIMD path before rendering. Runs Java-side
     * validation per the configured mode and records the result.
     */
    public static void onNativeSimdRender(GeoModel model, float[] boneParams, int renderPartMask, Object textureLocation) {
        GeneralConfig.NativeSimdValidationMode m = mode();
        if (m == GeneralConfig.NativeSimdValidationMode.OFF) return;
        if (model == null || model.bakedBones == null || model.bakedBones.isEmpty()) return;

        int boneCount = model.bakedBones.size();
        int firstAnyZeroOnlyBone = -1;  // hidden by legacy any-zero but NOT all-zero (Phase 1.1 fix surface)
        int firstShortParamsBone = -1;  // bone whose boneParams slice is short (divergence risk)
        int firstBadParentBone = -1;    // bone with invalid parent index (divergence risk)
        int hiddenAllZero = 0;
        int hiddenAnyZeroOnly = 0;
        int partMaskSelected = 0;

        for (int i = 0; i < boneCount; i++) {
            GeoModel.BakedBone bone = model.bakedBones.get(i);
            int pOffset = i * 12;
            if (boneParams == null || pOffset + 11 >= boneParams.length) {
                if (firstShortParamsBone < 0) firstShortParamsBone = i;
                continue;
            }
            if (bone.parentIdx < -1 || bone.parentIdx >= boneCount || bone.parentIdx == i) {
                if (firstBadParentBone < 0) firstBadParentBone = i;
            }
            float sx = boneParams[pOffset + 6];
            float sy = boneParams[pOffset + 7];
            float sz = boneParams[pOffset + 8];
            boolean allZero = (sx == 0.0f && sy == 0.0f && sz == 0.0f);
            boolean anyZero = (sx == 0.0f || sy == 0.0f || sz == 0.0f);
            if (allZero) {
                hiddenAllZero++;
            } else if (anyZero) {
                hiddenAnyZeroOnly++;
                if (firstAnyZeroOnlyBone < 0) firstAnyZeroOnlyBone = i;
            }
            if (renderPartMask == 0 || bone.partMask == renderPartMask || bone.partMask == 3) {
                partMaskSelected++;
            }
        }

        // Real divergence risk: malformed inputs that could make native read out of
        // bounds or traverse a broken parent chain differently than Java.
        boolean mismatch = firstShortParamsBone >= 0 || firstBadParentBone >= 0;

        if (mismatch || firstAnyZeroOnlyBone >= 0) {
            YesSteveModel.LOGGER.info("[SM-VALIDATE] model={} tex={} partMask={} bones={} partMaskSelected={} hiddenAllZero={} hiddenAnyZeroOnly={} firstAnyZeroOnlyBone={} firstShortParamsBone={} firstBadParentBone={} nativeLoaded={} nativeReason={}",
                    identity(model), textureLocation, renderPartMask, boneCount, partMaskSelected, hiddenAllZero, hiddenAnyZeroOnly, firstAnyZeroOnlyBone, firstShortParamsBone, firstBadParentBone,
                    AccelerationCapability.isLoaded(), AccelerationCapability.getReason());
        }

        if (mismatch) {
            if (m == GeneralConfig.NativeSimdValidationMode.STRICT_FALLBACK) {
                forceJavaForSession();
                YesSteveModel.LOGGER.warn("[SM-VALIDATE] STRICT_FALLBACK: forcing Java for session (shortParamsBone={} badParentBone={})", firstShortParamsBone, firstBadParentBone);
            } else if (m == GeneralConfig.NativeSimdValidationMode.CRASH_TEST) {
                throw new IllegalStateException("[SM-VALIDATE] CRASH_TEST: malformed bone params (shortParamsBone=" + firstShortParamsBone + " badParentBone=" + firstBadParentBone + ")");
            }
        }
    }

    private static String identity(GeoModel model) {
        try {
            return String.valueOf(model.getProperties());
        } catch (Throwable t) {
            return "?";
        }
    }
}
