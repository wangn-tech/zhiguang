package com.wangning.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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
     * @return Elasticsearch 客户端
     */
    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient(SearchProperties properties) {
        RestClient restClient = RestClient.builder(HttpHost.create(properties.getUri())).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
