package com.micaftic.morpher.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2.1/R2.2/R2.3 线程池基建测试。
 *
 * 注意：SmExecutors 池为进程级共享单例——本测试不触发 shutdown（会毒化共享池，
 * 影响其他测试与生产路径）；shutdown 语义由游戏退出路径调用（R2.3 验收项）。
 */
class SmExecutorsTest {

    @Test
    void pools_areDaemonAndNamed() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        SmExecutors.submit(SmExecutors.Pool.BACKGROUND, () -> {
            Thread t = Thread.currentThread();
            assertTrue(t.isDaemon(), "后台线程必须是 daemon");
            assertTrue(t.getName().startsWith("SM-BACKGROUND-"), "线程命名: " + t.getName());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应在限时内执行");
    }

    @Test
    void backgroundPool_executesTask() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SmExecutors.submit(SmExecutors.Pool.BACKGROUND, latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "任务应在限时内执行");
    }

    @Test
    void taskScope_cancelsPendingTasks() throws Exception {
        try (TaskScope scope = new TaskScope()) {
            // 提交一个阻塞任务；scope 关闭后它应被取消（中断协作）
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> blocking = scope.submit(
                    SmExecutors.pool(SmExecutors.Pool.BACKGROUND), () -> {
                        started.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });
            started.await(5, TimeUnit.SECONDS);

            scope.cancel();
            assertTrue(blocking.isCancelled() || blocking.isDone(), "cancel 应取消任务");

            // 已关闭 scope 拒绝新提交
            assertThrows(RejectedExecutionException.class,
                    () -> scope.submit(SmExecutors.pool(SmExecutors.Pool.BACKGROUND), () -> null));
            assertTrue(scope.isClosed(), "scope 应标记 closed");
        }
    }

    @Test
    void taskScope_closedRejectsBeforeExecution() throws Exception {
        TaskScope scope = new TaskScope();
        scope.close();
        assertThrows(RejectedExecutionException.class,
                () -> scope.submit(SmExecutors.pool(SmExecutors.Pool.BACKGROUND), () -> null));
    }
}
