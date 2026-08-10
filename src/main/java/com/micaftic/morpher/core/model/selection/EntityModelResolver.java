package com.micaftic.morpher.core.model.selection;

import com.micaftic.morpher.core.model.ModelRef;

import java.util.List;

/**
 * R6.2 EntityModelResolver — 候选模型决策器。
 *
 * <p>审计文档 3.6/R6.2：把 packet direct apply、PrivacyMode override、localOnly rule、
 * server fallback 的"谁覆盖谁"决策集中到这里，替代散落在调用点的"谁异步完成得晚，
 * 谁覆盖 Entity"。</p>
 *
 * <p>纯决策逻辑（无 MC 依赖）：输入 {@link EntityModelCandidates}，按
 * {@link ModelPriority} 从高到低选出第一个候选；配合 {@link ModelRevisionGuard}
 * 校验异步结果是否过期（R6.3）。</p>
 */
public final class EntityModelResolver {

    private final ModelRevisionGuard guard;

    public EntityModelResolver(ModelRevisionGuard guard) {
        this.guard = guard;
    }

    public ModelRevisionGuard guard() {
        return guard;
    }

    /**
     * 从候选集合中按优先级选出最终模型（最高优先级第一个出现者）。
     *
     * @return 选中的 ModelRef；无候选返回 null（由调用方回退 DEFAULT）
     */
    public ModelRef resolve(EntityModelCandidates candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<EntityModelCandidates.Candidate> list = candidates.candidates();
        ModelRef best = null;
        int bestRank = Integer.MAX_VALUE;
        for (EntityModelCandidates.Candidate candidate : list) {
            if (candidate.priority().rank() < bestRank) {
                bestRank = candidate.priority().rank();
                best = candidate.ref();
            }
        }
        return best;
    }

    /**
     * 异步结果是否仍可 apply（R6.3 revision 检查）。
     *
     * @param generation 发起异步请求时取得的 generation（见 {@link ModelRevisionGuard#request()}
     * @return true = 结果未过期，可安全 apply；false = 期间有更新选择/重置，必须丢弃
     */
    public boolean shouldApply(long generation) {
        return guard.isCurrent(generation);
    }

    /** 发起一次异步模型请求（推进 revision），返回本次 generation。 */
    public long request() {
        return guard.request();
    }

    /** 实体消失 / 维度切换 / 世界卸载：作废所有在途结果。 */
    public void reset() {
        guard.reset();
    }
}
