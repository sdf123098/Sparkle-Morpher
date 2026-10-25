package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryPreviewsTest {
    private static ModelRepoEntry model(String path) {
        return new ModelRepoEntry(path, "https://models/" + path, path, 42, "", "owner", "repo", "main", path);
    }

    @Test void findsUnicodeSidecarAndKeepsDownloadMetadata() {
        var entries = new ArrayList<>(List.of(model("人物/流萤.ysm")));
        RepositoryPreviews.attach(entries, 0, Map.of("人物/流萤.PNG", "https://image"));
        assertEquals("https://image", entries.getFirst().previewUrl());
        assertEquals("人物/流萤.ysm", entries.getFirst().githubPath());
        assertEquals(42, entries.getFirst().size());
    }

    @Test void exactSidecarWinsOverGenericCover() {
        var entries = new ArrayList<>(List.of(model("alice.zip")));
        RepositoryPreviews.attach(entries, 0, Map.of("alice.jpg", "specific", "cover.png", "generic"));
        assertEquals("specific", entries.getFirst().previewUrl());
    }

    @Test void genericCoverIsOnlyUsedForAnUnambiguousModelDirectory() {
        var entries = new ArrayList<>(List.of(model("a/alice.zip"), model("b/bob.ysm"), model("b/charlie.ysm")));
        RepositoryPreviews.attach(entries, 0, Map.of("a/preview.png", "alice", "b/cover.png", "ambiguous"));
        assertEquals("alice", entries.get(0).previewUrl());
        assertEquals("", entries.get(1).previewUrl());
        assertEquals("", entries.get(2).previewUrl());
    }

    @Test void supportsPreviewDirectoriesAndModelSpecificDirectories() {
        var entries = new ArrayList<>(List.of(model("pack/alice.bbmodel"), model("pack/bob.ysm")));
        RepositoryPreviews.attach(entries, 0, Map.of("pack/previews/alice.webp", "alice", "pack/bob/cover.png", "bob"));
        assertEquals("alice", entries.get(0).previewUrl());
        assertEquals("bob", entries.get(1).previewUrl());
    }

    @Test void preservesExplicitPreviewAndPreviouslyListedEntries() {
        var earlier = model("earlier.ysm");
        var explicit = new ModelRepoEntry("alice", "download", "alice.ysm", 1, "", "o", "r", "main", "alice.ysm", "", "", "explicit");
        var entries = new ArrayList<>(List.of(earlier, explicit));
        RepositoryPreviews.attach(entries, 1, Map.of("earlier.png", "old", "alice.png", "replacement"));
        assertEquals("", entries.getFirst().previewUrl());
        assertEquals("explicit", entries.get(1).previewUrl());
    }

    @Test void missingCoverDoesNotGuessANonexistentUrl() {
        var entries = new ArrayList<>(List.of(model("alice.ysm")));
        RepositoryPreviews.attach(entries, 0, Map.of("other.png", "other"));
        assertEquals("", entries.getFirst().previewUrl());
    }
}
