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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * R1.2.x §24.4 GitHubSource（neo26.x 适配版，行为等价搬运）。
 *
 * <p>GitHub 仓库列目录：tree API 优先，contents 回退，最后 archive 兜底。
 * 使用本分支的 RepositoryEntryParser.GithubPath 与 HttpTransport 签名模型。
 */
public final class GitHubSource implements RepositorySource {
    @Override
    public boolean supports(URI uri, String sourceUrl) {
        String host = uri == null ? null : uri.getHost();
        return host != null && ("github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host));
    }

    @Override
    public List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception {
        return listGithub(uri, config);
    }

    public static List<ModelRepoEntry> listGithub(URI uri, ResourceStationConfig.State config) throws Exception {
        RepositoryEntryParser.GithubPath path = RepositoryEntryParser.GithubPath.parse(uri);
        if (path.branch().isBlank()) {
            path = path.withBranch(resolveDefaultBranch(path.owner(), path.repo(), config));
        }
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] GitHub listing owner={} repo={} branch={} path={}",
                    path.owner(), path.repo(), path.branch(), path.path());
        }
        List<ModelRepoEntry> entries = new ArrayList<>();
        try {
            listGithubTree(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
        } catch (Exception treeError) {
            YesSteveModel.LOGGER.warn("[SM] GitHub tree listing failed for {}/{}@{}, trying contents fallback",
                    path.owner(), path.repo(), path.branch(), treeError);
            try {
                walkGithub(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
            } catch (Exception contentsError) {
                contentsError.addSuppressed(treeError);
                YesSteveModel.LOGGER.warn("[SM] GitHub contents listing failed for {}/{}@{}, trying archive fallback",
                        path.owner(), path.repo(), path.branch(), contentsError);
                listGithubArchive(path.owner(), path.repo(), path.branch(), path.path(), config, entries);
            }
        }
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] GitHub listing finished owner={} repo={} branch={} path={} entries={}",
                    path.owner(), path.repo(), path.branch(), path.path(), entries.size());
        }
        return entries;
    }

    public static void listGithubTree(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws Exception {
        String api = "https://api.github.com/repos/" + RepositoryEntryParser.enc(owner) + "/" + RepositoryEntryParser.enc(repo) + "/git/trees/" + RepositoryEntryParser.enc(branch) + "?recursive=1";
        JsonObject root = readGithubJson(api, config, 8 * 1024 * 1024).getAsJsonObject();
        JsonArray tree = root.has("tree") && root.get("tree").isJsonArray() ? root.getAsJsonArray("tree") : new JsonArray();
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
        }
    }

    public static void listGithubArchive(String owner, String repo, String branch, String path, ResourceStationConfig.State config, List<ModelRepoEntry> entries) throws IOException {
        String archiveUrl = "https://codeload.github.com/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "/zip/refs/heads/" + RepositoryEntryParser.encPath(branch);
        byte[] bytes = readGithub(archiveUrl, config, config.maxDownloadBytes());
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
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] GitHub archive fallback finished owner={} repo={} branch={} path={} entries={}",
                    owner, repo, branch, path, entries.size());
        }
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
        List<String> candidates = DownloadCandidatePlanner.githubCandidates(url, config);
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] GitHub JSON candidates sourceUrl={} candidates={}", url, candidates);
        }
        for (String candidate : candidates) {
            try {
                String json = new String(HttpTransport.read(candidate, config.timeoutMs(), maxBytes, true), StandardCharsets.UTF_8);
                return JsonParser.parseString(json);
            } catch (IOException | RuntimeException e) {
                last = e;
                YesSteveModel.LOGGER.warn("[SM] GitHub JSON candidate failed: {}", candidate, e);
            }
        }
        if (last instanceof IOException io) {
            throw io;
        }
        throw new IOException("Invalid GitHub API response", last);
    }

    public static byte[] readGithub(String url, ResourceStationConfig.State config, int maxBytes) throws IOException {
        IOException last = null;
        List<String> candidates = DownloadCandidatePlanner.githubCandidates(url, config);
        if (HttpTransport.debugLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM][ResourceStation] GitHub binary candidates sourceUrl={} candidates={}", url, candidates);
        }
        for (String candidate : candidates) {
            try {
                return HttpTransport.read(candidate, config.timeoutMs(), maxBytes, true);
            } catch (IOException e) {
                last = e;
                YesSteveModel.LOGGER.warn("[SM] GitHub candidate failed: {}", candidate, e);
            }
        }
        throw last == null ? new IOException("No GitHub URL") : last;
    }

}
