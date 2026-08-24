package com.wangning.counter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 实体互动计数 SDS 恢复配置，对应 {@code counter.recovery.*}。
 *
 * <p>恢复以 Redis 位图为事实来源。Redisson 只用于协调恢复任务，不参与正常的点赞、收藏写入。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "counter.recovery")
public class CounterRecoveryProperties {

    /** 是否在实体 SDS 缺失或损坏时尝试从位图恢复。 */
    private boolean enabled = true;

    /** Redisson 锁看门狗时长。 */
    @NotNull
    private Duration lockWatchdog = Duration.ofSeconds(30);

    /** 单个实体在限流窗口内允许的最多恢复次数。 */
    @Min(1)
    private int ratePermits = 3;

    /** 恢复限流窗口。 */
    @NotNull
    private Duration rateWindow = Duration.ofSeconds(10);

    /** 不活跃恢复限流器的最长保留时间。 */
    @NotNull
    private Duration rateLimiterIdle = Duration.ofHours(1);

    /** 指数退避的初始时长。 */
    @NotNull
    private Duration backoffBase = Duration.ofMillis(500);

    /** 指数退避的最大时长。 */
    @NotNull
    private Duration backoffMax = Duration.ofSeconds(30);
}
