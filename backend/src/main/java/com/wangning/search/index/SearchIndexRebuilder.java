package com.wangning.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.search.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 从 MySQL 全量重建公开知文搜索索引。
 *
 * <p>仅由显式启动开关或运维入口调用。重建会清空当前物理索引后再批量回灌，索引过程失败会抛出异常，
 * 使部署或运维任务获得明确失败信号。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class SearchIndexRebuilder {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchProperties properties;
    private final SearchIndexInitializer searchIndexInitializer;
    private final SearchIndexService searchIndexService;
    private final KnowPostMapper knowPostMapper;

    /**
     * 清空当前物理索引，并从 MySQL 分批回灌所有公开已发布知文。
     *
     * @throws IOException Elasticsearch 不可用、索引清理或回灌失败时抛出
     */
    public void rebuild() throws IOException {
        searchIndexInitializer.ensureIndex();
        elasticsearchClient.deleteByQuery(request -> request
                .index(properties.getIndexName())
                .query(query -> query.matchAll(matchAll -> matchAll))
        );

        int offset = 0;
        int batchSize = properties.getRebuildBatchSize();
        while (true) {
            List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(batchSize, offset);
            List<KnowPostFeedRow> safeRows = rows == null ? Collections.emptyList() : rows;
            if (safeRows.isEmpty()) {
                break;
            }
            searchIndexService.upsertKnowPosts(safeRows.stream()
                    .map(KnowPostFeedRow::getId)
                    .toList());
            offset += safeRows.size();
            if (safeRows.size() < batchSize) {
                break;
            }
        }
        elasticsearchClient.indices().refresh(request -> request.index(properties.getIndexName()));
    }
}
