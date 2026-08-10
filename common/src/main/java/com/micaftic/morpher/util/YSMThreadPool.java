package com.micaftic.morpher.util;

import java.util.concurrent.*;

public final class YSMThreadPool {

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 30, TimeUnit.SECONDS, new LinkedBlockingQueue(), runnable -> {
        Thread thread = new Thread(runnable, "SM Worker");
        thread.setPriority(5);
        thread.setDaemon(true);
        return thread;
    });

    private static final ThreadPoolExecutor SYNC_EXECUTOR = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "SM Sync");
        thread.setPriority(7);
        thread.setDaemon(true);
        return thread;
    });

    public static Future<?> submit(Runnable runnable) {
        return EXECUTOR.submit(runnable);
    }

    public static <T> Future<T> submitCallable(Callable<T> callable) {
        return EXECUTOR.submit(callable);
    }

    public static Future<?> submitSync(Runnable runnable) {
        return SYNC_EXECUTOR.submit(runnable);
    }

    /**
     * 退避等待毫秒数。仅用于发送重试等 backoff 场景（历史实现是 Thread.sleep）。
     * 注意：这不是线程池终止等待；真正关闭请使用 ExecutorService 的 shutdown/awaitTermination。
     */
    public static boolean sleepMillis(int i) {
        try {
            Thread.sleep(i);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /** @deprecated 语义实为退避 sleep，非线程池终止等待；请改用 {@link #sleepMillis(int)}。 */
    @Deprecated
    public static boolean awaitTermination(int i) {
        return sleepMillis(i);
    }
}