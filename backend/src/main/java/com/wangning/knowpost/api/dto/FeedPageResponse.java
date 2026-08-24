package com.wangning.knowpost.api.dto;

import java.util.List;

/**
 * 知文 Feed 分页响应。
 *
 * @param items 当前页内容
 * @param page 当前页码
 * @param size 当前页大小
 * @param hasMore 是否还有下一页
 */
public record FeedPageResponse(
        List<FeedItemResponse> items,
        int page,
        int size,
        boolean hasMore
) {
}
