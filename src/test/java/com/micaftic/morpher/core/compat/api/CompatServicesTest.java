package com.micaftic.morpher.core.compat.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * R11.1/R11.2 CompatServices 测试：默认 no-op / 注册取回 / null 忽略。
 *
 * <p>纯 Java（不引用 MC 类型）——接口中含 MC 类型参数的方法（isAuthorized/网络发送等）
 * 在 neo 剥离 MC 的 test 运行时无法调用，故只验证 String-only 方法 + 注册表语义。</p>
 */
class CompatServicesTest {

    @Test
    void defaultModelService_isNoOp() {
        assertFalse(CompatServices.maidModelService().containsModel("any"));
    }

    @Test
    void registerAndGet_roundTrip() {
        CompatServices.registerMaidModelService(MaidModelService.NONE);
        assertSame(MaidModelService.NONE, CompatServices.maidModelService());

        CompatServices.registerMaidNetworkService(MaidNetworkService.NONE);
        assertSame(MaidNetworkService.NONE, CompatServices.maidNetworkService());
    }

    @Test
    void registerNull_keepsPrevious() {
        CompatServices.registerMaidModelService(MaidModelService.NONE);
        CompatServices.registerMaidModelService(null);
        assertSame(MaidModelService.NONE, CompatServices.maidModelService());

        CompatServices.registerMaidNetworkService(MaidNetworkService.NONE);
        CompatServices.registerMaidNetworkService(null);
        assertSame(MaidNetworkService.NONE, CompatServices.maidNetworkService());
    }
}
