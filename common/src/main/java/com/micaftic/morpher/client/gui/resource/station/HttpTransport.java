package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;

/**
 * HTTP transport layer (R1.2.x §24.4 Resource Station split).
 *
 * <p>Verbatim move of the {@code ModelRepoClient.read(...)} family plus the timing /
 * throughput helpers, the shared constants and the {@code monitor} logging helper.
 * Behaviour is identical to the legacy in-class implementation.
 */
public final class HttpTransport {
    public static final String USER_AGENT = "OpenYSM-ResourceStation";
    public static final String GITHUB_ACCEPT = "application/vnd.github+json";
    public static final String GITHUB_API_VERSION = "2022-11-28";
    public static final String CACHE_BUSTER_PARAM = "ysm_refresh";
    public static final int LOW_SPEED_BYTES_PER_SECOND = 8 * 1024;
    public static final int LOW_SPEED_GRACE_MS = 7000;
    public static final int LOW_SPEED_WINDOW_MS = 5000;

    private HttpTransport() {
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes) throws IOException {
        return read(url, timeoutMs, maxBytes, null, null, "read");
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, null, "read");
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ResourceStationConfig.State config, String operation) throws IOException {
        return read(url, timeoutMs, maxBytes, null, config, operation);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, ResourceStationConfig.State config, String operation) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, config, operation, -1L);
    }

    public static byte[] read(String url, int timeoutMs, int maxBytes, ModelRepoClient.ProgressListener listener, ResourceStationConfig.State config, String operation, long expectedBytes) throws IOException {
        long start = System.nanoTime();
        boolean bypassCache = shouldBypassCache(operation);
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
            monitor(config, "HTTP start op={} url={} timeoutMs={} maxBytes={}", operation, url, timeoutMs, maxBytes);
            int code = connection.getResponseCode();
            int length = connection.getContentLength();
            monitor(config, "HTTP response op={} url={} code={} contentLength={} contentType={}", operation, url, code, length, connection.getContentType());
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
                monitor(config, "HTTP complete op={} url={} bytes={} elapsedMs={}", operation, url, data.length, elapsedMs(start));
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

    public static boolean shouldBypassCache(String operation) {
        return "index".equals(operation)
                || "preview".equals(operation)
                || "github-json".equals(operation)
                || "github-read".equals(operation)
                || "github-archive".equals(operation)
                || operation != null && operation.startsWith("jsdelivr");
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
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public static long elapsedMs(long startNanos, long nowNanos) {
        return (nowNanos - startNanos) / 1_000_000L;
    }

    public static long bytesPerSecond(int bytes, long startNanos, long nowNanos) {
        long elapsedNanos = Math.max(1L, nowNanos - startNanos);
        return Math.max(0L, bytes * 1_000_000_000L / elapsedNanos);
    }

    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        }
        if (bytesPerSecond < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB/s", bytesPerSecond / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

    public static void monitor(ResourceStationConfig.State config, String message, Object... args) {
        if (ResourceStationConfig.monitorLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM-RESOURCE] " + message, args);
        }
    }
}
