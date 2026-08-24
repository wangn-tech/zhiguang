package com.wangning.counter.schema;

/**
 * 计数模块 Redis 键生成工具。
 */
public final class CounterKeys {

    private static final String SCHEMA_VERSION = "v1";

    private CounterKeys() {
    }

    /**
     * 生成互动状态位图分片键。
     *
     * @param metric 互动指标
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param chunk 位图分片编号
     * @return Redis 位图键
     */
    public static String bitmapKey(CounterMetric metric, String entityType, String entityId, long chunk) {
        return "bm:%s:%s:%s:%d".formatted(metric.value(), entityType, entityId, chunk);
    }

    /**
     * 获取某实体、某指标的位图分片索引集合键。
     *
     * <p>恢复任务通过此集合定位分片，禁止在业务请求路径使用 Redis {@code KEYS} 扫描。</p>
     *
     * @param metric 互动指标
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 位图分片索引集合键
     */
    public static String bitmapIndexKey(CounterMetric metric, String entityType, String entityId) {
        return "bmidx:%s:%s:%s:%s".formatted(SCHEMA_VERSION, metric.value(), entityType, entityId);
    }

    /**
     * 获取某实体互动事件的单调序号键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 事件序号键
     */
    public static String sequenceKey(String entityType, String entityId) {
        return "cntseq:%s:%s:%s".formatted(SCHEMA_VERSION, entityType, entityId);
    }

    /**
     * 获取实体 SDS 最近一次恢复的事件序号围栏键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 恢复围栏键
     */
    public static String recoveryFenceKey(String entityType, String entityId) {
        return "cntfence:%s:%s:%s".formatted(SCHEMA_VERSION, entityType, entityId);
    }

    /**
     * 获取实体 SDS 恢复分布式锁键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return Redisson 锁键
     */
    public static String recoveryLockKey(String entityType, String entityId) {
        return "counter:recovery:lock:%s:%s".formatted(entityType, entityId);
    }

    /**
     * 获取实体 SDS 恢复限流器键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return Redisson 限流器键
     */
    public static String recoveryRateLimiterKey(String entityType, String entityId) {
        return "counter:recovery:rate:%s:%s".formatted(entityType, entityId);
    }

    /**
     * 获取实体 SDS 恢复退避指数键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return Redisson 退避指数键
     */
    public static String recoveryBackoffExponentKey(String entityType, String entityId) {
        return "counter:recovery:backoff:exp:%s:%s".formatted(entityType, entityId);
    }

    /**
     * 获取实体 SDS 恢复退避截止时间键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return Redisson 退避截止时间键
     */
    public static String recoveryBackoffUntilKey(String entityType, String entityId) {
        return "counter:recovery:backoff:until:%s:%s".formatted(entityType, entityId);
    }

    /**
     * 生成实体计数 SDS 的 Redis 键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 计数 SDS 键
     */
    public static String sdsKey(String entityType, String entityId) {
        return "cnt:%s:%s:%s".formatted(SCHEMA_VERSION, entityType, entityId);
    }

    /**
     * 生成待折叠互动增量的 Redis Hash 键。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 聚合桶键
     */
    public static String aggregationKey(String entityType, String entityId) {
        return "agg:%s:%s:%s".formatted(SCHEMA_VERSION, entityType, entityId);
    }

    /**
     * 获取记录待刷写聚合桶的 Redis Set 键。
     *
     * @return 聚合桶索引键
     */
    public static String aggregationIndexKey() {
        return "agg:%s:index".formatted(SCHEMA_VERSION);
    }

    /**
     * 获取已聚合计数事件的去重键。
     *
     * @param eventId 计数事件唯一 ID
     * @return 事件去重键
     */
    public static String eventDedupKey(String eventId) {
        return "counter:event:%s".formatted(eventId);
    }

    /**
     * 获取用户维度计数 SDS 的 Redis 键。
     *
     * @param userId 用户 ID
     * @return 用户计数 SDS 键
     */
    public static String userSdsKey(long userId) {
        return "ucnt:%d".formatted(userId);
    }

    /**
     * 获取标记用户 SDS 已由事实数据回填的 Redis 键。
     *
     * @param userId 用户 ID
     * @return 用户计数初始化标记键
     */
    public static String userCounterInitializedKey(long userId) {
        return "ucnt:v1:initialized:%d".formatted(userId);
    }
}
