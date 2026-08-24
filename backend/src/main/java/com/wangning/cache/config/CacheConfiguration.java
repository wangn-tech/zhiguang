package com.wangning.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知文 Caffeine 一级缓存 Bean 配置。
 *
 * <p>一级缓存保存与 Redis 二级缓存相同的 JSON 快照文本，以避免缓存层依赖知文 DTO 的具体实现。
 * 用户态和实时互动计数不会写入这些共享快照。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CacheConfiguration {

    /**
     * 创建知文详情一级缓存。
     *
     * @param properties 已绑定的缓存配置
     * @return 缓存键到 JSON 快照的映射
     */
    @Bean("knowPostDetailLocalCache")
    public Cache<String, String> knowPostDetailLocalCache(CacheProperties properties) {
        CacheProperties.L1 l1 = properties.getL1();
        return Caffeine.newBuilder()
                .maximumSize(l1.getDetailMaxSize())
                .expireAfterWrite(l1.getDetailTtl())
                .build();
    }

    /**
     * 创建公共 Feed 一级缓存。
     *
     * @param properties 已绑定的缓存配置
     * @return 缓存键到 JSON 快照的映射
     */
    @Bean("publicFeedLocalCache")
    public Cache<String, String> publicFeedLocalCache(CacheProperties properties) {
        CacheProperties.L1 l1 = properties.getL1();
        return Caffeine.newBuilder()
                .maximumSize(l1.getPublicFeedMaxSize())
                .expireAfterWrite(l1.getPublicFeedTtl())
                .build();
    }

    /**
     * 创建作者 Feed 一级缓存。
     *
     * @param properties 已绑定的缓存配置
     * @return 缓存键到 JSON 快照的映射
     */
    @Bean("mineFeedLocalCache")
    public Cache<String, String> mineFeedLocalCache(CacheProperties properties) {
        CacheProperties.L1 l1 = properties.getL1();
        return Caffeine.newBuilder()
                .maximumSize(l1.getMineFeedMaxSize())
                .expireAfterWrite(l1.getMineFeedTtl())
                .build();
    }
}
