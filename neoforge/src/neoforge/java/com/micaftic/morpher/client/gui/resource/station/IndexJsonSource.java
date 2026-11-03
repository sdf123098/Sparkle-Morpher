package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * R1.2.x §24.4 IndexJsonSource（neo26.x 适配版，行为等价的纯搬运）。
 *
 * <p>非 GitHub 仓库来源：把 sourceUrl 归一为 {@code index.json}，用
 * {@link HttpTransport#readFirst(List, int, int, boolean)} 走候选回退读取，
 * 再交给 {@link RepositoryEntryParser#parseIndex} 解析。
 *
 * <p>保持本分支的 {@code indexCandidates} 多候选语义（fa 分支无此路径）。
 */
public final class IndexJsonSource implements RepositorySource {

    @Override
    public boolean supports(URI uri, String sourceUrl) {
        String host = uri == null ? null : uri.getHost();
        return host == null || !("github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host));
    }

    @Override
    public List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception {
        String indexUrl = sourceUrl.endsWith("/") ? sourceUrl + "index.json" : sourceUrl;
        if (!indexUrl.endsWith("index.json")) {
            indexUrl = indexUrl + "/index.json";
        }
        return RepositoryEntryParser.parseIndex(indexUrl,
                new String(HttpTransport.readFirst(DownloadCandidatePlanner.indexCandidates(indexUrl, config),
                        config.timeoutMs(), 2 * 1024 * 1024, true), StandardCharsets.UTF_8));
    }
}
