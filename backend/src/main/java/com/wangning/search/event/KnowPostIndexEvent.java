package com.wangning.search.event;

/**
 * 写入 Outbox 的知文搜索索引请求。
 *
 * <p>消费者不依赖事件携带的知文快照，而是按 ID 查询 MySQL 当前事实。因此重复投递和乱序投递
 * 最终都会收敛到当前的公开状态。</p>
 *
 * @param type 事件类型，固定为 {@link #TYPE}
 * @param knowPostId 知文 ID
 */
public record KnowPostIndexEvent(String type, long knowPostId) {

    /** 知文索引请求的事件类型。 */
    public static final String TYPE = "KnowPostIndexRequested";
}
