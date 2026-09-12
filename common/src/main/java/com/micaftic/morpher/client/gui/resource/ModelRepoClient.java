package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.resource.station.DownloadCandidatePlanner;
import com.micaftic.morpher.client.gui.resource.station.GitHubSource;
import com.micaftic.morpher.client.gui.resource.station.HttpTransport;
import com.micaftic.morpher.client.gui.resource.station.IndexJsonSource;
import com.micaftic.morpher.client.gui.resource.station.RepositoryEntryParser;
import com.micaftic.morpher.client.gui.resource.station.RepositorySource;
import com.micaftic.morpher.util.ModelIdUtil;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Facade / router for the Resource Station repository client.
 *
 * <p>R1.2.x §24.4 split: the implementation now lives in the
 * {@code ...resource.station} subpackage (sources, transport, candidate planner,
 * entry parser). The public API below is kept intact as a compatibility facade and
 * only delegates. No legacy code path was deleted (removal is deferred to 1.2.8).
 */
public final class ModelRepoClient {
    /** Ordered routing, preserving legacy behaviour: GitHub host first, index.json terminal. */
    private static final List<RepositorySource> SOURCES = List.of(new GitHubSource(), new IndexJsonSource());

    private ModelRepoClient() {
    }

    public static List<ModelRepoEntry> list(String sourceUrl, int timeoutMs) throws Exception {
        return list(sourceUrl, new ResourceStationConfig.State(List.of(sourceUrl), sourceUrl, timeoutMs, 64 * 1024 * 1024, false, List.of()));
    }

    public static List<ModelRepoEntry> list(String sourceUrl, ResourceStationConfig.State config) throws Exception {
        URI uri = URI.create(sourceUrl.trim());
        HttpTransport.monitor(config, "List start source={} host={} timeoutMs={}", sourceUrl, uri.getHost(), config.timeoutMs());
        for (RepositorySource source : SOURCES) {
            if (source.supports(uri, sourceUrl)) {
                return source.list(sourceUrl, uri, config);
            }
        }
        throw new IllegalStateException("No repository source supports " + sourceUrl);
    }

    public static byte[] download(ModelRepoEntry entry, int timeoutMs, int maxBytes) throws IOException {
        return HttpTransport.read(entry.url(), timeoutMs, maxBytes);
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        IOException last = null;
        List<String> candidates = DownloadCandidatePlanner.rankCandidates(DownloadCandidatePlanner.downloadCandidates(entry, config), config);
        HttpTransport.monitor(config, "Download start name={} fileName={} size={} candidates={} githubFile={}", entry.name(), entry.fileName(), entry.size(), candidates.size(), entry.isGithubFile());
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            try {
                byte[] data = HttpTransport.read(url, config.timeoutMs(), config.maxDownloadBytes(), config, "download");
                HttpTransport.monitor(config, "Download complete name={} url={} bytes={}", entry.name(), url, data.length);
                return data;
            } catch (IOException | RuntimeException e) {
                last = asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource download candidate failed: {}", url, e);
                HttpTransport.monitor(config, "Download candidate failed name={} url={} error={}", entry.name(), url, e.toString());
            }
        }
        HttpTransport.monitor(config, "Download failed name={} candidates={} last={}", entry.name(), candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No download URL") : last;
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws IOException {
        IOException last = null;
        List<String> candidates = DownloadCandidatePlanner.rankCandidates(DownloadCandidatePlanner.downloadCandidates(entry, config), config);
        HttpTransport.monitor(config, "Download start name={} fileName={} size={} candidates={} githubFile={}", entry.name(), entry.fileName(), entry.size(), candidates.size(), entry.isGithubFile());
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            if (listener != null) {
                listener.onCandidate(url, i + 1, candidates.size());
            }
            try {
                byte[] data = HttpTransport.read(url, config.timeoutMs(), config.maxDownloadBytes(), listener, config, "download", entry.size());
                HttpTransport.monitor(config, "Download complete name={} url={} bytes={}", entry.name(), url, data.length);
                return data;
            } catch (IOException | RuntimeException e) {
                last = asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource download candidate failed: {}", url, e);
                HttpTransport.monitor(config, "Download candidate failed name={} url={} error={}", entry.name(), url, e.toString());
            }
        }
        HttpTransport.monitor(config, "Download failed name={} candidates={} last={}", entry.name(), candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No download URL") : last;
    }

    public static byte[] downloadPreview(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        if (entry.previewUrl() == null || entry.previewUrl().isBlank()) {
            throw new IOException("No preview URL");
        }
        HttpTransport.monitor(config, "Preview download start name={} url={}", entry.name(), entry.previewUrl());
        byte[] data = HttpTransport.read(entry.previewUrl(), config.timeoutMs(), 2 * 1024 * 1024, config, "preview");
        HttpTransport.monitor(config, "Preview download complete name={} bytes={}", entry.name(), data.length);
        return data;
    }

    public static String safeModelId(ModelRepoEntry entry) {
        String stem = RepositoryEntryParser.stripExtension(entry.fileName() == null || entry.fileName().isBlank() ? entry.name() : entry.fileName());
        String normalized = ModelIdUtil.normalizeImportModelId(stem);
        if (ModelIdUtil.isValidModelId(normalized)) {
            if (!ModelIdUtil.hasLetterOrNumber(normalized)) {
                return "repo/" + RepositoryEntryParser.sha1(entry.url()).substring(0, 12);
            }
            return normalized;
        }
        return "repo/" + RepositoryEntryParser.sha1(entry.url()).substring(0, 12);
    }

    private static IOException asIOException(Throwable throwable) {
        return throwable instanceof IOException io ? io : new IOException(throwable);
    }

    public static String formatSpeed(long bytesPerSecond) {
        return HttpTransport.formatSpeed(bytesPerSecond);
    }

    public static String hostName(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (IllegalArgumentException ignored) {
            return url;
        }
    }

    public interface ProgressListener {
        void onProgress(int downloaded, int total);

        default boolean isCancelled() {
            return false;
        }

        default void onProgress(int downloaded, int total, long bytesPerSecond) {
            onProgress(downloaded, total);
        }

        default void onCandidate(String url, int index, int total) {
        }
    }
}
