package com.wangning.relation.outbox;

/**
 * Canal 转发的一条 Outbox 行。
 *
 * @param outboxId Outbox 主键，用于消费者幂等去重
 * @param payload 关系事件 JSON 字符串
 */
public record CanalOutboxMessage(long outboxId, String payload) {
}
