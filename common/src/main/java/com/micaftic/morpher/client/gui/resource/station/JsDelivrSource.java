package com.micaftic.morpher.client.gui.resource.station;

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
import java.util.Set;
import java.util.regex.Matcher;

/**
 * jsDelivr listing source (R1.2.x §24.4).
 *
 * <p>Thin wrapper exposing the jsDelivr directory / flat listing used by
 * {@link GitHubSource} as its fallback. It is deliberately NOT registered as a
 * top-level route in {@code ModelRepoClient}: legacy routing only distinguished a
 * GitHub repository host and let everything else fall through to index.json, and
 * §24.2 forbids changing that behaviour. The {@code list} entry point is kept so the
 * type is a fully-formed {@link RepositorySource} ready to be routed in 1.2.8.
 */
public final class JsDelivrSource implements RepositorySource {
    @Override
    public boolean supports(URI uri, String sourceUrl) {
        String host = uri == null ? null : uri.getHost();
        return host != null && (host.equalsIgnoreCase("cdn.jsdelivr.net") || host.equalsIgnoreCase("data.jsdelivr.com"));
    }

    @Override
    public List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws IOException {
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/+", "");
        String[] parts = path.split("/");
        if (parts.length < 3 || !"gh".equals(parts[0])) {
            throw new IOException("Invalid jsDelivr URL");
        }
        String owner = parts[1];
        String repoRef = parts[2];
        int at = repoRef.indexOf('@');
        String repo = at >= 0 ? repoRef.substring(0, at) : repoRef;
        String branch = at >= 0 ? repoRef.substring(at + 1) : "main";
        StringBuilder subPath = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (!subPath.isEmpty()) {
                subPath.append('/');
            }
            subPath.append(parts[i]);
        }
        List<ModelRepoEntry> entries = new ArrayList<>();
        listDirectory(owner, repo, branch, subPath.toString(), config, entries, new HashSet<>(), 0);
        return entries;
    }

    public static void listDirectory(String owner, String repo, String branch, String path, ResourceStationConfig.State config,
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
        String pageUrl = "https://cdn.jsdelivr.net/gh/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "@" + RepositoryEntryParser.encPath(branch) + "/" + RepositoryEntryParser.encPath(basePath);
        String html = new String(HttpTransport.read(pageUrl, config.timeoutMs(), 2 * 1024 * 1024, config, "jsdelivr-list"), StandardCharsets.UTF_8);
        int before = entries.size();
        Matcher matcher = RepositoryEntryParser.HREF_PATTERN.matcher(html);
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
                listDirectory(owner, repo, branch, childPath, config, entries, visited, depth + 1);
                continue;
            }
            String name = RepositoryEntryParser.fileNameOnly(childPath);
            if (!RepositoryEntryParser.isImportFile(name)) {
                continue;
            }
            String rawUrl = "https://cdn.jsdelivr.net/gh/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "@" + RepositoryEntryParser.encPath(branch) + "/" + RepositoryEntryParser.encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, -1L, "jsDelivr: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        HttpTransport.monitor(config, "jsDelivr directory listed owner={} repo={} branch={} path={} entriesAdded={} visited={}",
                owner, repo, branch, normalizedPath, entries.size() - before, visited.size());
    }

    public static void listFlat(String owner, String repo, String branch, String path, ResourceStationConfig.State config,
                                         List<ModelRepoEntry> entries) throws IOException {
        String normalizedPath = path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        String prefix = normalizedPath.isBlank() ? "" : normalizedPath + "/";
        String api = "https://data.jsdelivr.com/v1/package/gh/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "@" + RepositoryEntryParser.encPath(branch) + "/flat";
        String json = new String(HttpTransport.read(api, config.timeoutMs(), 4 * 1024 * 1024, config, "jsdelivr-flat"), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray files = root.has("files") && root.get("files").isJsonArray() ? root.getAsJsonArray("files") : new JsonArray();
        int before = entries.size();
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String childPath = RepositoryEntryParser.firstString(object, "name");
            if (childPath == null) {
                continue;
            }
            childPath = childPath.replace('\\', '/').replaceAll("^/+", "");
            if (childPath.isBlank() || !childPath.startsWith(prefix)) {
                continue;
            }
            String name = RepositoryEntryParser.fileNameOnly(childPath);
            if (!RepositoryEntryParser.isImportFile(name)) {
                continue;
            }
            long size = object.has("size") && object.get("size").isJsonPrimitive() ? object.get("size").getAsLong() : -1L;
            String rawUrl = "https://cdn.jsdelivr.net/gh/" + RepositoryEntryParser.encPath(owner) + "/" + RepositoryEntryParser.encPath(repo) + "@" + RepositoryEntryParser.encPath(branch) + "/" + RepositoryEntryParser.encPath(childPath);
            entries.add(new ModelRepoEntry(name, rawUrl, name, size, "jsDelivr: " + owner + "/" + repo, owner, repo, branch, childPath));
        }
        HttpTransport.monitor(config, "jsDelivr flat listed owner={} repo={} branch={} path={} files={} entriesAdded={}",
                owner, repo, branch, normalizedPath, files.size(), entries.size() - before);
    }
}
