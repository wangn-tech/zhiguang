package com.wangning.cache.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.cache.config.CacheProperties;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.model.FeedPageSnapshot;
import com.wangning.cache.service.KnowPostFeedCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 基于 Caffeine、Redis 和页面键反向索引的 Feed 两级缓存。
 *
 * <p>公共 Feed 与作者 Feed 分别使用独立的一级缓存和 Redis 索引集合。失效时通过 {@code SSCAN}
 * 枚举索引，避免使用会阻塞 Redis 的 {@code KEYS} 命令。</p>
 */
@Slf4j
@Service
public class RedisKnowPostFeedCacheService implements KnowPostFeedCacheService {

    private static final long SCAN_BATCH_SIZE = 100L;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;
    private final Cache<String, String> publicLocalCache;
    private final Cache<String, String> mineLocalCache;

    /**
     * 创建 Feed 两级缓存服务。
     *
     * @param objectMapper JSON 序列化器
     * @param redisTemplate Redis 客户端
     * @param properties 已绑定的缓存配置
     * @param publicLocalCache 公共 Feed Caffeine 一级缓存
     * @param mineLocalCache 作者 Feed Caffeine 一级缓存
     */
    public RedisKnowPostFeedCacheService(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            CacheProperties properties,
            @Qualifier("publicFeedLocalCache") Cache<String, String> publicLocalCache,
            @Qualifier("mineFeedLocalCache") Cache<String, String> mineLocalCache
    ) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.publicLocalCache = publicLocalCache;
        this.mineLocalCache = mineLocalCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<FeedPageSnapshot> findPublic(int page, int size) {
        return find(CacheKeys.publicFeedKey(page, size), publicLocalCache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putPublic(FeedPageSnapshot snapshot) {
        put(
                CacheKeys.publicFeedKey(snapshot.page(), snapshot.size()),
                CacheKeys.publicFeedIndexKey(),
                snapshot,
                properties.getL2().getPublicFeedTtl(),
                publicLocalCache
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<FeedPageSnapshot> findMine(long creatorId, int page, int size) {
        return find(CacheKeys.mineFeedKey(creatorId, page, size), mineLocalCache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putMine(long creatorId, FeedPageSnapshot snapshot) {
        put(
                CacheKeys.mineFeedKey(creatorId, snapshot.page(), snapshot.size()),
                CacheKeys.mineFeedIndexKey(creatorId),
                snapshot,
                properties.getL2().getMineFeedTtl(),
                mineLocalCache
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidatePublic() {
        publicLocalCache.invalidateAll();
        invalidateRedisPages(CacheKeys.publicFeedIndexKey());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateMine(long creatorId) {
        String prefix = "cache:kp:feed:mine:" + creatorId + ':';
        mineLocalCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        invalidateRedisPages(CacheKeys.mineFeedIndexKey(creatorId));
    }

    /**
     * 查询一个页面快照，并在 Redis 命中后回填一级缓存。
     *
     * @param key 完整页面缓存键
     * @param localCache 对应的 Caffeine 一级缓存
     * @return 有效页面快照；未命中、损坏或 Redis 故障时为空
     */
    private Optional<FeedPageSnapshot> find(String key, Cache<String, String> localCache) {
        Optional<FeedPageSnapshot> local = readSnapshot(key, localCache.getIfPresent(key), localCache);
        if (local.isPresent()) {
            return local;
        }

        try {
            String cached = redisTemplate.opsForValue().get(key);
            Optional<FeedPageSnapshot> snapshot = readSnapshot(key, cached, localCache);
            snapshot.ifPresent(value -> localCache.put(key, cached));
            return snapshot;
        } catch (DataAccessException exception) {
            log.warn("读取 Feed Redis 缓存失败，降级回源：key={}", key, exception);
            return Optional.empty();
        }
    }

    /**
     * 写入一页 Redis 快照及其反向索引，并写入一级缓存。
     *
     * @param key 完整页面缓存键
     * @param indexKey 页面键反向索引
     * @param snapshot 待缓存页面
     * @param ttl Redis 缓存 TTL
     * @param localCache 对应的 Caffeine 一级缓存
     */
    private void put(
            String key,
            String indexKey,
            FeedPageSnapshot snapshot,
            Duration ttl,
            Cache<String, String> localCache
    ) {
        if (snapshot.items() == null || snapshot.items().isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            localCache.put(key, json);
            try {
                redisTemplate.opsForValue().set(key, json, ttl);
                SetOperations<String, String> setOperations = redisTemplate.opsForSet();
                setOperations.add(indexKey, key);
                redisTemplate.expire(indexKey, ttl);
            } catch (DataAccessException exception) {
                log.warn("写入 Feed Redis 缓存失败，仅保留本地缓存：key={}", key, exception);
            }
        } catch (JsonProcessingException exception) {
            log.warn("序列化 Feed 缓存快照失败，忽略缓存写入：key={}", key, exception);
        }
    }

    /**
     * 通过 Redis Set 反向索引分批删除已缓存的页面。
     *
     * @param indexKey 页面键反向索引
     */
    private void invalidateRedisPages(String indexKey) {
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(
                indexKey,
                ScanOptions.scanOptions().count(SCAN_BATCH_SIZE).build()
        )) {
            List<String> batch = new ArrayList<>();
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    deletePageBatch(batch);
                    batch.clear();
                }
            }
            deletePageBatch(batch);
            redisTemplate.delete(indexKey);
        } catch (DataAccessException exception) {
            log.warn("失效 Feed Redis 缓存失败：indexKey={}", indexKey, exception);
        }
    }

    /**
     * 删除一个页面键批次。
     *
     * @param keys 待删除页面键
     */
    private void deletePageBatch(Collection<String> keys) {
        if (!keys.isEmpty()) {
            redisTemplate.delete(List.copyOf(keys));
        }
    }

    /**
     * 将页面 JSON 转换为快照；格式错误时删除该缓存键。
     *
     * @param key 完整页面缓存键
     * @param json 缓存 JSON
     * @param localCache 对应的 Caffeine 一级缓存
     * @return 有效快照；空值或格式错误时为空
     */
    private Optional<FeedPageSnapshot> readSnapshot(String key, String json, Cache<String, String> localCache) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, FeedPageSnapshot.class));
        } catch (JsonProcessingException exception) {
            log.warn("Feed 缓存格式错误，删除损坏缓存：key={}, reason={}", key, exception.getOriginalMessage());
            localCache.invalidate(key);
            try {
                redisTemplate.delete(key);
            } catch (DataAccessException deleteException) {
                log.warn("删除损坏的 Feed Redis 缓存失败：key={}", key, deleteException);
            }
            return Optional.empty();
        }
    }
}
