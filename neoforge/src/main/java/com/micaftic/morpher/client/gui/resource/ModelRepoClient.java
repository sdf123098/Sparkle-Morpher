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
import java.util.concurrent.CancellationException;
import java.net.URI;
import java.util.List;

/**
 * Resource Station 仓库客户端 facade / 路由。
 *
 * <p>R1.2.x §24.4 拆分（neo26.x 适配版）：实现已迁到
 * {@code ...resource.station} 子包（sources / transport / candidate planner / entry parser）。
 * 下面的 public API 原样保留为兼容 facade，仅做委派；旧代码路径未删除
 * （删除留到 1.2.8）。
 *
 * <p>本分支与 fa 分支的实现差异（readFirst 多候选回退、indexCandidates、
 * GitHub 加速候选、debugLogEnabled 调试日志）在 station 组件内**原样保留**，
 * 未向 fa 分支的参数模型对齐。
 */
public final class ModelRepoClient {
    /** 保序路由，维持既有语义：GitHub 仓库 host 优先，index.json 兜底。 */
    private static final List<RepositorySource> SOURCES = List.of(new GitHubSource(), new IndexJsonSource());

    private ModelRepoClient() {
    }

    public static List<ModelRepoEntry> list(String sourceUrl, int timeoutMs) throws Exception {
        return list(sourceUrl, new ResourceStationConfig.State(List.of(sourceUrl), sourceUrl, timeoutMs, 64 * 1024 * 1024, false, List.of()));
    }

