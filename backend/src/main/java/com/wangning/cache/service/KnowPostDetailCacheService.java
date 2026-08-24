package com.wangning.cache.service;

import com.wangning.cache.model.KnowPostDetailSnapshot;

import java.util.Optional;

/**
 * 公开知文详情快照缓存。
 *
 * <p>该接口只处理可以跨用户共享的稳定字段。调用方负责权限判断，以及最新互动计数和用户态的补齐。</p>
 */
public interface KnowPostDetailCacheService {

    /**
     * 按知文 ID 查询详情快照。
     *
     * @param knowPostId 知文 ID
     * @return 命中时返回公开详情快照；未命中或缓存不可用时为空
     */
    Optional<KnowPostDetailSnapshot> find(long knowPostId);

    /**
     * 写入公开详情快照。
     *
     * @param snapshot 待缓存的公开快照
     */
    void put(KnowPostDetailSnapshot snapshot);

    /**
     * 删除指定知文的详情快照。
     *
     * @param knowPostId 知文 ID
     */
    void invalidate(long knowPostId);
}
