package com.micaftic.morpher.util;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * R2.3 任务作用域：把一组后台任务绑定到一个生命周期（game session / server connection /
 * world / model load / download job），scope 关闭时取消所有未完成任务。
 *
 * 用法：
 * <pre>
 * try (TaskScope scope = new TaskScope()) {
 *     scope.submit(SmExecutors.pool(Pool.NETWORK_IO), () -> download());
 * }
 * // close()：取消仍未完成的任务（R2.3：断服/换世界时调用对应 scope 的 cancel）
 * </pre>
 *
 * 取消语义：只取消未开始/排队任务与响应中断的在跑任务（Future.cancel(true) 的中断协作）；
 * 不等待已完成任务。已关闭的 scope 拒绝新任务（抛 {@link RejectedExecutionException}）。
 */
public final class TaskScope implements AutoCloseable {

    private final Set<Future<?>> tasks = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public boolean isClosed() {
        return closed;
    }

    public <T> Future<T> submit(ExecutorService executor, Callable<T> task) {
        if (closed) {
            throw new RejectedExecutionException("TaskScope already closed");
        }
        Future<T> future = executor.submit(() -> {
            if (closed) {
                throw new RejectedExecutionException("TaskScope closed before execution");
            }
            return task.call();
        });
        tasks.add(future);
        return future;
    }

    public Future<?> submit(ExecutorService executor, Runnable task) {
        return submit(executor, () -> {
            task.run();
            return null;
        });
    }

    /** 取消所有未完成任务（幂等）。 */
    public void cancel() {
        closed = true;
        for (Future<?> future : tasks) {
            future.cancel(true);
        }
        tasks.clear();
    }

    @Override
    public void close() {
        cancel();
    }
}
