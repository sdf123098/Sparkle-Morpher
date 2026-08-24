package com.micaftic.morpher.core.compat.api;

/**
 * R11.1/R11.2 Compat API — 兼容服务注册表。
 *
 * <p>核心模块只定义 hook（{@link MaidModelService} / {@link MaidNetworkService}）；
 * 各平台/核心实现的 adapter 在启动时注册，兼容逻辑经注册表取用。
 * 未注册时返回 no-op 默认，避免空指针与启动顺序依赖。</p>
 */
public final class CompatServices {

    private static volatile MaidModelService maidModelService = MaidModelService.NONE;

    private static volatile MaidNetworkService maidNetworkService = MaidNetworkService.NONE;

    private CompatServices() {
    }

    /** 注册服务端模型目录服务（null 忽略，保留原值）。 */
    public static void registerMaidModelService(MaidModelService service) {
        if (service != null) {
            maidModelService = service;
        }
    }

    /** 取服务端模型目录服务（未注册为 no-op）。 */
    public static MaidModelService maidModelService() {
        return maidModelService;
    }

    /** 注册服务端网络发送服务（null 忽略，保留原值）。 */
    public static void registerMaidNetworkService(MaidNetworkService service) {
        if (service != null) {
            maidNetworkService = service;
        }
    }

    /** 取服务端网络发送服务（未注册为 no-op）。 */
    public static MaidNetworkService maidNetworkService() {
        return maidNetworkService;
    }
}
