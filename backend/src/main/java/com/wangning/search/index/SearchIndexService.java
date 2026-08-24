package com.wangning.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.search.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 知文搜索索引的单条和批量写入服务。
 *
 * <p>同一知文 ID 始终写入同一 ES 文档，因此 Kafka 至少一次投递或全量回灌重试均具备幂等性。
 * 非公开或不存在的知文会被覆盖为 {@code status=deleted} 的 tombstone 文档。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class SearchIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchProperties properties;
    private final SearchIndexInitializer searchIndexInitializer;
    private final KnowPostMapper knowPostMapper;
    private final KnowPostSearchDocumentFactory documentFactory;

    /**
     * 读取 MySQL 当前事实并写入一篇知文索引文档。
     *
     * @param knowPostId 知文 ID
     * @throws IOException Elasticsearch 不可用或写入失败时抛出
     */
    public void upsertKnowPost(long knowPostId) throws IOException {
        upsertKnowPosts(List.of(knowPostId));
    }

    /**
     * 读取 MySQL 当前事实并批量写入知文索引文档。
     *
     * @param knowPostIds 知文 ID 列表
     * @throws IOException Elasticsearch 不可用、索引初始化失败或批量写入失败时抛出
     */
    public void upsertKnowPosts(List<Long> knowPostIds) throws IOException {
        if (knowPostIds == null || knowPostIds.isEmpty()) {
            return;
        }
        List<KnowPostSearchDocument> documents = new ArrayList<>(knowPostIds.size());
        for (Long knowPostId : knowPostIds) {
            if (knowPostId != null && knowPostId > 0) {
                documents.add(loadDocument(knowPostId));
            }
        }
        if (documents.isEmpty()) {
            return;
        }

        searchIndexInitializer.ensureIndex();
        BulkResponse response = elasticsearchClient.bulk(request -> {
            for (KnowPostSearchDocument document : documents) {
                request.operations(operation -> operation.index(index -> index
                        .index(properties.getIndexAlias())
                        .id(String.valueOf(document.id()))
                        .document(document)
                ));
            }
            return request;
        });
        if (response.errors()) {
            throw new IOException("Elasticsearch 批量写入知文索引失败");
        }
    }

    /**
     * 显式写入一篇软删除索引文档。
     *
     * @param knowPostId 知文 ID
     * @throws IOException Elasticsearch 不可用或写入失败时抛出
     */
    public void softDeleteKnowPost(long knowPostId) throws IOException {
        if (knowPostId <= 0) {
            throw new IllegalArgumentException("knowPostId 必须为正整数");
        }
        searchIndexInitializer.ensureIndex();
        KnowPostSearchDocument document = KnowPostSearchDocument.deleted(knowPostId);
        elasticsearchClient.index(request -> request
                .index(properties.getIndexAlias())
                .id(String.valueOf(knowPostId))
                .document(document)
        );
    }

    /**
     * 按 MySQL 当前数据构建可索引文档；非公开内容统一转为软删除文档。
     *
     * @param knowPostId 知文 ID
     * @return 完整公开文档或软删除文档
     */
    private KnowPostSearchDocument loadDocument(long knowPostId) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(knowPostId);
        if (!documentFactory.isSearchable(row)) {
            return KnowPostSearchDocument.deleted(knowPostId);
        }
        return documentFactory.create(row);
    }
}
