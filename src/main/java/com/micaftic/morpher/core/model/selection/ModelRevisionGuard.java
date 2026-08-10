package com.micaftic.morpher.core.model.selection;

import java.util.concurrent.atomic.AtomicLong;

/**
 * R6.3 ModelRevisionGuard — 异步模型结果竞态防护（revision/generation 机制）。
 *
 * <p>审计文档 3.6/R6.3：异步 resolver 每次请求带 revision N，结果回来时
 * {@code N != current → discard}——杜绝"谁异步完成得晚，谁覆盖 Entity"。
 * Cloud-like delayed candidate、Legacy packet arrival、local selection change、
 * entity despawn、dimension switch 五类竞态都不允许错误覆盖。</p>
 *
 * <pre>
 *   long gen = guard.request();          // 发起异步请求，取得本次 generation
 *   ... 异步返回 ...
 *   if (guard.isCurrent(gen)) apply();   // 结果未过期才 apply
 *   guard.reset();                       // 实体消失 / 维度切换 / 会话结束
 * </pre>
 *
 * <p>线程安全：AtomicLong 无锁递增。</p>
 */
public final class ModelRevisionGuard {

    private final AtomicLong current = new AtomicLong();

    /** 当前 revision（未发起过请求时为 0）。 */
    public long current() {
        return current.get();
    }

    /**
     * 发起一次异步模型请求：推进 revision 并返回本次请求的 generation。
     * 返回的 generation 只有在该请求发起后没有新请求/重置时才等于 current。
     */
    public long request() {
        return current.incrementAndGet();
    }

    /** 结果是否仍有效：generation 等于当前 revision 才可 apply。 */
    public boolean isCurrent(long generation) {
        return generation == current.get();
    }

    /**
     * 重置（实体消失 / 维度切换 / 世界卸载）：作废所有在途结果。
     * 实现为推进 revision 而非归零——保证 generation 单调不复用，
     * 避免旧 generation 与新 generation 数值相同导致误判有效。
     */
    public void reset() {
        current.incrementAndGet();
    }
}
