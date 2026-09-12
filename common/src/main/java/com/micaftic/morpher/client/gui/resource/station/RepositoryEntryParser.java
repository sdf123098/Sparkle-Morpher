package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * index.json entry parsing plus the shared string / URL helpers (R1.2.x §24.4).
 *
 * <p>Verbatim move of {@code parseIndex}, the string helpers, the hashing / encoding
 * helpers, {@code HREF_PATTERN} and the {@code GithubPath} record.
 */
public final class RepositoryEntryParser {
    public static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]+)\"");

    private RepositoryEntryParser() {
    }

    public static List<ModelRepoEntry> parseIndex(String indexUrl, String json, ResourceStationConfig.State config) {
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
        HttpTransport.monitor(config, "Parsed index indexUrl={} rawItems={} importableEntries={}", indexUrl, array.size(), entries.size());
        return entries;
    }

    public static boolean isImportFile(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ysm") || lower.endsWith(".zip") || lower.endsWith(".bbmodel");
    }

    public static String firstString(JsonObject object, String... names) {
        for (String name : names) {
            if (object.has(name) && !object.get(name).isJsonNull()) {
                return object.get(name).getAsString();
            }
        }
        return null;
    }

    public static String tagsString(JsonObject object) {
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

    public static List<String> urlList(JsonObject object, URI base, String... names) {
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

    public static void addUrlValue(List<String> result, URI base, JsonElement element) {
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

    public static String fileNameFromUrl(String url, String fallback) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? fallback : name;
    }

    public static String fileNameOnly(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static String stripArchiveRoot(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : "";
    }

    public static String stripExtension(String name) {
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

    public static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to hash repo URL", e);
            return Integer.toHexString(value.hashCode());
        }
    }

    public static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String encPath(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.split("/")) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(enc(part));
        }
        return out.toString();
    }

    public record GithubPath(String owner, String repo, String branch, String path) {
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
            String repo = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
            if (repo.isBlank()) {
                throw new IllegalArgumentException("Invalid GitHub URL");
            }
            return new GithubPath(parts[0], repo, branch, path);
        }
    }
}
