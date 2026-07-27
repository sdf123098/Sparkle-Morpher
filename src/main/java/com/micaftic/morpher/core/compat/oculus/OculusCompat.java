package com.micaftic.morpher.core.compat.oculus;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class OculusCompat {
    private static final boolean SHADER_MOD_LOADED = isModLoaded("iris") || isModLoaded("oculus");
    private static final IrisApiBridge IRIS_API = new IrisApiBridge();

    private OculusCompat() {
    }

    public static boolean isLoaded() {
        return SHADER_MOD_LOADED;
    }

    public static boolean isPBRActive() {
        return isRenderingShadowPass();
    }

    public static void updatePBRState() {
    }

    public static boolean isShaderPackInUse() {
        return SHADER_MOD_LOADED && IRIS_API.isShaderPackInUse();
    }

    public static boolean isRenderingShadowPass() {
        return SHADER_MOD_LOADED && IRIS_API.isRenderingShadowPass();
    }

    private static boolean isModLoaded(String modId) {
        try {
            return ModList.get().getModContainerById(modId).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class IrisApiBridge {
        private final Object instance;
        private final Method isShaderPackInUse;
        private final Method isRenderingShadowPass;

        private IrisApiBridge() {
            Object api = null;
            Method shaderPackMethod = null;
            Method shadowPassMethod = null;
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                shaderPackMethod = apiClass.getMethod("isShaderPackInUse");
                shadowPassMethod = apiClass.getMethod("isRenderingShadowPass");
            } catch (Throwable ignored) {
            }
            this.instance = api;
            this.isShaderPackInUse = shaderPackMethod;
            this.isRenderingShadowPass = shadowPassMethod;
        }

        private boolean isShaderPackInUse() {
            return invokeBoolean(this.isShaderPackInUse);
        }

        private boolean isRenderingShadowPass() {
            return invokeBoolean(this.isRenderingShadowPass);
        }

        private boolean invokeBoolean(Method method) {
            if (this.instance == null || method == null) {
                return false;
            }
            try {
                Object result = method.invoke(this.instance);
                return result instanceof Boolean value && value;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
