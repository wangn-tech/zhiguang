package com.wangning.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wangning.search.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 创建并维护知文搜索索引 Mapping 与读写别名。
 *
 * <p>索引创建失败会向调用者抛出异常，禁止吞掉 IK 缺失、ES 不可用或 Mapping 不兼容等部署错误。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class SearchIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchProperties properties;

    /**
     * 确保物理索引和读写别名存在。
     *
     * @throws IOException Elasticsearch 不可用或索引创建失败时抛出
     */
    public void ensureIndex() throws IOException {
        if (!elasticsearchClient.indices().exists(request -> request.index(properties.getIndexName())).value()) {
            elasticsearchClient.indices().create(request -> request
                    .index(properties.getIndexName())
                    .mappings(mapping -> mapping
                            .properties("id", property -> property.long_(builder -> builder))
                            .properties("title", property -> property.text(builder -> builder
                                    .analyzer("ik_max_word")
                                    .searchAnalyzer("ik_smart")
                                    .fields("keyword", field -> field.keyword(keyword -> keyword.ignoreAbove(256)))
                            ))
                            .properties("description", property -> property.text(builder -> builder
                                    .analyzer("ik_max_word")
                                    .searchAnalyzer("ik_smart")
                            ))
                            .properties("body", property -> property.text(builder -> builder
                                    .analyzer("ik_max_word")
                                    .searchAnalyzer("ik_smart")
                            ))
                            .properties("tags", property -> property.keyword(builder -> builder))
                            .properties("authorId", property -> property.long_(builder -> builder))
                            .properties("authorAvatar", property -> property.keyword(builder -> builder))
                            .properties("authorNickname", property -> property.keyword(builder -> builder))
                            .properties("authorTagJson", property -> property.keyword(builder -> builder))
                            .properties("imgUrls", property -> property.keyword(builder -> builder))
                            .properties("isTop", property -> property.boolean_(builder -> builder))
                            .properties("publishTime", property -> property.date(builder -> builder))
                            .properties("status", property -> property.keyword(builder -> builder))
                            .properties("titleSuggest", property -> property.completion(builder -> builder))
                    )
            );
        }
        elasticsearchClient.indices().putAlias(request -> request
                .index(properties.getIndexName())
                .name(properties.getIndexAlias())
                .isWriteIndex(true)
        );
    }
}
