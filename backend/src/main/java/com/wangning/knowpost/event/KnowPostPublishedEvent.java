package com.wangning.knowpost.event;

/**
 * 知文发布成功后的领域事件。
 *
 * @param creatorId 知文作者 ID
 */
public record KnowPostPublishedEvent(long creatorId) {
}
