package com.wangning.counter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 计数 Kafka 聚合链路配置，对应 {@code counter.events.*}。
 *
 * <p>默认关闭，以便没有启动 Kafka 的本地环境仍能使用位图状态能力。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "counter.events")
public class CounterEventProperties {

    /** 是否启用 Kafka 计数事件生产和消费链路。 */
    private boolean enabled;

    /** 互动计数事件主题。 */
    @NotBlank
    private String topic = "counter-events";

    /** 聚合消费者组。 */
    @NotBlank
    private String consumerGroup = "counter-aggregation";

    /** 已聚合计数事件的 Redis 去重键保留时长。 */
    @NotNull
    private Duration dedupTtl = Duration.ofDays(7);

    /** 聚合桶的定时刷写间隔。 */
    @NotNull
    private Duration flushInterval = Duration.ofSeconds(1);

    /** 单次扫描最多处理的聚合桶数量。 */
    @Min(1)
    private int flushBatchSize = 100;
}
