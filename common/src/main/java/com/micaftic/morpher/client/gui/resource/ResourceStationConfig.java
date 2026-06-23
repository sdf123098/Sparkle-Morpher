package com.micaftic.morpher.client.gui.resource;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.architectury.platform.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ResourceStationConfig {
    private static final List<String> DEFAULT_URLS = List.of(
            "https://github.com/Elaina69/Yes-Steve-Model-Repo/tree/main",
            "https://github.com/sdf123098/YSM-Model"
    );
    private static final List<String> DEFAULT_GITHUB_ACCELERATORS = List.of(
            "https://gh-proxy.com/",
            "https://gh.llkk.cc/",
            "https://gh.ddlc.top/"
    );
    private static final Path FILE = Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID).resolve("resource_station.properties");

    private ResourceStationConfig() {
    }

    public static State load() {
        Properties properties = new Properties();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                properties.load(in);
            } catch (IOException e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to load resource station config", e);
            }
        }
        List<String> urls = new ArrayList<>();
        String urlList = properties.getProperty("urls", "");
        for (String url : urlList.split("\\|")) {
            if (!url.isBlank()) {
                addUrl(urls, repairKnownUrl(url.trim()));
            }
        }
        if (urls.isEmpty()) {
            urls.addAll(DEFAULT_URLS);
        } else {
            for (String defaultUrl : DEFAULT_URLS) {
                addUrl(urls, defaultUrl);
            }
        }
        String selected = repairKnownUrl(properties.getProperty("selectedUrl", urls.get(0)).trim());
        if (!urls.contains(selected)) {
            urls.add(0, selected);
        }
        int timeoutMs = parseInt(properties.getProperty("timeoutMs"), 6000);
        int maxDownloadBytes = parseInt(properties.getProperty("maxDownloadBytes"), 64 * 1024 * 1024);
        boolean mainlandChinaMode = Boolean.parseBoolean(properties.getProperty("mainlandChinaMode", properties.getProperty("preferGithubAccelerator", "false")));
        List<String> githubAccelerators = parseList(properties.getProperty("githubAccelerators", ""));
        if (githubAccelerators.isEmpty()) {
            githubAccelerators.addAll(DEFAULT_GITHUB_ACCELERATORS);
        }
        State state = new State(urls, selected, timeoutMs, maxDownloadBytes, mainlandChinaMode, githubAccelerators);
        if (monitorLogEnabled()) {
            YesSteveModel.LOGGER.info("[SM-RESOURCE] Loaded config file={} selected={} urls={} timeoutMs={} maxDownloadBytes={} mainlandChinaMode={} accelerators={}",
                    FILE, state.selectedUrl(), state.urls().size(), state.timeoutMs(), state.maxDownloadBytes(), state.mainlandChinaMode(), state.githubAccelerators().size());
        }
        return state;
    }

    public static void save(State state) {
        try {
            Files.createDirectories(FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("urls", String.join("|", state.urls()));
            properties.setProperty("selectedUrl", state.selectedUrl());
            properties.setProperty("timeoutMs", Integer.toString(state.timeoutMs()));
            properties.setProperty("maxDownloadBytes", Integer.toString(state.maxDownloadBytes()));
            properties.setProperty("mainlandChinaMode", Boolean.toString(state.mainlandChinaMode()));
            properties.setProperty("githubAccelerators", String.join("|", state.githubAccelerators()));
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "OpenYSM resource station");
            }
            if (monitorLogEnabled()) {
                YesSteveModel.LOGGER.info("[SM-RESOURCE] Saved config file={} selected={} urls={} timeoutMs={} maxDownloadBytes={} mainlandChinaMode={} accelerators={}",
                        FILE, state.selectedUrl(), state.urls().size(), state.timeoutMs(), state.maxDownloadBytes(), state.mainlandChinaMode(), state.githubAccelerators().size());
            }
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to save resource station config", e);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        for (String item : value.split("\\|")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private static void addUrl(List<String> urls, String url) {
        if (!url.isBlank() && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private static String repairKnownUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("https://github.com/Elaina69/Yes-")) {
            return "https://github.com/Elaina69/Yes-Steve-Model-Repo/tree/main";
        }
        if (url.startsWith("https://github.com/sdf123098/YSM-")) {
            return "https://github.com/sdf123098/YSM-Model";
        }
        return url;
    }

    public static boolean monitorLogEnabled() {
        return GeneralConfig.safeGet(GeneralConfig.RESOURCE_STATION_MONITOR_LOG, false);
    }

    public record State(List<String> urls, String selectedUrl, int timeoutMs, int maxDownloadBytes,
                        boolean mainlandChinaMode, List<String> githubAccelerators) {

        public State withMainlandChinaMode(boolean mainlandChinaMode) {
            return new State(this.urls, this.selectedUrl, this.timeoutMs, this.maxDownloadBytes, mainlandChinaMode, this.githubAccelerators);
        }

        public State withUrlsAndSelected(List<String> urls, String selectedUrl) {
            return new State(urls, selectedUrl, this.timeoutMs, this.maxDownloadBytes, this.mainlandChinaMode, this.githubAccelerators);
        }
    }
}
