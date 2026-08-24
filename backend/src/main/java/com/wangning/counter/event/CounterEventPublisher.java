package com.wangning.counter.event;

/**
 * 计数事件发布端口。
 */
public interface CounterEventPublisher {

    /**
     * 发布一条已发生的互动计数增量事件。
     *
     * @param event 计数事件
     */
    void publish(CounterEvent event);
}
