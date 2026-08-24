package com.wangning.relation.outbox;

/**
 * Outbox 消息主题常量。
 */
public final class OutboxTopics {

    /** Canal 将 {@code outbox} 表变更转发到的默认 Topic。 */
    public static final String CANAL_OUTBOX = "canal-outbox";

    private OutboxTopics() {
    }
}
