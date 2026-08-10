package com.micaftic.morpher.core.model.selection;

import com.micaftic.morpher.core.model.ModelRef;
import com.micaftic.morpher.core.model.ModelSourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 测试：优先级决策 + 五类竞态防护（审计文档 R6 验收）。
 */
class EntityModelResolverTest {

    private static final ModelRef SERVER_FORCED_REF = ModelRef.of(ModelSourceType.SERVER_FORCED, "lobby", "maid");
    private static final ModelRef LOCAL_REF = ModelRef.of(ModelSourceType.LOCAL, "cirno");
    private static final ModelRef CLOUD_REF = ModelRef.of(ModelSourceType.CLOUD, "uuid-1", "asset-1");
    private static final ModelRef LEGACY_REF = ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno");
    private static final ModelRef DEFAULT_REF = ModelRef.of(ModelSourceType.BUILTIN, "default");

    @Test
    void resolve_picksHighestPriority() {
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        // 乱序插入：legacy < cloud < server-forced（高优先级应胜出，与插入顺序无关）
        EntityModelCandidates candidates = EntityModelCandidates.builder()
                .add(LEGACY_REF, ModelPriority.LEGACY_SERVER)
                .add(CLOUD_REF, ModelPriority.CLOUD)
                .add(SERVER_FORCED_REF, ModelPriority.SERVER_FORCED)
                .build();
        assertEquals(SERVER_FORCED_REF, resolver.resolve(candidates));
    }

    @Test
    void resolve_followsDocumentedPriorityChain() {
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        // 文档 3.6：SERVER_FORCED > LOCAL_PREVIEW > CLOUD > LEGACY_SERVER > DEFAULT
        assertResolved(resolver, ModelPriority.SERVER_FORCED, SERVER_FORCED_REF);
        assertResolved(resolver, ModelPriority.LOCAL_PREVIEW, LOCAL_REF);
        assertResolved(resolver, ModelPriority.CLOUD, CLOUD_REF);
        assertResolved(resolver, ModelPriority.LEGACY_SERVER, LEGACY_REF);
        assertResolved(resolver, ModelPriority.DEFAULT, DEFAULT_REF);
    }

    private void assertResolved(EntityModelResolver resolver, ModelPriority priority, ModelRef expected) {
        EntityModelCandidates candidates = EntityModelCandidates.builder().add(expected, priority).build();
        assertEquals(expected, resolver.resolve(candidates));
    }

    @Test
    void resolve_emptyCandidates_returnsNull() {
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        assertNull(resolver.resolve(EntityModelCandidates.builder().build()));
        assertNull(resolver.resolve(null));
    }

    // ---- R6.3 revision 竞态防护（验收 5 类） ----

    @Test
    void cloudLikeDelayedCandidate_staleGenerationDiscarded() {
        // Cloud-like delayed candidate：晚到的云端候选带旧 generation → 丢弃
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        long gen1 = resolver.request();            // 云端异步请求
        long gen2 = resolver.request();            // 期间本地选择变化 → 新请求
        assertTrue(resolver.shouldApply(gen2));
        assertFalse(resolver.shouldApply(gen1), "旧 generation 必须丢弃（不得覆盖新选择）");
    }

    @Test
    void legacyPacketArrival_doesNotOverrideNewerSelection() {
        // Legacy packet arrival：旧服务器包在本地选择变化后到达 → 不覆盖
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        long packetGen = resolver.request();       // 服务器包异步 apply 请求
        resolver.request();                        // 用户本地选择了新模型
        assertFalse(resolver.shouldApply(packetGen), "过期 packet 不得覆盖本地新选择");
    }

    @Test
    void localSelectionChange_invalidatesInFlightAsyncResult() {
        // local selection change：本地选择连续变化 → 只有最后一个请求可 apply
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        long first = resolver.request();
        long second = resolver.request();
        long third = resolver.request();
        assertTrue(resolver.shouldApply(third));
        assertFalse(resolver.shouldApply(first));
        assertFalse(resolver.shouldApply(second));
    }

    @Test
    void entityDespawn_resetDiscardsInFlight() {
        // entity despawn：实体消失 → reset 作废所有在途结果
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        long gen = resolver.request();
        resolver.reset();
        assertFalse(resolver.shouldApply(gen), "实体消失后不得再 apply");
        // reset 推进 revision（generation 不复用）：当前 revision 大于旧 gen
        assertTrue(resolver.guard().current() > gen);
    }

    @Test
    void dimensionSwitch_resetInvalidatesOldGenerations() {
        // dimension switch：维度切换 → reset；新维度的请求正常可用
        EntityModelResolver resolver = new EntityModelResolver(new ModelRevisionGuard());
        long oldGen = resolver.request();
        resolver.reset();                          // 切换维度
        long newGen = resolver.request();
        assertTrue(resolver.shouldApply(newGen));
        assertFalse(resolver.shouldApply(oldGen));
    }

    @Test
    void guard_requestMonotonicIncreases() {
        ModelRevisionGuard guard = new ModelRevisionGuard();
        assertEquals(1, guard.request());
        assertEquals(2, guard.request());
        assertEquals(2, guard.current());
        assertTrue(guard.isCurrent(2));
    }
}
