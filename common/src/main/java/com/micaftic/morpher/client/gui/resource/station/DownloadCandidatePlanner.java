package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Download / API candidate URL planning and probe ordering (R1.2.x §24.4).
 *
 * <p>Verbatim move of the candidate builders, the probe ranking and the
 * {@code CandidateProbe} record.
 */
public final class DownloadCandidatePlanner {
    public static final int PROBE_BYTES = 64 * 1024;
    public static final int PROBE_TIMEOUT_MS = 2500;
    public static final int MAX_PROBED_CANDIDATES = 6;

    private DownloadCandidatePlanner() {
    }

    public static List<String> githubCandidates(String url, ResourceStationConfig.State config) {
        Set<String> candidates = new LinkedHashSet<>();
        if (!config.mainlandChinaMode()) {
            candidates.add(url);
        }
        for (String prefix : config.githubAccelerators()) {
            String normalized = normalizeProxyPrefix(prefix);
            if (!normalized.isBlank()) {
                candidates.add(normalized + url);
            }
        }
        candidates.add(url);
        return new ArrayList<>(candidates);
    }

    public static List<String> githubApiCandidates(String url, ResourceStationConfig.State config) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(url);
        for (String prefix : config.githubAccelerators()) {
            String normalized = normalizeProxyPrefix(prefix);
            if (!normalized.isBlank()) {
                candidates.add(normalized + url);
            }
        }
        return new ArrayList<>(candidates);
    }

    public static List<String> downloadCandidates(ModelRepoEntry entry, ResourceStationConfig.State config) {
        Set<String> candidates = new LinkedHashSet<>();
        if (entry.isGithubFile()) {
            String cdnUrl = "https://cdn.jsdelivr.net/gh/" + RepositoryEntryParser.encPath(entry.githubOwner()) + "/" + RepositoryEntryParser.encPath(entry.githubRepo()) + "@" + RepositoryEntryParser.encPath(entry.githubBranch()) + "/" + RepositoryEntryParser.encPath(entry.githubPath());
            if (config.mainlandChinaMode()) {
                candidates.add(cdnUrl);
                candidates.addAll(githubCandidates(entry.url(), config));
            } else {
                candidates.add(entry.url());
                candidates.add(cdnUrl);
                candidates.addAll(githubCandidates(entry.url(), config));
            }
        } else {
            candidates.addAll(urlCandidates(entry.url(), config));
        }
        for (String mirror : entry.mirrors()) {
            candidates.addAll(urlCandidates(mirror, config));
        }
        return new ArrayList<>(candidates);
    }

    public static List<String> urlCandidates(String url, ResourceStationConfig.State config) {
        if (isGithubRelated(url)) {
            return githubCandidates(url, config);
        }
        return List.of(url);
    }

    public static List<String> rankCandidates(List<String> candidates, ResourceStationConfig.State config) {
        if (candidates.size() < 2) {
            return candidates;
        }
        List<CandidateProbe> probes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            CandidateProbe probe = i < MAX_PROBED_CANDIDATES ? probeCandidate(url, config, i) : CandidateProbe.untested(url, i);
            probes.add(probe);
        }
        probes.sort(Comparator
                .comparing(CandidateProbe::success).reversed()
                .thenComparingLong(CandidateProbe::elapsedMs)
                .thenComparingInt(CandidateProbe::originalIndex));
        List<String> ranked = probes.stream().map(CandidateProbe::url).toList();
        HttpTransport.monitor(config, "Download ranked candidates={}", ranked);
        return ranked;
    }

    public static CandidateProbe probeCandidate(String url, ResourceStationConfig.State config, int index) {
        long started = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeout = Math.min(Math.max(1000, config.timeoutMs()), PROBE_TIMEOUT_MS);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", HttpTransport.USER_AGENT);
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            if (url.contains("api.github.com")) {
                connection.setRequestProperty("Accept", HttpTransport.GITHUB_ACCEPT);
                connection.setRequestProperty("X-GitHub-Api-Version", HttpTransport.GITHUB_API_VERSION);
            }
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            int total = 0;
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while (total < PROBE_BYTES && (read = in.read(buffer, 0, Math.min(buffer.length, PROBE_BYTES - total))) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    total += read;
                }
            }
            long elapsed = Math.max(1, HttpTransport.elapsedMs(started));
            HttpTransport.monitor(config, "Probe candidate ok url={} bytes={} elapsedMs={} speed={}", url, total, elapsed, HttpTransport.formatSpeed(HttpTransport.bytesPerSecond(total, started, System.nanoTime())));
            return new CandidateProbe(url, index, true, elapsed);
        } catch (IOException | RuntimeException e) {
            HttpTransport.monitor(config, "Probe candidate failed url={} error={}", url, e.toString());
            return new CandidateProbe(url, index, false, Long.MAX_VALUE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String normalizeProxyPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String trimmed = prefix.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    public static boolean isGithubRelated(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (isGithubRepositoryHost(host)
                    || host.equalsIgnoreCase("api.github.com")
                    || host.equalsIgnoreCase("raw.githubusercontent.com")
                    || host.equalsIgnoreCase("codeload.github.com")
                    || host.endsWith(".githubusercontent.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isGithubRepositoryHost(String host) {
        return "github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host);
    }

    private record CandidateProbe(String url, int originalIndex, boolean success, long elapsedMs) {
        static CandidateProbe untested(String url, int originalIndex) {
            return new CandidateProbe(url, originalIndex, false, Long.MAX_VALUE);
        }
    }
}
