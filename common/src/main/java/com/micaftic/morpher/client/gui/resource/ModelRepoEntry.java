package com.micaftic.morpher.client.gui.resource;

import java.util.List;

public record ModelRepoEntry(String name, String url, String fileName, long size, String description,
                             String githubOwner, String githubRepo, String githubBranch, String githubPath,
                             String author, String tags, String previewUrl, List<String> mirrors) {
    public ModelRepoEntry {
        mirrors = mirrors == null ? List.of() : List.copyOf(mirrors);
    }

    public ModelRepoEntry(String name, String url, String fileName, long size, String description) {
        this(name, url, fileName, size, description, "", "", "", "", "", "", "");
    }

    public ModelRepoEntry(String name, String url, String fileName, long size, String description,
                          String githubOwner, String githubRepo, String githubBranch, String githubPath) {
        this(name, url, fileName, size, description, githubOwner, githubRepo, githubBranch, githubPath, "", "", "");
    }

    public ModelRepoEntry(String name, String url, String fileName, long size, String description,
                          String githubOwner, String githubRepo, String githubBranch, String githubPath,
                          String author, String tags, String previewUrl) {
        this(name, url, fileName, size, description, githubOwner, githubRepo, githubBranch, githubPath, author, tags, previewUrl, List.of());
    }

    public boolean isGithubFile() {
        return !githubOwner.isBlank() && !githubRepo.isBlank() && !githubBranch.isBlank() && !githubPath.isBlank();
    }
}
