package net.irisshaders.iris.api.v0;

public interface IrisApi {
    static IrisApi getInstance() { return null; }
    boolean isShaderPackInUse();
    boolean isRenderingShadowPass();
}
