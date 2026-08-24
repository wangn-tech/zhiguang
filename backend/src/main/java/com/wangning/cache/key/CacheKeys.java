package com.wangning.cache.key;

/**
 * 知文缓存 Redis 键规范。
 *
 * <p>缓存键集中维护，避免业务代码散落字符串字面量。返回的页面键同时用作 Caffeine 一级缓存键。</p>
 */
public final class CacheKeys {

    private static final String DETAIL_PREFIX = "cache:kp:detail:";
    private static final String DETAIL_LOCK_PREFIX = "cache:lock:kp:detail:";
    private static final String PUBLIC_FEED_PREFIX = "cache:kp:feed:public:";
    private static final String MINE_FEED_PREFIX = "cache:kp:feed:mine:";
    private static final String PUBLIC_FEED_INDEX_KEY = "cache:kp:feed:public:index";
    private static final String MINE_FEED_INDEX_PREFIX = "cache:kp:feed:mine:index:";

    private CacheKeys() {
    }

    /**
     * 获取公开知文详情快照键。
     *
     * @param knowPostId 知文 ID
     * @return 详情缓存键
     */
    public static String detailKey(long knowPostId) {
        return DETAIL_PREFIX + positiveId(knowPostId, "knowPostId");
    }

    /**
     * 获取详情回源互斥锁键。
     *
     * @param knowPostId 知文 ID
     * @return 短期互斥锁键
     */
    public static String detailLockKey(long knowPostId) {
        return DETAIL_LOCK_PREFIX + positiveId(knowPostId, "knowPostId");
    }

    /**
     * 获取公共 Feed 页面缓存键。
     *
     * @param page 已规范化的页码
     * @param size 已规范化的页大小
     * @return 公共 Feed 缓存键
     */
    public static String publicFeedKey(int page, int size) {
        return PUBLIC_FEED_PREFIX + positiveNumber(page, "page") + ':' + positiveNumber(size, "size");
    }

    /**
     * 获取作者 Feed 页面缓存键。
     *
     * @param creatorId 作者用户 ID
     * @param page 已规范化的页码
     * @param size 已规范化的页大小
     * @return 作者 Feed 缓存键
     */
    public static String mineFeedKey(long creatorId, int page, int size) {
        return MINE_FEED_PREFIX + positiveId(creatorId, "creatorId") + ':'
                + positiveNumber(page, "page") + ':' + positiveNumber(size, "size");
    }

    /**
     * 获取公共 Feed 页面键的反向索引。
     *
     * @return Redis Set 键
     */
    public static String publicFeedIndexKey() {
        return PUBLIC_FEED_INDEX_KEY;
    }

    /**
     * 获取某作者 Feed 页面键的反向索引。
     *
     * @param creatorId 作者用户 ID
     * @return Redis Set 键
     */
    public static String mineFeedIndexKey(long creatorId) {
        return MINE_FEED_INDEX_PREFIX + positiveId(creatorId, "creatorId");
    }

    private static long positiveId(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须为正整数");
        }
        return value;
    }

    private static int positiveNumber(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须为正整数");
        }
        return value;
    }
}
