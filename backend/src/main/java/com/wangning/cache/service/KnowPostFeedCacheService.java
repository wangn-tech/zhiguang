package com.wangning.cache.service;

import com.wangning.cache.model.FeedPageSnapshot;

import java.util.Optional;

/**
 * 知文公共 Feed 和作者 Feed 页面快照缓存。
 *
 * <p>缓存只包含可共享的稳定字段；调用方负责将实时互动计数和当前用户态补充到最终 DTO。</p>
 */
public interface KnowPostFeedCacheService {

    /**
     * 查询公共 Feed 页面快照。
     *
     * @param page 已规范化的页码
     * @param size 已规范化的页大小
     * @return 命中时返回页面快照；未命中或缓存不可用时为空
     */
    Optional<FeedPageSnapshot> findPublic(int page, int size);

    /**
     * 写入公共 Feed 页面快照。
     *
     * @param snapshot 待缓存的页面快照
     */
    void putPublic(FeedPageSnapshot snapshot);

    /**
     * 查询作者 Feed 页面快照。
     *
     * @param creatorId 作者用户 ID
     * @param page 已规范化的页码
     * @param size 已规范化的页大小
     * @return 命中时返回页面快照；未命中或缓存不可用时为空
     */
    Optional<FeedPageSnapshot> findMine(long creatorId, int page, int size);

    /**
     * 写入作者 Feed 页面快照。
     *
     * @param creatorId 作者用户 ID
     * @param snapshot 待缓存的页面快照
     */
    void putMine(long creatorId, FeedPageSnapshot snapshot);

    /**
     * 失效当前已缓存的全部公共 Feed 页面。
     */
    void invalidatePublic();

    /**
     * 失效指定作者当前已缓存的全部 Feed 页面。
     *
     * @param creatorId 作者用户 ID
     */
    void invalidateMine(long creatorId);
}
