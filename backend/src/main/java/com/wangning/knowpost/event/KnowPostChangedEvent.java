package com.wangning.knowpost.event;

/**
 * 知文持久化数据发生变化后的领域事件。
 *
 * <p>监听器必须在所属事务成功提交后处理，避免缓存在线程内事务回滚时被错误失效。</p>
 *
 * @param knowPostId 知文 ID
 * @param creatorId 作者用户 ID
 */
public record KnowPostChangedEvent(long knowPostId, long creatorId) {
}
