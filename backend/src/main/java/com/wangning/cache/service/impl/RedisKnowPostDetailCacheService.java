package com.wangning.cache.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.cache.config.CacheProperties;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.model.KnowPostDetailSnapshot;
import com.wangning.cache.service.KnowPostDetailCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 基于 Caffeine 和 Redis 的公开知文详情两级缓存。
 *
 * <p>缓存读写异常会降级为未命中，确保 Redis 故障不会阻断知文详情的数据库回源。反序列化失败的
 * 缓存项会被删除，防止损坏数据反复命中。</p>
 */
@Slf4j
@Service
public class RedisKnowPostDetailCacheService implements KnowPostDetailCacheService {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;

    private final Cache<String, String> localCache;

    /**
     * 创建详情两级缓存服务。
     *
     * @param objectMapper JSON 序列化器
     * @param redisTemplate Redis 客户端
     * @param properties 已绑定的缓存配置
     * @param localCache 知文详情 Caffeine 一级缓存
     */
    public RedisKnowPostDetailCacheService(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            CacheProperties properties,
            @Qualifier("knowPostDetailLocalCache") Cache<String, String> localCache
    ) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.localCache = localCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KnowPostDetailSnapshot> find(long knowPostId) {
        String key = CacheKeys.detailKey(knowPostId);
        Optional<KnowPostDetailSnapshot> local = readSnapshot(key, localCache.getIfPresent(key));
        if (local.isPresent()) {
            return local;
        }

        try {
            String cached = redisTemplate.opsForValue().get(key);
            Optional<KnowPostDetailSnapshot> snapshot = readSnapshot(key, cached);
            snapshot.ifPresent(value -> localCache.put(key, cached));
            return snapshot;
        } catch (DataAccessException exception) {
            log.warn("读取知文详情 Redis 缓存失败，降级回源：key={}", key, exception);
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(KnowPostDetailSnapshot snapshot) {
        String key = CacheKeys.detailKey(parseId(snapshot.id()));
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            localCache.put(key, json);
            try {
                redisTemplate.opsForValue().set(key, json, properties.getL2().getDetailTtl());
            } catch (DataAccessException exception) {
                log.warn("写入知文详情 Redis 缓存失败，仅保留本地缓存：key={}", key, exception);
            }
        } catch (JsonProcessingException exception) {
            log.warn("序列化知文详情缓存快照失败，忽略缓存写入：key={}", key, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate(long knowPostId) {
        String key = CacheKeys.detailKey(knowPostId);
        localCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn("删除知文详情 Redis 缓存失败：key={}", key, exception);
        }
    }

    /**
     * 将缓存 JSON 转换为快照；格式错误时删除损坏的两级缓存项。
     *
     * @param key 缓存键
     * @param json 缓存 JSON
     * @return 有效快照；空值或格式错误时为空
     */
    private Optional<KnowPostDetailSnapshot> readSnapshot(String key, String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, KnowPostDetailSnapshot.class));
        } catch (JsonProcessingException exception) {
            log.warn("知文详情缓存格式错误，删除损坏缓存：key={}, reason={}", key, exception.getOriginalMessage());
            invalidateByKey(key);
            return Optional.empty();
        }
    }

    /**
     * 根据完整缓存键删除两级缓存项。
     *
     * @param key 已校验的详情缓存键
     */
    private void invalidateByKey(String key) {
        localCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn("删除损坏的知文详情 Redis 缓存失败：key={}", key, exception);
        }
    }

    /**
     * 将快照中的字符串 ID 转换为正整数，防止错误快照生成异常 Redis 键。
     *
     * @param id 字符串形式的知文 ID
     * @return 正整数知文 ID
     * @throws IllegalArgumentException ID 缺失、格式无效或非正数时抛出
     */
    private long parseId(String id) {
        try {
            long value = Long.parseLong(id);
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("缓存快照的知文 ID 非法", exception);
        }
    }
}
