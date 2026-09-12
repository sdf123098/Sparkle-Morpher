package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitHub repository listing source (R1.2.x §24.4).
 *
 * <p>Verbatim move of the GitHub column cluster: tree / contents / archive walking,
 * default-branch resolution, GitHub JSON reads and the jsDelivr fallback trigger.
 * The jsDelivr listing implementation itself lives in {@link JsDelivrSource}.
 */
public final class GitHubSource implements RepositorySource {
    @Override
    public boolean supports(URI uri, String sourceUrl) {
        return DownloadCandidatePlanner.isGithubRepositoryHost(uri == null ? null : uri.getHost());
    }

    @Override
    public List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception {
        return listGithub(uri, config);
    }

    public static List<ModelRepoEntry> listGithub(URI uri, ResourceStationConfig.State config) throws Exception {
        RepositoryEntryParser.GithubPath path = RepositoryEntryParser.GithubPath.parse(uri);
        HttpTransport.monitor(config, "GitHub list parsed owner={} repo={} branch={} path={}", path.owner(), path.repo(), path.branch(), path.path());
        List<ModelRepoEntry> entries = new ArrayList<>();
        Exception primaryError = null;
        if (path.branch().isBlank()) {
            path = path.withBranch(resolveDefaultBranch(path.owner(), path.repo(), config));
            HttpTransport.monitor(config, "GitHub initial branch owner={} repo={} branch={} mainlandChinaMode={}",
                    path.owner(), path.repo(), path.branch(), config.mainlandChinaMode());
        }
        primaryError = tryListGithubTree(path, config, entries, primaryError, "primary");
        if (entries.isEmpty()) {
            primaryError = tryWalkGithub(path, config, entries, primaryError, "fallback");
        }
        if (entries.isEmpty()) {
            listGithubArchive(path, config, entries, primaryError);
        }
        HttpTransport.monitor(config, "GitHub list complete owner={} repo={} branch={} path={} entries={}", path.owner(), path.repo(), path.branch(), path.path(), entries.size());
        return entries;
    }

