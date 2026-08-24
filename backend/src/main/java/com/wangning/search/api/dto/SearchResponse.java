package com.wangning.search.api.dto;

import com.wangning.knowpost.api.dto.FeedItemResponse;

import java.util.List;

/**
 * 知文搜索结果页。
 *
 * @param items 当前页搜索结果，字段与公开 Feed 保持一致
 * @param nextAfter 下一页 Base64URL 游标；没有更多数据时为 {@code null}
 * @param hasMore 是否还有下一页
 */
public record SearchResponse(
        List<FeedItemResponse> items,
        String nextAfter,
        boolean hasMore
) {
}
