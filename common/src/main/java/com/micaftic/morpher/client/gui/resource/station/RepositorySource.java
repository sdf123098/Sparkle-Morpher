package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.net.URI;
import java.util.List;

/**
 * A repository listing strategy (R1.2.x §24.4 Resource Station split).
 *
 * <p>{@code ModelRepoClient.list} routes through the ordered source list, preserving
 * the legacy decision order: GitHub repository host first, index.json as the terminal
 * fallback.
 */
public interface RepositorySource {
    boolean supports(URI uri, String sourceUrl);

    List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception;
}
