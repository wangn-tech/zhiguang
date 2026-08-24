package com.wangning.relation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 关注或粉丝列表中的关系排序信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationListItem {

    /** 列表中另一方用户 ID。 */
    private Long userId;

    /** 关系创建时间，也是列表排序和游标分页依据。 */
    private Instant createdAt;
}