    public static List<ModelRepoEntry> list(String sourceUrl, ResourceStationConfig.State config) throws Exception {
        long started = System.nanoTime();
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] Listing source started sourceUrl={} timeoutMs={} maxDownloadBytes={} mainlandChinaMode={} githubAccelerators={}",
                    sourceUrl, config.timeoutMs(), config.maxDownloadBytes(), config.mainlandChinaMode(), config.githubAccelerators());
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            List<ModelRepoEntry> result = null;
            for (RepositorySource source : SOURCES) {
                if (source.supports(uri, sourceUrl)) {
                    result = source.list(sourceUrl, uri, config);
                    break;
                }
            }
            if (result == null) {
                throw new IllegalStateException("No repository source supports " + sourceUrl);
            }
            if (HttpTransport.debugLogEnabled()) {
                YesSteveModel.LOGGER.info("[SM][ResourceStation] Listing source finished sourceUrl={} entries={} elapsedMs={}",
                        sourceUrl, result.size(), HttpTransport.elapsedMs(started));
            }
            return result;
        } catch (Exception e) {
            if (HttpTransport.debugLogEnabled()) {
                YesSteveModel.LOGGER.warn("[SM][ResourceStation] Listing source failed sourceUrl={} elapsedMs={}", sourceUrl, HttpTransport.elapsedMs(started), e);
            }
            throw e;
        }
    }

    public static byte[] download(ModelRepoEntry entry, int timeoutMs, int maxBytes) throws IOException {
        return HttpTransport.read(entry.url(), timeoutMs, maxBytes);
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        return download(entry, config, null);
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws IOException {
        HttpTransport.checkCancelled(listener);
        IOException last = null;
        List<String> candidates = DownloadCandidatePlanner.rankCandidates(DownloadCandidatePlanner.downloadCandidates(entry, config), config, listener);
        HttpTransport.monitor(config, "Download start name={} fileName={} size={} candidates={} githubFile={}", entry.name(), entry.fileName(), entry.size(), candidates.size(), entry.isGithubFile());
        for (int i = 0; i < candidates.size(); i++) {
            HttpTransport.checkCancelled(listener);
            String url = candidates.get(i);
            if (listener != null) {
                listener.onCandidate(url, i + 1, candidates.size());
            }
            try {
                byte[] data = HttpTransport.read(url, config.timeoutMs(), config.maxDownloadBytes(), listener, entry.size());
                HttpTransport.monitor(config, "Download complete name={} url={} bytes={}", entry.name(), url, data.length);
                return data;
            } catch (CancellationException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                HttpTransport.checkCancelled(listener);
                last = HttpTransport.asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource download candidate failed: {}", url, e);
                HttpTransport.monitor(config, "Download candidate failed name={} url={} error={}", entry.name(), url, e.toString());
            }
        }
        HttpTransport.monitor(config, "Download failed name={} candidates={} last={}", entry.name(), candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No download URL") : last;
    }

    public static byte[] downloadPreview(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        return downloadPreview(entry, config, null);
    }

    public static byte[] downloadPreview(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws IOException {
        HttpTransport.checkCancelled(listener);
        if (entry.previewUrl() == null || entry.previewUrl().isBlank()) {
            throw new IOException("No preview URL");
        }
        HttpTransport.monitor(config, "Preview download start name={} url={}", entry.name(), entry.previewUrl());
        byte[] data = readPreviewCandidates(DownloadCandidatePlanner.urlCandidates(entry.previewUrl(), config),
                config, 2 * 1024 * 1024, listener);
        HttpTransport.monitor(config, "Preview download complete name={} bytes={}", entry.name(), data.length);
        return data;
    }

    /** Only requested for the opened detail page, with a smaller transfer limit than model import. */
    public static byte[] downloadEmbeddedPreview(ModelRepoEntry entry, ResourceStationConfig.State config) throws Exception {
        return downloadEmbeddedPreview(entry, config, null);
    }

    public static byte[] downloadEmbeddedPreview(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws Exception {
        HttpTransport.checkCancelled(listener);
        int limit = Math.min(config.maxDownloadBytes(),
                com.micaftic.morpher.client.gui.resource.station.EmbeddedModelPreview.MAX_MODEL_BYTES);
        if (entry.size() > limit) return null;
        var bounded = new ResourceStationConfig.State(config.urls(), config.selectedUrl(), config.timeoutMs(),
                limit, config.mainlandChinaMode(), config.githubAccelerators());
        byte[] source = readPreviewCandidates(DownloadCandidatePlanner.downloadCandidates(entry, bounded), bounded, limit, listener);
        HttpTransport.checkCancelled(listener);
        byte[] preview = com.micaftic.morpher.client.gui.resource.station.EmbeddedModelPreview.extract(source, entry.fileName());
        HttpTransport.checkCancelled(listener);
        return preview;
    }

    /** In-memory 3D card source, bounded independently of local model downloads. */
    public static byte[] downloadPreviewModel(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws IOException {
        HttpTransport.checkCancelled(listener);
        int limit = Math.min(config.maxDownloadBytes(),
                com.micaftic.morpher.client.gui.resource.station.EmbeddedModelPreview.MAX_MODEL_BYTES);
        if (entry.size() > limit) throw new IOException("Model exceeds the 3D preview transfer limit");
        return readPreviewCandidates(DownloadCandidatePlanner.downloadCandidates(entry, config), config, limit, listener);
    }

    private static byte[] readPreviewCandidates(List<String> candidates, ResourceStationConfig.State config, int limit) throws IOException {
        return readPreviewCandidates(candidates, config, limit, null);
    }

    private static byte[] readPreviewCandidates(List<String> candidates, ResourceStationConfig.State config, int limit, ProgressListener listener) throws IOException {
        IOException last = null;
        for (int i = 0; i < candidates.size(); i++) {
            HttpTransport.checkCancelled(listener);
            String url = candidates.get(i);
            if (listener != null) listener.onCandidate(url, i + 1, candidates.size());
            try { return HttpTransport.read(url, config.timeoutMs(), limit, listener, true); }
            catch (CancellationException cancelled) { throw cancelled; }
            catch (IOException | RuntimeException failure) {
                HttpTransport.checkCancelled(listener);
                last = HttpTransport.asIOException(failure);
            }
        }
        throw last == null ? new IOException("No preview URL") : last;
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
        default Runnable onCancel(Runnable action) { return () -> {}; }

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
