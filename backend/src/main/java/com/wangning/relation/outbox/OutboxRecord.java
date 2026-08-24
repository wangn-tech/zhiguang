package com.wangning.relation.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 待由 Canal 捕获的 Outbox 事件记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxRecord {

    /** Outbox 记录 ID。 */
    private Long id;

    /** 聚合类型，例如 {@code following}。 */
    private String aggregateType;

    /** 聚合记录 ID。 */
    private Long aggregateId;

    /** 事件类型。 */
    private String type;

    /** JSON 格式事件载荷。 */
    private String payload;

    /** 事件创建时间。 */
    private Instant createdAt;
}
