package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * index.json listing source (R1.2.x §24.4).
 *
 * <p>Terminal route: everything that is not a GitHub repository host is listed from
 * its {@code index.json}, exactly as the legacy {@code list} implementation did.
 */
public final class IndexJsonSource implements RepositorySource {
    @Override
    public boolean supports(URI uri, String sourceUrl) {
        return true;
    }

    @Override
    public List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception {
        String indexUrl = sourceUrl.endsWith("/") ? sourceUrl + "index.json" : sourceUrl;
        if (!indexUrl.endsWith("index.json")) {
            indexUrl = indexUrl + "/index.json";
        }
        HttpTransport.monitor(config, "List index source={} indexUrl={}", sourceUrl, indexUrl);
        List<ModelRepoEntry> entries = RepositoryEntryParser.parseIndex(indexUrl, new String(HttpTransport.read(indexUrl, config.timeoutMs(), 2 * 1024 * 1024, config, "index"), StandardCharsets.UTF_8), config);
        HttpTransport.monitor(config, "List index complete indexUrl={} entries={}", indexUrl, entries.size());
        return entries;
    }
}
