package com.wangning.counter.event;

import com.wangning.counter.schema.CounterMetric;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 一次互动状态变更产生的计数增量事件。
 *
 * <p>只有 Redis 位图状态实际变化时才会创建事件，{@code delta} 只能为 {@code 1} 或 {@code -1}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounterEvent {

    /** 事件唯一 ID，用于消费者幂等去重。 */
    private String eventId;

    /** 实体类型。 */
    private String entityType;

    /** 实体 ID。 */
    private String entityId;

    /** 互动指标名称。 */
    private String metric;

    /** 指标在 SDS 中的固定段下标。 */
    private int index;

    /** 执行互动的用户 ID。 */
    private long userId;

    /** 本次状态变更对应的计数增量。 */
    private int delta;

    /**
     * 同一实体互动状态变更的单调序号。
     *
     * <p>值为 {@code 0} 表示旧版本生产的兼容事件；大于零时，消费者会与 SDS 恢复围栏比较，
     * 防止恢复前的延迟事件重复累计。</p>
     */
    private long sequence;

    /**
     * 创建一个符合当前计数 Schema 的事件。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param metric 互动指标
     * @param userId 操作用户 ID
     * @param delta 计数增量，仅允许 {@code 1} 或 {@code -1}
     * @return 计数事件
     */
    public static CounterEvent of(
            String entityType,
            String entityId,
            CounterMetric metric,
            long userId,
            int delta
    ) {
        return of(entityType, entityId, metric, userId, delta, 0L);
    }

    /**
     * 创建一个带实体单调序号的计数事件。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param metric 互动指标
     * @param userId 操作用户 ID
     * @param delta 计数增量，仅允许 {@code 1} 或 {@code -1}
     * @param sequence 同一实体的互动事件序号
     * @return 计数事件
     */
    public static CounterEvent of(
            String entityType,
            String entityId,
            CounterMetric metric,
            long userId,
            int delta,
            long sequence
    ) {
        return new CounterEvent(
                UUID.randomUUID().toString(), entityType, entityId, metric.value(), metric.index(), userId, delta, sequence
        );
    }
}
