package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.ModelIdUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ModelRepoClient {
    private static final String USER_AGENT = "OpenYSM-ResourceStation";
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String CACHE_BUSTER_PARAM = "ysm_refresh";
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]+)\"");
    private static final int PROBE_BYTES = 64 * 1024;
    private static final int PROBE_TIMEOUT_MS = 2500;
    private static final int MAX_PROBED_CANDIDATES = 6;
    private static final int LOW_SPEED_BYTES_PER_SECOND = 8 * 1024;
    private static final int LOW_SPEED_GRACE_MS = 7000;
    private static final int LOW_SPEED_WINDOW_MS = 5000;

    private ModelRepoClient() {
    }

    public static List<ModelRepoEntry> list(String sourceUrl, int timeoutMs) throws Exception {
        return list(sourceUrl, new ResourceStationConfig.State(List.of(sourceUrl), sourceUrl, timeoutMs, 64 * 1024 * 1024, false, List.of()));
    }

    public static List<ModelRepoEntry> list(String sourceUrl, ResourceStationConfig.State config) throws Exception {
        URI uri = URI.create(sourceUrl.trim());
        monitor(config, "List start source={} host={} timeoutMs={}", sourceUrl, uri.getHost(), config.timeoutMs());
        if ("github.com".equalsIgnoreCase(uri.getHost())) {
            return listGithub(uri, config);
        }
        String indexUrl = sourceUrl.endsWith("/") ? sourceUrl + "index.json" : sourceUrl;
        if (!indexUrl.endsWith("index.json")) {
            indexUrl = indexUrl + "/index.json";
        }
        monitor(config, "List index source={} indexUrl={}", sourceUrl, indexUrl);
        List<ModelRepoEntry> entries = parseIndex(indexUrl, new String(read(indexUrl, config.timeoutMs(), 2 * 1024 * 1024, config, "index"), StandardCharsets.UTF_8), config);
        monitor(config, "List index complete indexUrl={} entries={}", indexUrl, entries.size());
        return entries;
    }

    public static byte[] download(ModelRepoEntry entry, int timeoutMs, int maxBytes) throws IOException {
        return read(entry.url(), timeoutMs, maxBytes);
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        IOException last = null;
        List<String> candidates = rankCandidates(downloadCandidates(entry, config), config);
        monitor(config, "Download start name={} fileName={} size={} candidates={} githubFile={}", entry.name(), entry.fileName(), entry.size(), candidates.size(), entry.isGithubFile());
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            try {
                byte[] data = read(url, config.timeoutMs(), config.maxDownloadBytes(), config, "download");
                monitor(config, "Download complete name={} url={} bytes={}", entry.name(), url, data.length);
                return data;
            } catch (IOException | RuntimeException e) {
                last = asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource download candidate failed: {}", url, e);
                monitor(config, "Download candidate failed name={} url={} error={}", entry.name(), url, e.toString());
            }
        }
        monitor(config, "Download failed name={} candidates={} last={}", entry.name(), candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No download URL") : last;
    }

    public static byte[] download(ModelRepoEntry entry, ResourceStationConfig.State config, ProgressListener listener) throws IOException {
        IOException last = null;
        List<String> candidates = rankCandidates(downloadCandidates(entry, config), config);
        monitor(config, "Download start name={} fileName={} size={} candidates={} githubFile={}", entry.name(), entry.fileName(), entry.size(), candidates.size(), entry.isGithubFile());
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            if (listener != null) {
                listener.onCandidate(url, i + 1, candidates.size());
            }
            try {
                byte[] data = read(url, config.timeoutMs(), config.maxDownloadBytes(), listener, config, "download", entry.size());
                monitor(config, "Download complete name={} url={} bytes={}", entry.name(), url, data.length);
                return data;
            } catch (IOException | RuntimeException e) {
                last = asIOException(e);
                YesSteveModel.LOGGER.warn("[SM] Resource download candidate failed: {}", url, e);
                monitor(config, "Download candidate failed name={} url={} error={}", entry.name(), url, e.toString());
            }
        }
        monitor(config, "Download failed name={} candidates={} last={}", entry.name(), candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No download URL") : last;
    }

    public static byte[] downloadPreview(ModelRepoEntry entry, ResourceStationConfig.State config) throws IOException {
        if (entry.previewUrl() == null || entry.previewUrl().isBlank()) {
            throw new IOException("No preview URL");
        }
        monitor(config, "Preview download start name={} url={}", entry.name(), entry.previewUrl());
        byte[] data = read(entry.previewUrl(), config.timeoutMs(), 2 * 1024 * 1024, config, "preview");
        monitor(config, "Preview download complete name={} bytes={}", entry.name(), data.length);
        return data;
    }

    public static String safeModelId(ModelRepoEntry entry) {
        String stem = stripExtension(entry.fileName() == null || entry.fileName().isBlank() ? entry.name() : entry.fileName());
        String normalized = ModelIdUtil.normalizeImportModelId(stem);
        if (ModelIdUtil.isValidModelId(normalized)) {
            if (!ModelIdUtil.hasLetterOrNumber(normalized)) {
                return "repo/" + sha1(entry.url()).substring(0, 12);
            }
            return normalized;
        }
        return "repo/" + sha1(entry.url()).substring(0, 12);
    }

    private static List<ModelRepoEntry> parseIndex(String indexUrl, String json, ResourceStationConfig.State config) {
        List<ModelRepoEntry> entries = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else {
            JsonObject object = root.getAsJsonObject();
            array = object.has("models") && object.get("models").isJsonArray() ? object.getAsJsonArray("models") : new JsonArray();
        }
        URI base = URI.create(indexUrl);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String url = firstString(object, "url", "download", "download_url", "file");
            String name = firstString(object, "name", "title", "fileName", "filename");
            String description = firstString(object, "description", "desc");
            String author = firstString(object, "author", "authors", "creator");
            String tags = tagsString(object);
            String preview = firstString(object, "preview", "preview_url", "previewUrl", "image", "thumbnail", "icon");
            List<String> mirrors = urlList(object, base, "mirrors", "mirror", "urls", "download_urls", "downloadUrls", "downloads");
            long size = object.has("size") && object.get("size").isJsonPrimitive() ? object.get("size").getAsLong() : -1L;
            if (url == null || url.isBlank()) {
                continue;
            }
            URI resolved = base.resolve(url);
            String previewUrl = preview == null || preview.isBlank() ? "" : base.resolve(preview).toString();
            String fileName = fileNameFromUrl(resolved.toString(), name);
            if (isImportFile(fileName)) {
                entries.add(new ModelRepoEntry(name == null || name.isBlank() ? fileName : name, resolved.toString(), fileName, size, description == null ? "" : description, "", "", "", "", author == null ? "" : author, tags, previewUrl, mirrors));
            }
        }
        monitor(config, "Parsed index indexUrl={} rawItems={} importableEntries={}", indexUrl, array.size(), entries.size());
        return entries;
    }

    private static List<ModelRepoEntry> listGithub(URI uri, ResourceStationConfig.State config) throws Exception {
        GithubPath path = GithubPath.parse(uri);
        monitor(config, "GitHub list parsed owner={} repo={} branch={} path={}", path.owner(), path.repo(), path.branch(), path.path());
        List<ModelRepoEntry> entries = new ArrayList<>();
        Exception primaryError = null;
        if (path.branch().isBlank()) {
            path = path.withBranch(resolveDefaultBranch(path.owner(), path.repo(), config));
            monitor(config, "GitHub initial branch owner={} repo={} branch={} mainlandChinaMode={}",
                    path.owner(), path.repo(), path.branch(), config.mainlandChinaMode());
        }
        primaryError = tryListGithubTree(path, config, entries, primaryError, "primary");
        if (entries.isEmpty()) {
            primaryError = tryWalkGithub(path, config, entries, primaryError, "fallback");
        }
        if (entries.isEmpty()) {
            listGithubArchive(path, config, entries, primaryError);
        }
        monitor(config, "GitHub list complete owner={} repo={} branch={} path={} entries={}", path.owner(), path.repo(), path.branch(), path.path(), entries.size());
        return entries;
    }

    private static Exception tryListJsDelivrFlat(GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                                 Exception primaryError, String stage) {
        try {
            listJsDelivrFlat(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            monitor(config, "jsDelivr flat {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    private static Exception tryListGithubTree(GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                               Exception primaryError, String stage) {
        try {
            listGithubTree(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            YesSteveModel.LOGGER.warn("[SM] GitHub tree listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), error);
            monitor(config, "GitHub tree {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    private static Exception tryWalkGithub(GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                           Exception primaryError, String stage) {
        try {
            walkGithub(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            YesSteveModel.LOGGER.warn("[SM] GitHub contents listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), error);
            monitor(config, "GitHub contents {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    private static void listGithubArchive(GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                          Exception primaryError) throws Exception {
        try {
            listGithubArchive(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (IOException archiveError) {
            if (primaryError != null) {
                archiveError.addSuppressed(primaryError);
            }
            throw archiveError;
        }
    }

    private static void listGithubFallbacks(GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                            Exception primaryError, boolean tryGithubTree) throws Exception {
        try {
            listJsDelivrDirectory(path.owner(), path.repo(), path.branch(), path.path(), config, entries, new HashSet<>(), 0);
        } catch (Exception jsDelivrError) {
            YesSteveModel.LOGGER.warn("[SM] jsDelivr directory listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), jsDelivrError);
            monitor(config, "jsDelivr fallback failed owner={} repo={} branch={} path={} error={}",
                    path.owner(), path.repo(), path.branch(), path.path(), jsDelivrError.toString());
            primaryError = suppress(primaryError, jsDelivrError);
        }
        if (!entries.isEmpty()) {
            return;
        }
        if (tryGithubTree) {
            primaryError = tryListGithubTree(path, config, entries, primaryError, "fallback");
        }
        if (!entries.isEmpty()) {
            return;
        }
        try {
            listGithubArchive(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (IOException archiveError) {
            if (primaryError != null) {
                archiveError.addSuppressed(primaryError);
            }
            throw archiveError;
        }
    }

    private static void listGithubTree(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws Exception {
        String api = "https://api.github.com/repos/" + enc(owner) + "/" + enc(repo) + "/git/trees/" + enc(branch) + "?recursive=1";
        JsonObject root = readGithubJson(api, config, 8 * 1024 * 1024).getAsJsonObject();
        JsonArray tree = root.has("tree") && root.get("tree").isJsonArray() ? root.getAsJsonArray("tree") : new JsonArray();
        monitor(config, "GitHub tree loaded owner={} repo={} branch={} path={} treeItems={}", owner, repo, branch, path, tree.size());
        String prefix = path == null || path.isBlank() ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "") + "/";
        for (JsonElement element : tree) {
            JsonObject object = element.getAsJsonObject();
            String type = firstString(object, "type");
            String childPath = firstString(object, "path");
            if (!"blob".equals(type) || childPath == null || !childPath.startsWith(prefix)) {
                continue;
            }
            String name = fileNameOnly(childPath);
            if (!isImportFile(name)) {
                continue;
            }
            long size = object.has("size") ? object.get("size").getAsLong() : -1L;
            String rawUrl = "https://raw.githubusercontent.com/" + encPath(owner) + "/" + encPath(repo) + "/" + encPath(branch) + "/" + encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, size, "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        if (root.has("truncated") && root.get("truncated").getAsBoolean()) {
            YesSteveModel.LOGGER.warn("[SM] GitHub tree response was truncated for {}/{}", owner, repo);
            monitor(config, "GitHub tree truncated owner={} repo={} branch={}", owner, repo, branch);
        }
    }

    private static void listGithubArchive(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws IOException {
        String archiveUrl = "https://codeload.github.com/" + encPath(owner) + "/" + encPath(repo) + "/zip/refs/heads/" + encPath(branch);
        byte[] bytes = read(archiveUrl, config.timeoutMs(), config.maxDownloadBytes(), config, "github-archive");
        String prefix = path == null || path.isBlank() ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "") + "/";
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String childPath = stripArchiveRoot(entry.getName());
                if (childPath.isBlank() || !childPath.startsWith(prefix)) {
                    continue;
                }
                String name = fileNameOnly(childPath);
                if (!isImportFile(name)) {
                    continue;
                }
                String rawUrl = "https://raw.githubusercontent.com/" + encPath(owner) + "/" + encPath(repo) + "/" + encPath(branch) + "/" + encPath(childPath);
                entries.add(new ModelRepoEntry(name, rawUrl, name, entry.getSize(), "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath));
            }
        }
        monitor(config, "GitHub archive fallback complete owner={} repo={} branch={} path={} entries={}",
                owner, repo, branch, path, entries.size());
    }

    private static void listJsDelivrDirectory(String owner, String repo, String branch, String path, ResourceStationConfig.State config,
                                              List<ModelRepoEntry> entries, Set<String> visited, int depth) throws IOException {
        if (depth > 8 || visited.size() > 1200) {
            return;
        }
        String normalizedPath = path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        String key = owner + "/" + repo + "@" + branch + "/" + normalizedPath;
        if (!visited.add(key)) {
            return;
        }
        String basePath = normalizedPath.isBlank() ? "" : normalizedPath + "/";
        String pageUrl = "https://cdn.jsdelivr.net/gh/" + encPath(owner) + "/" + encPath(repo) + "@" + encPath(branch) + "/" + encPath(basePath);
        String html = new String(read(pageUrl, config.timeoutMs(), 2 * 1024 * 1024, config, "jsdelivr-list"), StandardCharsets.UTF_8);
        int before = entries.size();
        Matcher matcher = HREF_PATTERN.matcher(html);
        while (matcher.find()) {
            String href = matcher.group(1);
            String prefix = "/gh/" + owner + "/" + repo + "@" + branch + "/";
            if (!href.startsWith(prefix)) {
                continue;
            }
            String childPath = href.substring(prefix.length());
            int query = childPath.indexOf('?');
            if (query >= 0) {
                childPath = childPath.substring(0, query);
            }
            childPath = childPath.replaceAll("^/+", "");
            if (childPath.isBlank() || childPath.equals(basePath)) {
                continue;
            }
            if (childPath.endsWith("/")) {
                listJsDelivrDirectory(owner, repo, branch, childPath, config, entries, visited, depth + 1);
                continue;
            }
            String name = fileNameOnly(childPath);
            if (!isImportFile(name)) {
                continue;
            }
            String rawUrl = "https://cdn.jsdelivr.net/gh/" + encPath(owner) + "/" + encPath(repo) + "@" + encPath(branch) + "/" + encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, -1L, "jsDelivr: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        monitor(config, "jsDelivr directory listed owner={} repo={} branch={} path={} entriesAdded={} visited={}",
                owner, repo, branch, normalizedPath, entries.size() - before, visited.size());
    }

    private static void listJsDelivrFlat(String owner, String repo, String branch, String path, ResourceStationConfig.State config,
                                         List<ModelRepoEntry> entries) throws IOException {
        String normalizedPath = path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        String prefix = normalizedPath.isBlank() ? "" : normalizedPath + "/";
        String api = "https://data.jsdelivr.com/v1/package/gh/" + encPath(owner) + "/" + encPath(repo) + "@" + encPath(branch) + "/flat";
        String json = new String(read(api, config.timeoutMs(), 4 * 1024 * 1024, config, "jsdelivr-flat"), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray files = root.has("files") && root.get("files").isJsonArray() ? root.getAsJsonArray("files") : new JsonArray();
        int before = entries.size();
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String childPath = firstString(object, "name");
            if (childPath == null) {
                continue;
            }
            childPath = childPath.replace('\\', '/').replaceAll("^/+", "");
            if (childPath.isBlank() || !childPath.startsWith(prefix)) {
                continue;
            }
            String name = fileNameOnly(childPath);
            if (!isImportFile(name)) {
                continue;
            }
            long size = object.has("size") && object.get("size").isJsonPrimitive() ? object.get("size").getAsLong() : -1L;
            String rawUrl = "https://cdn.jsdelivr.net/gh/" + encPath(owner) + "/" + encPath(repo) + "@" + encPath(branch) + "/" + encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, size, "jsDelivr: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        monitor(config, "jsDelivr flat listed owner={} repo={} branch={} path={} files={} entriesAdded={}",
                owner, repo, branch, normalizedPath, files.size(), entries.size() - before);
    }

    private static void walkGithub(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws Exception {
        String api = "https://api.github.com/repos/" + enc(owner) + "/" + enc(repo) + "/contents";
        if (!path.isBlank()) {
            api += "/" + encPath(path);
        }
        api += "?ref=" + enc(branch);
        JsonElement root = readGithubJson(api, config, 4 * 1024 * 1024);
        JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String type = firstString(object, "type");
            String name = firstString(object, "name");
            String childPath = firstString(object, "path");
            if ("dir".equals(type)) {
                walkGithub(owner, repo, branch, childPath == null ? "" : childPath, config, entries);
            } else if ("file".equals(type) && isImportFile(name)) {
                String url = firstString(object, "download_url");
                long size = object.has("size") ? object.get("size").getAsLong() : -1L;
                entries.add(new ModelRepoEntry(name, url, name, size, "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath == null ? name : childPath));
            }
        }
    }

    private static String resolveDefaultBranch(String owner, String repo, ResourceStationConfig.State config) {
        String api = "https://api.github.com/repos/" + enc(owner) + "/" + enc(repo);
        try {
            JsonObject object = readGithubJson(api, config, 1024 * 1024).getAsJsonObject();
            String branch = firstString(object, "default_branch");
            if (branch != null && !branch.isBlank()) {
                return branch;
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to resolve GitHub default branch for {}/{}", owner, repo, e);
        }
        return "main";
    }

    private static JsonElement readGithubJson(String url, ResourceStationConfig.State config, int maxBytes) throws IOException {
        Exception last = null;
        List<String> candidates = githubApiCandidates(url, config);
        monitor(config, "GitHub JSON start url={} candidates={} mainlandChinaMode={}", url, candidates.size(), config.mainlandChinaMode());
        for (String candidate : candidates) {
            try {
                String json = new String(read(candidate, config.timeoutMs(), maxBytes, config, "github-json"), StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(json);
                monitor(config, "GitHub JSON complete url={} candidate={} bytes={}", url, candidate, json.length());
                return parsed;
            } catch (IOException | RuntimeException e) {
                last = e;
                monitor(config, "GitHub JSON candidate failed url={} candidate={} error={}", url, candidate, e.toString());
            }
        }
        monitor(config, "GitHub JSON failed url={} candidates={} last={}", url, candidates.size(), last == null ? "none" : last.toString());
        YesSteveModel.LOGGER.warn("[SM] GitHub JSON failed: {}", url, last);
        if (last instanceof IOException io) {
            throw io;
        }
        throw new IOException("Invalid GitHub API response", last);
    }

    private static byte[] readGithub(String url, ResourceStationConfig.State config, int maxBytes) throws IOException {
        IOException last = null;
        List<String> candidates = githubCandidates(url, config);
        monitor(config, "GitHub read start url={} candidates={} mainlandChinaMode={}", url, candidates.size(), config.mainlandChinaMode());
        for (String candidate : candidates) {
            try {
                byte[] data = read(candidate, config.timeoutMs(), maxBytes, config, "github-read");
                monitor(config, "GitHub read complete url={} candidate={} bytes={}", url, candidate, data.length);
                return data;
            } catch (IOException e) {
                last = e;
                YesSteveModel.LOGGER.warn("[SM] GitHub candidate failed: {}", candidate, e);
                monitor(config, "GitHub read candidate failed url={} candidate={} error={}", url, candidate, e.toString());
            }
        }
        monitor(config, "GitHub read failed url={} candidates={} last={}", url, candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No GitHub URL") : last;
    }

    private static byte[] read(String url, int timeoutMs, int maxBytes) throws IOException {
        return read(url, timeoutMs, maxBytes, null, null, "read");
    }

    private static byte[] read(String url, int timeoutMs, int maxBytes, ProgressListener listener) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, null, "read");
    }

    private static byte[] read(String url, int timeoutMs, int maxBytes, ResourceStationConfig.State config, String operation) throws IOException {
        return read(url, timeoutMs, maxBytes, null, config, operation);
    }

    private static byte[] read(String url, int timeoutMs, int maxBytes, ProgressListener listener, ResourceStationConfig.State config, String operation) throws IOException {
        return read(url, timeoutMs, maxBytes, listener, config, operation, -1L);
    }

    private static byte[] read(String url, int timeoutMs, int maxBytes, ProgressListener listener, ResourceStationConfig.State config, String operation, long expectedBytes) throws IOException {
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

    private static int progressTotal(int contentLength, long expectedBytes, int maxBytes) {
        if (contentLength > 0) {
            return contentLength;
        }
        if (expectedBytes > 0 && expectedBytes <= maxBytes && expectedBytes <= Integer.MAX_VALUE) {
            return (int) expectedBytes;
        }
        return 0;
    }

    private static void checkCancelled(ProgressListener listener) {
        if (listener != null && listener.isCancelled()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    private static List<String> githubCandidates(String url, ResourceStationConfig.State config) {
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

    private static List<String> githubApiCandidates(String url, ResourceStationConfig.State config) {
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

    private static List<String> downloadCandidates(ModelRepoEntry entry, ResourceStationConfig.State config) {
        Set<String> candidates = new LinkedHashSet<>();
        if (entry.isGithubFile()) {
            String cdnUrl = "https://cdn.jsdelivr.net/gh/" + encPath(entry.githubOwner()) + "/" + encPath(entry.githubRepo()) + "@" + encPath(entry.githubBranch()) + "/" + encPath(entry.githubPath());
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

    private static List<String> urlCandidates(String url, ResourceStationConfig.State config) {
        if (isGithubRelated(url)) {
            return githubCandidates(url, config);
        }
        return List.of(url);
    }

    private static List<String> rankCandidates(List<String> candidates, ResourceStationConfig.State config) {
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
        monitor(config, "Download ranked candidates={}", ranked);
        return ranked;
    }

    private static CandidateProbe probeCandidate(String url, ResourceStationConfig.State config, int index) {
        long started = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeout = Math.min(Math.max(1000, config.timeoutMs()), PROBE_TIMEOUT_MS);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            if (url.contains("api.github.com")) {
                connection.setRequestProperty("Accept", GITHUB_ACCEPT);
                connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION);
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
            long elapsed = Math.max(1, elapsedMs(started));
            monitor(config, "Probe candidate ok url={} bytes={} elapsedMs={} speed={}", url, total, elapsed, formatSpeed(bytesPerSecond(total, started, System.nanoTime())));
            return new CandidateProbe(url, index, true, elapsed);
        } catch (IOException | RuntimeException e) {
            monitor(config, "Probe candidate failed url={} error={}", url, e.toString());
            return new CandidateProbe(url, index, false, Long.MAX_VALUE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeProxyPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String trimmed = prefix.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private static boolean shouldBypassCache(String operation) {
        return "index".equals(operation)
                || "preview".equals(operation)
                || "github-json".equals(operation)
                || "github-read".equals(operation)
                || "github-archive".equals(operation)
                || operation != null && operation.startsWith("jsdelivr");
    }

    private static String withCacheBuster(String url) {
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

    private static boolean isGithubRelated(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("github.com")
                    || host.equalsIgnoreCase("api.github.com")
                    || host.equalsIgnoreCase("raw.githubusercontent.com")
                    || host.equalsIgnoreCase("codeload.github.com")
                    || host.endsWith(".githubusercontent.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void monitor(ResourceStationConfig.State config, String message, Object... args) {
        if (ResourceStationConfig.monitorLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM-RESOURCE] " + message, args);
        }
    }

    private static Exception suppress(Exception primary, Exception next) {
        if (next == null) {
            return primary;
        }
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private static IOException asIOException(Throwable throwable) {
        return throwable instanceof IOException io ? io : new IOException(throwable);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static long elapsedMs(long startNanos, long nowNanos) {
        return (nowNanos - startNanos) / 1_000_000L;
    }

    private static long bytesPerSecond(int bytes, long startNanos, long nowNanos) {
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

    public static String hostName(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (IllegalArgumentException ignored) {
            return url;
        }
    }

    private static boolean isImportFile(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ysm") || lower.endsWith(".zip") || lower.endsWith(".bbmodel");
    }

    private static String firstString(JsonObject object, String... names) {
        for (String name : names) {
            if (object.has(name) && !object.get(name).isJsonNull()) {
                return object.get(name).getAsString();
            }
        }
        return null;
    }

    private static String tagsString(JsonObject object) {
        if (!object.has("tags") || object.get("tags").isJsonNull()) {
            return "";
        }
        JsonElement element = object.get("tags");
        if (element.isJsonArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonElement tag : element.getAsJsonArray()) {
                if (!tag.isJsonNull()) {
                    tags.add(tag.getAsString());
                }
            }
            return String.join(", ", tags);
        }
        return element.getAsString();
    }

    private static List<String> urlList(JsonObject object, URI base, String... names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (!object.has(name) || object.get(name).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(name);
            if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    addUrlValue(result, base, item);
                }
            } else {
                addUrlValue(result, base, element);
            }
        }
        return result;
    }

    private static void addUrlValue(List<String> result, URI base, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        String value;
        if (element.isJsonObject()) {
            value = firstString(element.getAsJsonObject(), "url", "download", "download_url", "href");
        } else {
            value = element.getAsString();
        }
        if (value == null || value.isBlank()) {
            return;
        }
        String resolved = base.resolve(value.trim()).toString();
        if (!result.contains(resolved)) {
            result.add(resolved);
        }
    }

    private static String fileNameFromUrl(String url, String fallback) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? fallback : name;
    }

    private static String fileNameOnly(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripArchiveRoot(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : "";
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(ext)) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name;
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to hash repo URL", e);
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encPath(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.split("/")) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(enc(part));
        }
        return out.toString();
    }

    private record GithubPath(String owner, String repo, String branch, String path) {
        GithubPath withBranch(String branch) {
            return new GithubPath(owner, repo, branch, path);
        }

        static GithubPath parse(URI uri) {
            String[] parts = uri.getPath().replaceFirst("^/+", "").split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid GitHub URL");
            }
            String branch = "";
            String path = "";
            if (parts.length >= 4 && "tree".equals(parts[2])) {
                branch = parts[3];
                if (parts.length > 4) {
                    path = String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
                }
            }
            return new GithubPath(parts[0], parts[1], branch, path);
        }
    }

    private record CandidateProbe(String url, int originalIndex, boolean success, long elapsedMs) {
        static CandidateProbe untested(String url, int originalIndex) {
            return new CandidateProbe(url, originalIndex, false, Long.MAX_VALUE);
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
