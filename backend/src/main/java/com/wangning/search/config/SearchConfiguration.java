package com.wangning.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 官方 Java Client 配置。
 *
 * <p>仅在 {@code search.enabled=true} 时创建客户端。客户端关闭时会一并释放底层 HTTP 连接池。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class SearchConfiguration {

    /**
     * 创建 Elasticsearch 官方 Java Client。
     *
     * @param properties 已绑定的搜索配置
     * @param objectMapper Spring 配置的 JSON 映射器，包含 Java Time 支持
     * @return Elasticsearch 客户端
     */
    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient(SearchProperties properties, ObjectMapper objectMapper) {
        RestClient restClient = RestClient.builder(HttpHost.create(properties.getUri())).build();
        ObjectMapper elasticsearchObjectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(elasticsearchObjectMapper)
        );
        return new ElasticsearchClient(transport);
    }
}
