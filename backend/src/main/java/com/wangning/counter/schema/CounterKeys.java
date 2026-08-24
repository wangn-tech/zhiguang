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
}
