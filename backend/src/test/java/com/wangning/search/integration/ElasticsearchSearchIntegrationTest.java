package com.wangning.search.integration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.search.config.SearchProperties;
import com.wangning.search.index.KnowPostSearchDocument;
import com.wangning.search.index.KnowPostSearchDocumentFactory;
import com.wangning.search.index.SearchIndexInitializer;
import com.wangning.search.index.SearchIndexRebuilder;
import com.wangning.search.index.SearchIndexService;
import com.wangning.search.service.ElasticsearchSearchService;
import com.wangning.search.service.MysqlSearchFallbackService;
import com.wangning.search.service.SearchCursorCodec;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 使用带 IK 插件的项目 Elasticsearch 镜像验证搜索完整链路。
 *
 * <p>测试依赖本地已经构建的 {@code zhiguang-elasticsearch:9.2.1} 镜像。CI 在 Maven 验证前构建该镜像，
 * 本地可执行 {@code docker compose build elasticsearch} 后运行本测试。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class ElasticsearchSearchIntegrationTest {

    private static final DockerImageName ELASTICSEARCH_IMAGE = DockerImageName
            .parse("zhiguang-elasticsearch:9.2.1")
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");

    @Container
    private static final GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(ELASTICSEARCH_IMAGE)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=yellow&timeout=1s")
                    .forPort(9200)
                    .forStatusCode(200));

    @Mock
    private KnowPostMapper knowPostMapper;

    @Mock
    private KnowPostSearchDocumentFactory documentFactory;

    @Mock
    private CounterService counterService;

    @Mock
    private MysqlSearchFallbackService mysqlSearchFallbackService;

    private RestClientTransport transport;
    private ElasticsearchClient elasticsearchClient;
    private SearchProperties properties;

    /**
     * 创建 Elasticsearch 客户端和独立的测试索引配置。
     */
    @BeforeAll
    void setUpClient() {
        RestClient restClient = RestClient.builder(HttpHost.create(
                "http://" + ELASTICSEARCH.getHost() + ':' + ELASTICSEARCH.getMappedPort(9200)
        )).build();
        ObjectMapper elasticsearchObjectMapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper(elasticsearchObjectMapper));
        elasticsearchClient = new ElasticsearchClient(transport);
        properties = new SearchProperties();
        properties.setIndexName("zhiguang_knowpost_it_v1");
        properties.setIndexAlias("zhiguang_knowpost_it");
    }

    /**
     * 删除测试索引并关闭 HTTP 传输。
     *
     * @throws Exception 清理 Elasticsearch 资源失败时抛出
     */
    @AfterAll
    void tearDown() throws Exception {
        elasticsearchClient.indices().delete(request -> request
                .index(properties.getIndexName())
                .ignoreUnavailable(true));
        transport.close();
    }

    @Test
    void shouldCreateIkIndexRebuildDocumentsAndServeSearch() throws Exception {
        SearchIndexInitializer initializer = new SearchIndexInitializer(elasticsearchClient, properties);
        initializer.ensureIndex();
        assertThat(elasticsearchClient.indices().exists(request -> request.index(properties.getIndexName())).value())
                .isTrue();
        assertThat(elasticsearchClient.indices().analyze(request -> request
                .index(properties.getIndexName())
                .analyzer("ik_max_word")
                .text("人工智能基础"))
                .tokens())
                .isNotEmpty();

        KnowPostFeedRow feedRow = new KnowPostFeedRow();
        feedRow.setId(100L);
        KnowPostDetailRow detailRow = new KnowPostDetailRow();
        detailRow.setId(100L);
        detailRow.setStatus("published");
        detailRow.setVisible("public");
        KnowPostSearchDocument document = new KnowPostSearchDocument(
                100L,
                "Java 并发基础",
                "线程与锁的入门知识",
                "Java 并发编程实践",
                List.of("Java", "并发"),
                1L,
                null,
                "作者",
                "[]",
                List.of(),
                false,
                Instant.parse("2026-08-24T00:00:00Z"),
                "published",
                "Java 并发基础"
        );
        when(knowPostMapper.listFeedPublic(500, 0)).thenReturn(List.of(feedRow));
        when(knowPostMapper.findDetailById(100L)).thenReturn(detailRow);
        when(documentFactory.isSearchable(detailRow)).thenReturn(true);
        when(documentFactory.create(detailRow)).thenReturn(document);

        SearchIndexService indexService = new SearchIndexService(
                elasticsearchClient,
                properties,
                initializer,
                knowPostMapper,
                documentFactory
        );
        new SearchIndexRebuilder(elasticsearchClient, properties, initializer, indexService, knowPostMapper).rebuild();

        CountResponse count = elasticsearchClient.count(request -> request.index(properties.getIndexAlias()));
        assertThat(count.count()).isEqualTo(1L);
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 3L, "fav", 2L));
        ElasticsearchSearchService searchService = new ElasticsearchSearchService(
                elasticsearchClient,
                properties,
                counterService,
                mysqlSearchFallbackService,
                new SearchCursorCodec(new ObjectMapper())
        );

        var searchResponse = searchService.search("并发", 20, "Java", null, null);
        var suggestResponse = searchService.suggest("Java", 10);

        assertThat(searchResponse.items()).extracting(item -> item.id()).containsExactly("100");
        assertThat(searchResponse.items().getFirst().likeCount()).isEqualTo(3L);
        assertThat(suggestResponse.items()).contains("Java 并发基础");
    }
}
