package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Associates repository image files with models without issuing speculative HTTP requests. */
public final class RepositoryPreviews {
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".webp");

    private RepositoryPreviews() {}

    public static boolean isImage(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static void attach(List<ModelRepoEntry> entries, int first, Map<String, String> images) {
        Map<String, String> normalized = new HashMap<>();
        images.forEach((path, url) -> normalized.putIfAbsent(path.toLowerCase(Locale.ROOT), url));
        Map<String, Integer> modelsPerFolder = new HashMap<>();
        for (int i = first; i < entries.size(); i++)
            modelsPerFolder.merge(folder(entries.get(i).githubPath()), 1, Integer::sum);
        for (int i = first; i < entries.size(); i++) {
            ModelRepoEntry entry = entries.get(i);
            if (entry.previewUrl() != null && !entry.previewUrl().isBlank()) continue;
            String path = entry.githubPath(), folder = folder(path);
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int extension = fileName.lastIndexOf('.');
            String stem = extension < 0 ? fileName : fileName.substring(0, extension);
            String preview = find(normalized, folder + stem, folder + stem + ".preview",
                    folder + stem + ".thumbnail", folder + "previews/" + stem,
                    folder + "images/" + stem, folder + stem + "/preview", folder + stem + "/cover");
            // A generic cover beside several models is ambiguous; do not show the wrong model.
            if (preview.isBlank() && modelsPerFolder.getOrDefault(folder, 0) == 1)
                preview = find(normalized, folder + "preview", folder + "thumbnail", folder + "cover");
            if (!preview.isBlank()) entries.set(i, new ModelRepoEntry(entry.name(), entry.url(),
                    entry.fileName(), entry.size(), entry.description(), entry.githubOwner(),
                    entry.githubRepo(), entry.githubBranch(), entry.githubPath(), entry.author(),
                    entry.tags(), preview, entry.mirrors()));
        }
    }

    private static String find(Map<String, String> images, String... stems) {
        for (String stem : stems) for (String extension : EXTENSIONS) {
            String url = images.get((stem + extension).toLowerCase(Locale.ROOT));
            if (url != null) return url;
        }
        return "";
    }

    private static String folder(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }
}
