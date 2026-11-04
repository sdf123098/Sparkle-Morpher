package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;

import java.net.URI;
import java.util.List;

/**
 * R1.2.x §24.4 RepositorySource（neo26.x 适配版）。
 *
 * <p>仓库来源策略接口。路由顺序在 {@code ModelRepoClient.list} 中保持既有语义：
 * GitHub 仓库 host 优先，其余落到 index.json。
 */
public interface RepositorySource {

    /** 该来源是否处理此 URL。 */
    boolean supports(URI uri, String sourceUrl);

    /** 列出该来源下的可导入模型条目。 */
    List<ModelRepoEntry> list(String sourceUrl, URI uri, ResourceStationConfig.State config) throws Exception;
}
