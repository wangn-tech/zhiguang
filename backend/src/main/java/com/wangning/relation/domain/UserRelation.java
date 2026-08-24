package com.wangning.relation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 用户之间的一条单向关注关系。
 *
 * <p>该模型同时映射 {@code following} 与 {@code follower} 表。两张表的用户 ID
 * 字段方向不同，具体由 Mapper 的方法语义决定。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRelation {

    /** 关系记录 ID。 */
    private Long id;

    /** 关注发起者用户 ID。 */
    private Long fromUserId;

    /** 被关注者用户 ID。 */
    private Long toUserId;

    /** 是否为有效关注关系。 */
    private Boolean active;

    /** 最近一次关注发生时间，用于列表排序。 */
    private Instant createdAt;

    /** 关系状态最近更新时间。 */
    private Instant updatedAt;
}
