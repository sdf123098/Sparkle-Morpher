package com.micaftic.morpher.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R2.1/R2.2 统一线程池 facade：替代 YSMThreadPool、各模块 newCachedThreadPool、散落 new Thread。
 *
 * 四个池（全部 daemon + 命名 + 有界队列）：
 * <ul>
 *   <li>{@link Pool#MODEL_PARSE} — 模型解析（CPU 密集），线程数 = max(2, 核数/2)，队列 256</li>
 *   <li>{@link Pool#MODEL_IO} — 模型/资源文件 IO，线程数 2，队列 128</li>
 *   <li>{@link Pool#NETWORK_IO} — 下载/上传，线程数 2，队列 64</li>
 *   <li>{@link Pool#BACKGROUND} — 通用后台任务，线程数 2，队列 256</li>
 * </ul>
 *
 * 背压策略（R2.2）：队列满时任务退回提交线程执行（CallerRunsPolicy），不丢弃、不 OOM。
 * shutdown 后提交抛 {@link RejectedExecutionException}（由调用方按上下文处理）。
 *
 * 生命周期（R2.3）：游戏退出时调用 {@link #shutdown()}；单次任务作用域用 {@link TaskScope}。
 */
public final class SmExecutors {

    public enum Pool {
        MODEL_PARSE, MODEL_IO, NETWORK_IO, BACKGROUND
    }

    private static final int CORES = Runtime.getRuntime().availableProcessors();

    private static final ThreadPoolExecutor MODEL_PARSE = create(Pool.MODEL_PARSE,
            Math.max(2, CORES / 2), Math.max(2, CORES / 2), 256);
    private static final ThreadPoolExecutor MODEL_IO = create(Pool.MODEL_IO, 2, 4, 128);
    private static final ThreadPoolExecutor NETWORK_IO = create(Pool.NETWORK_IO, 2, 4, 64);
    private static final ThreadPoolExecutor BACKGROUND = create(Pool.BACKGROUND, 2, 2, 256);

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    private SmExecutors() {
    }

    private static ThreadPoolExecutor create(Pool pool, int core, int max, int queueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(core, max, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity), threadFactory(pool));
        // R2.2 背压：队列满时由提交线程执行任务，避免任务丢弃与无界内存增长
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    private static ThreadFactory threadFactory(Pool pool) {
        return runnable -> {
            Thread thread = new Thread(runnable, "SM-" + pool.name() + "-" + THREAD_SEQ.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(5);
            return thread;
        };
    }

    public static ExecutorService pool(Pool pool) {
        return switch (pool) {
            case MODEL_PARSE -> MODEL_PARSE;
            case MODEL_IO -> MODEL_IO;
            case NETWORK_IO -> NETWORK_IO;
            case BACKGROUND -> BACKGROUND;
        };
    }

    public static void submit(Pool pool, Runnable task) {
        pool(pool).execute(task);
    }

    /** 退出游戏时调用（R2.3）：关闭全部池，拒绝新任务，等待在跑任务结束。 */
    public static void shutdown() {
        for (ExecutorService executor : new ExecutorService[]{MODEL_PARSE, MODEL_IO, NETWORK_IO, BACKGROUND}) {
            executor.shutdown();
        }
    }

    /** 测试辅助：等待所有池终止（限时）。 */
    static boolean awaitShutdown(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        for (ExecutorService executor : new ExecutorService[]{MODEL_PARSE, MODEL_IO, NETWORK_IO, BACKGROUND}) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0 || !executor.awaitTermination(Math.max(1, remaining), TimeUnit.MILLISECONDS)) {
                return false;
            }
        }
        return true;
    }
}
