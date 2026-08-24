package com.wangning.counter.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 未启用 Kafka 时的空事件发布实现。
 *
 * <p>该模式仅用于本地独立开发；它不会维护最终聚合计数。</p>
 */
@Service
@ConditionalOnProperty(prefix = "counter.events", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopCounterEventPublisher implements CounterEventPublisher {

    /** {@inheritDoc} */
    @Override
    public void publish(CounterEvent event) {
        // 聚合事件链路未启用时，互动位图仍是有效的事实状态。
    }
}
