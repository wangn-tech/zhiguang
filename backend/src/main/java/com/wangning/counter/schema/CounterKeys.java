package com.wangning.counter.schema;

/**
 * 计数模块 Redis 键生成工具。
 */
public final class CounterKeys {

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
}
