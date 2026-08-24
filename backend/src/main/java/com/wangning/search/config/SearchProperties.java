package com.wangning.search.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Elasticsearch 搜索模块配置，对应 {@code search.*}。
 *
 * <p>搜索默认关闭，使未启动 Elasticsearch 的本地环境仍可运行用户、认证和知文等基础功能。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    /** 是否启用 Elasticsearch 客户端、索引同步消费者和搜索接口。 */
    private boolean enabled;

    /** Elasticsearch HTTP 地址。 */
    @NotBlank
    private String uri = "http://localhost:9200";

    /** 搜索读别名。 */
    @NotBlank
    private String indexAlias = "zhiguang_knowpost";

    /** 当前 Mapping 版本对应的物理索引名。 */
    @NotBlank
    private String indexName = "zhiguang_knowpost_v1";

    /** 是否在应用启动时执行一次全量索引回灌。 */
    private boolean rebuildOnStartup;

    /** 全量回灌时每页读取和每批写入的最大文档数。 */
    @Min(1)
    private int rebuildBatchSize = 500;

    /** 单篇正文建立索引时最多读取的字节数。 */
    @Min(1)
    private int maxBodyBytes = 65_536;
}
