package com.micaftic.morpher.audio;

import java.util.List;

/**
 * R10.3 Audio cache 驱逐策略（纯 Java，无 MC/GL 依赖）。
 *
 * <p>把 {@link AudioStreamCache#trimToBudget} 的"逐条驱逐全局最旧"改成 weighted LRU：
 * 驱逐权重 = 未使用时长 × 字节数——优先驱逐"又旧又大"的条目，每次驱逐释放更多字节，
 * 减少回 budget 所需的迭代次数，同时不误伤刚使用过的条目（未使用时长 ≈ 0 权重 ≈ 0）。
 */
public final class AudioEvictionPolicy {

    /** 驱逐候选：一个缓存条目在策略视角下的投影。 */
    public record Candidate(long lastUsedAt, long byteSize) {
    }

    private AudioEvictionPolicy() {
    }

    /**
     * 从候选列表选出驱逐目标的下标；列表为空返回 -1。
     *
     * @param now       当前时间（毫秒）
     * @param candidates 候选条目（通常为全部 provider 的全部缓存条目投影）
     */
    public static int selectVictim(long now, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return -1;
        }
        int bestIndex = -1;
        double bestWeight = -1.0d;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            long staleMillis = Math.max(0L, now - candidate.lastUsedAt());
            if (staleMillis <= 0L) {
                // 本帧刚用过：权重 0，不可驱逐
                continue;
            }
            // 权重 = 未使用时长 × 字节数：旧 + 大 → 优先驱逐
            double weight = (double) staleMillis * (double) candidate.byteSize();
            if (weight > bestWeight) {
                bestWeight = weight;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
