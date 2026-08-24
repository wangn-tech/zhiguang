package com.wangning.counter.schema;

/**
 * 用户维度计数 SDS 的固定字段定义。
 */
public enum UserCounterMetric {

    /** 用户正在关注的人数。 */
    FOLLOWINGS(0),

    /** 用户的粉丝数。 */
    FOLLOWERS(1),

    /** 用户已发布的知文数。 */
    POSTS(2),

    /** 用户知文累计获得的点赞数。 */
    LIKES_RECEIVED(3),

    /** 用户知文累计获得的收藏数。 */
    FAVS_RECEIVED(4);

    private final int index;

    UserCounterMetric(int index) {
        this.index = index;
    }

    /**
     * 获取该字段在 SDS 中从 0 开始的段下标。
     *
     * @return 段下标
     */
    public int index() {
        return index;
    }
}
