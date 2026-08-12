package com.micaftic.morpher.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R10.3 AudioEvictionPolicy 测试：weighted LRU 驱逐目标选择。
 */
class AudioEvictionPolicyTest {

    @Test
    void emptyList_returnsMinusOne() {
        assertEquals(-1, AudioEvictionPolicy.selectVictim(1000L, List.of()));
        assertEquals(-1, AudioEvictionPolicy.selectVictim(1000L, null));
    }

    @Test
    void freshlyUsedEntries_areNotEvictable() {
        // now=1000，两个条目都刚用过（lastUsedAt == now）→ 权重 0 → 无候选
        List<AudioEvictionPolicy.Candidate> candidates = List.of(
                new AudioEvictionPolicy.Candidate(1000L, 100_000L),
                new AudioEvictionPolicy.Candidate(1000L, 10_000L));
        assertEquals(-1, AudioEvictionPolicy.selectVictim(1000L, candidates));
    }

    @Test
    void oldestEntry_wins_whenSizesEqual() {
        // 字节相同时按未使用时长（旧 → 驱逐）
        List<AudioEvictionPolicy.Candidate> candidates = List.of(
                new AudioEvictionPolicy.Candidate(900L, 100L),   // 旧 100ms
                new AudioEvictionPolicy.Candidate(500L, 100L));  // 旧 500ms → 优先
        assertEquals(1, AudioEvictionPolicy.selectVictim(1000L, candidates));
    }

    @Test
    void largerStaleEntry_beatsSmallerOlderEntry() {
        // 权重 = 未使用时长 × 字节数：
        // A: 400ms × 1000B = 400k
        // B: 900ms × 100B  = 90k
        // A 虽更新但大得多 → 优先驱逐（每次释放更多字节）
        List<AudioEvictionPolicy.Candidate> candidates = List.of(
                new AudioEvictionPolicy.Candidate(600L, 1000L),
                new AudioEvictionPolicy.Candidate(100L, 100L));
        assertEquals(0, AudioEvictionPolicy.selectVictim(1000L, candidates));
    }

    @Test
    void ties_resolveToFirstMax() {
        List<AudioEvictionPolicy.Candidate> candidates = List.of(
                new AudioEvictionPolicy.Candidate(500L, 100L),  // 500ms×100 = 50k
                new AudioEvictionPolicy.Candidate(500L, 100L)); // 同权重 → 取第一个
        assertEquals(0, AudioEvictionPolicy.selectVictim(1000L, candidates));
    }

    @Test
    void futureTimestamps_clampToZeroWeight() {
        // lastUsedAt 在未来（时钟回拨）→ stale 钳 0 → 不可驱逐
        List<AudioEvictionPolicy.Candidate> candidates = List.of(
                new AudioEvictionPolicy.Candidate(1500L, 100_000L));
        assertEquals(-1, AudioEvictionPolicy.selectVictim(1000L, candidates));
    }
}