    public static Exception tryListJsDelivrFlat(RepositoryEntryParser.GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                                 Exception primaryError, String stage) {
        try {
            JsDelivrSource.listFlat(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            HttpTransport.monitor(config, "jsDelivr flat {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    public static Exception tryListGithubTree(RepositoryEntryParser.GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                               Exception primaryError, String stage) {
        try {
            listGithubTree(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            YesSteveModel.LOGGER.warn("[SM] GitHub tree listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), error);
            HttpTransport.monitor(config, "GitHub tree {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    public static Exception tryWalkGithub(RepositoryEntryParser.GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                           Exception primaryError, String stage) {
        try {
            walkGithub(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception error) {
            YesSteveModel.LOGGER.warn("[SM] GitHub contents listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), error);
            HttpTransport.monitor(config, "GitHub contents {} failed owner={} repo={} branch={} path={} error={}",
                    stage, path.owner(), path.repo(), path.branch(), path.path(), error.toString());
            return suppress(primaryError, error);
        }
        return primaryError;
    }

    public static void listGithubArchive(RepositoryEntryParser.GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
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

    public static void listGithubFallbacks(RepositoryEntryParser.GithubPath path, ResourceStationConfig.State config, List<ModelRepoEntry> entries,
                                            Exception primaryError, boolean tryGithubTree) throws Exception {
        try {
            JsDelivrSource.listDirectory(path.owner(), path.repo(), path.branch(), path.path(), config, entries, new HashSet<>(), 0);
        } catch (Exception jsDelivrError) {
            YesSteveModel.LOGGER.warn("[SM] jsDelivr directory listing failed for {}/{}@{}",
                    path.owner(), path.repo(), path.branch(), jsDelivrError);
            HttpTransport.monitor(config, "jsDelivr fallback failed owner={} repo={} branch={} path={} error={}",
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

    public static void listGithubTree(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws Exception {
        String api = "https://api.github.com/repos/" + RepositoryEntryParser.enc(owner) + "/" + RepositoryEntryParser.enc(repo) + "/git/trees/" + RepositoryEntryParser.enc(branch) + "?recursive=1";
        JsonObject root = readGithubJson(api, config, 8 * 1024 * 1024).getAsJsonObject();
        JsonArray tree = root.has("tree") && root.get("tree").isJsonArray() ? root.getAsJsonArray("tree") : new JsonArray();
        HttpTransport.monitor(config, "GitHub tree loaded owner={} repo={} branch={} path={} treeItems={}", owner, repo, branch, path, tree.size());
        String prefix = path == null || path.isBlank() ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "") + "/";
        for (JsonElement element : tree) {
            JsonObject object = element.getAsJsonObject();
            String type = RepositoryEntryParser.firstString(object, "type");
            String childPath = RepositoryEntryParser.firstString(object, "path");
            if (!"blob".equals(type) || childPath == null || !childPath.startsWith(prefix)) {
                continue;
            }
            String name = RepositoryEntryParser.fileNameOnly(childPath);
            if (!RepositoryEntryParser.isImportFile(name)) {
                continue;
            }
            long size = object.has("size") ? object.get("size").getAsLong() : -1L;
            String rawUrl = "https://raw.githubusercontent.com/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "/" + RepositoryEntryParser.encPath(branch) + "/" + RepositoryEntryParser.encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, size, "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        if (root.has("truncated") && root.get("truncated").getAsBoolean()) {
            YesSteveModel.LOGGER.warn("[SM] GitHub tree response was truncated for {}/{}", owner, repo);
            HttpTransport.monitor(config, "GitHub tree truncated owner={} repo={} branch={}", owner, repo, branch);
        }
    }

    public static void listGithubArchive(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws IOException {
        String archiveUrl = "https://codeload.github.com/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "/zip/refs/heads/" + RepositoryEntryParser.encPath(branch);
        byte[] bytes = HttpTransport.read(archiveUrl, config.timeoutMs(), config.maxDownloadBytes(), config, "github-archive");
        String prefix = path == null || path.isBlank() ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "") + "/";
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String childPath = RepositoryEntryParser.stripArchiveRoot(entry.getName());
                if (childPath.isBlank() || !childPath.startsWith(prefix)) {
                    continue;
                }
                String name = RepositoryEntryParser.fileNameOnly(childPath);
                if (!RepositoryEntryParser.isImportFile(name)) {
                    continue;
                }
                String rawUrl = "https://raw.githubusercontent.com/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "/" + RepositoryEntryParser.encPath(branch) + "/" + RepositoryEntryParser.encPath(childPath);
                entries.add(new ModelRepoEntry(name, rawUrl, name, entry.getSize(), "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath));
            }
        }
        HttpTransport.monitor(config, "GitHub archive fallback complete owner={} repo={} branch={} path={} entries={}",
                owner, repo, branch, path, entries.size());
    }

    public static void walkGithub(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws Exception {
        String api = "https://api.github.com/repos/" + RepositoryEntryParser.enc(owner) + "/" + RepositoryEntryParser.enc(repo) + "/contents";
        if (!path.isBlank()) {
            api += "/" + RepositoryEntryParser.encPath(path);
        }
        api += "?ref=" + RepositoryEntryParser.enc(branch);
        JsonElement root = readGithubJson(api, config, 4 * 1024 * 1024);
        JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String type = RepositoryEntryParser.firstString(object, "type");
            String name = RepositoryEntryParser.firstString(object, "name");
            String childPath = RepositoryEntryParser.firstString(object, "path");
            if ("dir".equals(type)) {
                walkGithub(owner, repo, branch, childPath == null ? "" : childPath, config, entries);
            } else if ("file".equals(type) && RepositoryEntryParser.isImportFile(name)) {
                String url = RepositoryEntryParser.firstString(object, "download_url");
                long size = object.has("size") ? object.get("size").getAsLong() : -1L;
                entries.add(new ModelRepoEntry(name, url, name, size, "GitHub: " + owner + "/" + repo, owner, repo, branch, childPath == null ? name : childPath));
            }
        }
    }

    public static String resolveDefaultBranch(String owner, String repo, ResourceStationConfig.State config) {
        String api = "https://api.github.com/repos/" + RepositoryEntryParser.enc(owner) + "/" + RepositoryEntryParser.enc(repo);
        try {
            JsonObject object = readGithubJson(api, config, 1024 * 1024).getAsJsonObject();
            String branch = RepositoryEntryParser.firstString(object, "default_branch");
            if (branch != null && !branch.isBlank()) {
                return branch;
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to resolve GitHub default branch for {}/{}", owner, repo, e);
        }
        return "main";
    }

    public static JsonElement readGithubJson(String url, ResourceStationConfig.State config, int maxBytes) throws IOException {
        Exception last = null;
        List<String> candidates = DownloadCandidatePlanner.githubApiCandidates(url, config);
        HttpTransport.monitor(config, "GitHub JSON start url={} candidates={} mainlandChinaMode={}", url, candidates.size(), config.mainlandChinaMode());
        for (String candidate : candidates) {
            try {
                String json = new String(HttpTransport.read(candidate, config.timeoutMs(), maxBytes, config, "github-json"), StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(json);
                HttpTransport.monitor(config, "GitHub JSON complete url={} candidate={} bytes={}", url, candidate, json.length());
                return parsed;
            } catch (IOException | RuntimeException e) {
                last = e;
                HttpTransport.monitor(config, "GitHub JSON candidate failed url={} candidate={} error={}", url, candidate, e.toString());
            }
        }
        HttpTransport.monitor(config, "GitHub JSON failed url={} candidates={} last={}", url, candidates.size(), last == null ? "none" : last.toString());
        YesSteveModel.LOGGER.warn("[SM] GitHub JSON failed: {}", url, last);
        if (last instanceof IOException io) {
            throw io;
        }
        throw new IOException("Invalid GitHub API response", last);
    }

    public static byte[] readGithub(String url, ResourceStationConfig.State config, int maxBytes) throws IOException {
        IOException last = null;
        List<String> candidates = DownloadCandidatePlanner.githubCandidates(url, config);
        HttpTransport.monitor(config, "GitHub read start url={} candidates={} mainlandChinaMode={}", url, candidates.size(), config.mainlandChinaMode());
        for (String candidate : candidates) {
            try {
                byte[] data = HttpTransport.read(candidate, config.timeoutMs(), maxBytes, config, "github-read");
                HttpTransport.monitor(config, "GitHub read complete url={} candidate={} bytes={}", url, candidate, data.length);
                return data;
            } catch (IOException e) {
                last = e;
                YesSteveModel.LOGGER.warn("[SM] GitHub candidate failed: {}", candidate, e);
                HttpTransport.monitor(config, "GitHub read candidate failed url={} candidate={} error={}", url, candidate, e.toString());
            }
        }
        HttpTransport.monitor(config, "GitHub read failed url={} candidates={} last={}", url, candidates.size(), last == null ? "none" : last.toString());
        throw last == null ? new IOException("No GitHub URL") : last;
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
}
