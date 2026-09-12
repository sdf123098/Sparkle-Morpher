package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

/**
 * R1.2.x §24.4 HttpTransport（neo26.x 适配版，行为等价搬运）。
 *
 * <p>本分支的 HTTP 层使用 {@code boolean bypassCache} 参数模型（而非 fa 分支的
 * {@code String operation} + {@code shouldBypassCache}），并带 {@code REQUEST_IDS}
 * 请求编号与 {@code debugLogEnabled()} 调试日志。此处按本分支实际实现搬运，
 * 未引入 fa 分支的参数模型。
 */
public final class HttpTransport {
    public static final String USER_AGENT = "OpenYSM-ResourceStation";
    public static final String GITHUB_ACCEPT = "application/vnd.github+json";
    public static final String GITHUB_API_VERSION = "2022-11-28";
    public static final String CACHE_BUSTER_PARAM = "ysm_refresh";
    private static final java.util.concurrent.atomic.AtomicLong REQUEST_IDS = new java.util.concurrent.atomic.AtomicLong();
    private static final int LOW_SPEED_BYTES_PER_SECOND = 8 * 1024;
    private static final int LOW_SPEED_GRACE_MS = 7000;
    private static final int LOW_SPEED_WINDOW_MS = 5000;

    private HttpTransport() {
    }

    /** 与拆分前 ModelRepoClient.debugLogEnabled() 同一实现（GeneralConfig 开关）。 */
    public static boolean debugLogEnabled() {
        return com.micaftic.morpher.config.GeneralConfig.safeGet(
                com.micaftic.morpher.config.GeneralConfig.RESOURCE_STATION_MONITOR_LOG, false);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes) throws IOException {
        return read(url, timeoutMs, maxBytes, null, -1L, false);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, -1L, false);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, boolean bypassCache) throws IOException {
        return read(url, timeoutMs, maxBytes, null, -1L, bypassCache);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, boolean bypassCache) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, -1L, bypassCache);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, long expectedBytes) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, expectedBytes, false);
    }

    public static byte[] readFirst(List<String> candidates, int timeoutMs, int maxBytes) throws IOException {
        return readFirst(candidates, timeoutMs, maxBytes, null, false);
    }

    public static byte[] readFirst(List<String> candidates, int timeoutMs, int maxBytes, boolean bypassCache) throws IOException {
        return readFirst(candidates, timeoutMs, maxBytes, null, bypassCache);
    }

    public static byte[] readFirst(List<String> candidates, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener) throws IOException {
        return readFirst(candidates, timeoutMs, maxBytes, listener, false);
    }

    public static byte[] readFirst(List<String> candidates, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, boolean bypassCache) throws IOException {
        IOException last = null;
        for (String candidate : candidates) {
            try {
                return read(candidate, timeoutMs, maxBytes, listener, bypassCache);
            } catch (IOException | RuntimeException e) {
                last = asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource candidate failed: {}", candidate, e);
            }
        }
        throw last == null ? new IOException("No resource URL") : last;
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, long expectedBytes, boolean bypassCache) throws IOException {
        long requestId = REQUEST_IDS.incrementAndGet();
        long start = System.nanoTime();
        String requestUrl = bypassCache ? withCacheBuster(url) : url;
        HttpURLConnection connection = (HttpURLConnection) URI.create(requestUrl).toURL().openConnection();
        try {
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (bypassCache) {
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Expires", "0");
            }
            if (url.contains("api.github.com")) {
                connection.setRequestProperty("Accept", GITHUB_ACCEPT);
                connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION);
            }
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            int length = connection.getContentLength();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            if (length > maxBytes) {
                throw new IOException("File exceeds limit");
            }
            int progressTotal = progressTotal(length, expectedBytes, maxBytes);
            checkCancelled(listener);
            if (listener != null) {
                listener.onProgress(0, progressTotal);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                long speedWindowStarted = System.nanoTime();
                int speedWindowBytes = 0;
                while ((read = in.read(buffer)) >= 0) {
                    checkCancelled(listener);
                    if (read == 0) {
                        continue;
                    }
                    long now = System.nanoTime();
                    total += read;
                    speedWindowBytes += read;
                    if (total > maxBytes) {
                        throw new IOException("File exceeds limit");
                    }
                    out.write(buffer, 0, read);
                    if (listener != null) {
                        listener.onProgress(total, progressTotal, bytesPerSecond(total, start, now));
                    }
                    if (progressTotal > 0 && total >= progressTotal) {
                        break;
                    }
                    long elapsedMs = elapsedMs(start, now);
                    long windowMs = elapsedMs(speedWindowStarted, now);
                    if (elapsedMs >= LOW_SPEED_GRACE_MS && windowMs >= LOW_SPEED_WINDOW_MS) {
                        long windowSpeed = bytesPerSecond(speedWindowBytes, speedWindowStarted, now);
                        if (windowSpeed > 0 && windowSpeed < LOW_SPEED_BYTES_PER_SECOND) {
                            throw new IOException("Download too slow: " + formatSpeed(windowSpeed));
                        }
                        speedWindowStarted = now;
                        speedWindowBytes = 0;
                    }
                }
                byte[] data = out.toByteArray();
                checkCancelled(listener);
                if (listener != null && progressTotal > 0) {
                    listener.onProgress(data.length, progressTotal, bytesPerSecond(data.length, start, System.nanoTime()));
                }
                return data;
            }
        } finally {
            connection.disconnect();
        }
    }

    public static int progressTotal(int contentLength, long expectedBytes, int maxBytes) {
        if (contentLength > 0) {
            return contentLength;
        }
        if (expectedBytes > 0 && expectedBytes <= maxBytes && expectedBytes <= Integer.MAX_VALUE) {
            return (int) expectedBytes;
        }
        return 0;
    }

    public static void checkCancelled(ModelRepoClient.ProgressListener listener) {
        if (listener != null && listener.isCancelled()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    public static IOException asIOException(Throwable throwable) {
        return throwable instanceof IOException io ? io : new IOException(throwable);
    }

    public static String withCacheBuster(String url) {
        String base = url;
        String fragment = "";
        int fragmentIndex = base.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = base.substring(fragmentIndex);
            base = base.substring(0, fragmentIndex);
        }
        char separator = base.contains("?") ? '&' : '?';
        return base + separator + CACHE_BUSTER_PARAM + '=' + System.currentTimeMillis() + fragment;
    }

    public static long elapsedMs(long startNanos) {
        return elapsedMs(startNanos, System.nanoTime());
    }

    public static long elapsedMs(long startNanos, long nowNanos) {
        return (nowNanos - startNanos) / 1_000_000L;
    }

    public static long bytesPerSecond(int bytes, long startNanos, long nowNanos) {
        long elapsed = Math.max(1L, nowNanos - startNanos);
        return bytes * 1_000_000_000L / elapsed;
    }

    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        }
        if (bytesPerSecond < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB/s", bytesPerSecond / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

}
