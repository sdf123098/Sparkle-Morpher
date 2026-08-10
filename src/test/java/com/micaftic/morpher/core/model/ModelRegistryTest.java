package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5.3 ModelRegistry 测试：register/replace 不泄漏 / lease 防 evict / lazy evict / 快照不可变。
 */
class ModelRegistryTest {

    /** 假 runtime：close 计数（验证 replace/evict 释放）。 */
    private static final class FakeRuntime implements ModelRuntime {
        private final ModelRef ref;
        final AtomicInteger closeCount = new AtomicInteger();
        volatile boolean loaded = true;

        FakeRuntime(ModelRef ref) {
            this.ref = ref;
        }

        @Override
        public ModelRef ref() {
            return ref;
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            loaded = false;
        }
    }

    private static final ModelRef LOCAL_CIRNO = ModelRef.of(ModelSourceType.LOCAL, "cirno");

    @Test
    void register_lookup_roundTrip() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime runtime = new FakeRuntime(LOCAL_CIRNO);
        assertNull(registry.register(LOCAL_CIRNO, runtime), "首次注册返回 null");
        assertEquals(runtime, registry.lookup(LOCAL_CIRNO));
        assertEquals(1, registry.size());
        assertTrue(registry.refs().contains(LOCAL_CIRNO));
    }

    @Test
    void register_duplicateRef_returnsOldWithoutReplacing() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime first = new FakeRuntime(LOCAL_CIRNO);
        FakeRuntime second = new FakeRuntime(LOCAL_CIRNO);
        assertNull(registry.register(LOCAL_CIRNO, first));
        assertEquals(first, registry.register(LOCAL_CIRNO, second), "register 不静默替换");
        assertEquals(first, registry.lookup(LOCAL_CIRNO));
        assertEquals(0, first.closeCount.get(), "旧 runtime 未被 close");
        assertEquals(0, second.closeCount.get(), "新 runtime 未注册，由调用方决定释放");
    }

    @Test
    void replace_closesOldRuntime_noLeak() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime old = new FakeRuntime(LOCAL_CIRNO);
        FakeRuntime fresh = new FakeRuntime(LOCAL_CIRNO);
        registry.register(LOCAL_CIRNO, old);
        assertEquals(old, registry.replace(LOCAL_CIRNO, fresh), "返回被替换的旧 runtime");
        assertEquals(1, old.closeCount.get(), "replace 必须释放旧 runtime（replace 不泄漏验收）");
        assertEquals(0, fresh.closeCount.get());
        assertEquals(fresh, registry.lookup(LOCAL_CIRNO));
    }

    @Test
    void evict_noLease_releasesAndRemoves() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime runtime = new FakeRuntime(LOCAL_CIRNO);
        registry.register(LOCAL_CIRNO, runtime);
        assertTrue(registry.evict(LOCAL_CIRNO));
        assertEquals(1, runtime.closeCount.get(), "evict 释放 runtime");
        assertNull(registry.lookup(LOCAL_CIRNO));
        assertEquals(0, registry.size());
        assertFalse(registry.evict(LOCAL_CIRNO), "已移除再 evict 返回 false");
    }

    @Test
    void lease_preventsEvict_untilClosed() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime runtime = new FakeRuntime(LOCAL_CIRNO);
        registry.register(LOCAL_CIRNO, runtime);

        ModelLease lease = registry.lease(LOCAL_CIRNO);
        assertNotNull(lease);
        assertFalse(registry.evict(LOCAL_CIRNO), "lease 持有期间 evict 不得释放");
        assertEquals(0, runtime.closeCount.get());
        assertEquals(runtime, registry.lookup(LOCAL_CIRNO), "runtime 仍可用");

        lease.close();
        assertTrue(lease.isClosed());
        assertTrue(registry.evict(LOCAL_CIRNO), "lease 归还后可 evict");
        assertEquals(1, runtime.closeCount.get());
    }

    @Test
    void lease_unknownRef_returnsNull() {
        ModelRegistry registry = new ModelRegistry();
        assertNull(registry.lease(LOCAL_CIRNO));
    }

    @Test
    void lease_close_isIdempotent() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime runtime = new FakeRuntime(LOCAL_CIRNO);
        registry.register(LOCAL_CIRNO, runtime);
        ModelLease lease = registry.lease(LOCAL_CIRNO);
        lease.close();
        lease.close();
        // 幂等：多次 close 后 evict 仍可执行一次（计数只归还一次）
        assertTrue(registry.evict(LOCAL_CIRNO));
        assertEquals(1, runtime.closeCount.get());
    }

    @Test
    void replace_withActiveLease_closesOld() {
        // replace 是显式更新（模型文件刷新），不受 lease 保护——旧 runtime 必须释放
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime old = new FakeRuntime(LOCAL_CIRNO);
        registry.register(LOCAL_CIRNO, old);
        try (ModelLease ignored = registry.lease(LOCAL_CIRNO)) {
            FakeRuntime fresh = new FakeRuntime(LOCAL_CIRNO);
            registry.replace(LOCAL_CIRNO, fresh);
            assertEquals(1, old.closeCount.get(), "replace 不泄漏：旧 runtime 释放");
            assertEquals(fresh, registry.lookup(LOCAL_CIRNO));
        }
    }

    @Test
    void snapshot_isUnmodifiable() {
        ModelRegistry registry = new ModelRegistry();
        registry.register(LOCAL_CIRNO, new FakeRuntime(LOCAL_CIRNO));
        Map<ModelRef, ModelRuntime> snapshot = registry.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(LOCAL_CIRNO, null));
        assertThrows(UnsupportedOperationException.class, () -> registry.refs().add(null));
        assertEquals(1, snapshot.size());
    }

    @Test
    void closeAll_closesEverything() {
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime a = new FakeRuntime(ModelRef.of(ModelSourceType.LOCAL, "a"));
        FakeRuntime b = new FakeRuntime(ModelRef.of(ModelSourceType.LOCAL, "b"));
        registry.register(ModelRef.of(ModelSourceType.LOCAL, "a"), a);
        registry.register(ModelRef.of(ModelSourceType.LOCAL, "b"), b);
        registry.closeAll();
        assertEquals(1, a.closeCount.get());
        assertEquals(1, b.closeCount.get());
        assertEquals(0, registry.size());
        assertNull(registry.lookup(ModelRef.of(ModelSourceType.LOCAL, "a")));
    }

    @Test
    void registry_distinguishesSameIdDifferentSources() {
        // 同一 id 不同来源是不同模型（local:cirno vs server:cirno）
        ModelRegistry registry = new ModelRegistry();
        FakeRuntime local = new FakeRuntime(ModelRef.of(ModelSourceType.LOCAL, "cirno"));
        FakeRuntime server = new FakeRuntime(ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno"));
        registry.register(ModelRef.of(ModelSourceType.LOCAL, "cirno"), local);
        registry.register(ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno"), server);
        assertEquals(2, registry.size());
        assertEquals(local, registry.lookup(ModelRef.of(ModelSourceType.LOCAL, "cirno")));
        assertEquals(server, registry.lookup(ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno")));
    }
}
