package com.wangning.relation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 关系 Outbox 异步事件配置，对应 {@code relation.events.*}。
 *
 * <p>默认关闭，避免未启动 Canal、Kafka 的开发环境在应用启动时持续重试连接。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "relation.events")
public class RelationEventProperties {

    /** 是否启用 Canal 桥接和 Kafka 消费者。 */
    private boolean enabled;

    /** Canal 事件转发的 Kafka Topic。 */
    @NotBlank
    private String topic = "canal-outbox";

    /** 关系事件消费者组。 */
    @NotBlank
    private String consumerGroup = "relation-outbox-consumer";

    /** 已处理 Outbox 事件的 Redis 去重键保留时长。 */
    @NotNull
    private Duration dedupTtl = Duration.ofDays(7);

    /** 关注和粉丝 ZSet 缓存的过期时间。 */
    @NotNull
    private Duration cacheTtl = Duration.ofHours(2);

    /** Canal 连接配置。 */
    @Valid
    @NotNull
    private Canal canal = new Canal();

    /**
     * Canal Server 连接配置。
     */
    @Data
    public static class Canal {

        /** Canal Server 主机。 */
        @NotBlank
        private String host = "localhost";

        /** Canal Server TCP 端口。 */
        @Min(1)
        private int port = 11111;

        /** Canal destination 名称。 */
        @NotBlank
        private String destination = "zhiguang";

        /** Canal 用户名。 */
        private String username = "canal";

        /** Canal 密码。 */
        private String password = "canal";

        /** 订阅的库表正则。 */
        @NotBlank
        private String filter = "zhiguang\\.outbox";

        /** 单次拉取的最大条数。 */
        @Min(1)
        private int batchSize = 1000;

        /** 空批次轮询间隔。 */
        @NotNull
        private Duration pollInterval = Duration.ofSeconds(1);
    }
}
