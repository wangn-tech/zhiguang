package com.wangning.cache.model;

import java.util.List;

/**
 * 可跨用户共享的 Feed 页面快照。
 *
 * @param items 当前页稳定内容项
 * @param page 已规范化的页码
 * @param size 已规范化的页大小
 * @param hasMore 是否还有下一页
 */
public record FeedPageSnapshot(
        List<FeedItemSnapshot> items,
        int page,
        int size,
        boolean hasMore
) {
}
