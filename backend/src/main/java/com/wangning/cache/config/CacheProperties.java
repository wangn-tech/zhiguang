package com.wangning.cache.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 知文两级缓存配置，对应 {@code cache.*}。
 *
 * <p>L1 是单个应用实例内的 Caffeine，L2 是全部实例共享的 Redis。两层 TTL 独立配置，
 * L1 必须短于 L2，避免本地进程在 Redis 已失效后长期返回陈旧快照。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    /** 进程内 Caffeine 一级缓存配置。 */
    @Valid
    @NotNull
    private L1 l1 = new L1();

    /** Redis 二级缓存配置。 */
    @Valid
    @NotNull
    private L2 l2 = new L2();

    /** Caffeine 缓存的容量和过期时间。 */
    @Data
    public static class L1 {

        /** 知文详情快照 TTL。 */
        @NotNull
        private Duration detailTtl = Duration.ofSeconds(30);

        /** 知文详情快照最大数量。 */
        @Min(1)
        private long detailMaxSize = 5_000;

        /** 公共 Feed 页面 TTL。 */
        @NotNull
        private Duration publicFeedTtl = Duration.ofSeconds(15);

        /** 公共 Feed 页面最大数量。 */
        @Min(1)
        private long publicFeedMaxSize = 1_000;

        /** 作者 Feed 页面 TTL。 */
        @NotNull
        private Duration mineFeedTtl = Duration.ofSeconds(10);

        /** 作者 Feed 页面最大数量。 */
        @Min(1)
        private long mineFeedMaxSize = 1_000;
    }

    /** Redis 缓存的过期时间。 */
    @Data
    public static class L2 {

        /** 知文详情快照 TTL。 */
        @NotNull
        private Duration detailTtl = Duration.ofMinutes(10);

        /** 公共 Feed 页面 TTL。 */
        @NotNull
        private Duration publicFeedTtl = Duration.ofMinutes(2);

        /** 作者 Feed 页面 TTL。 */
        @NotNull
        private Duration mineFeedTtl = Duration.ofMinutes(1);
    }
}
