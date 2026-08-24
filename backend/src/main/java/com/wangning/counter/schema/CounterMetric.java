package com.wangning.counter.schema;

/**
 * 当前支持的内容互动指标。
 */
public enum CounterMetric {

    /** 点赞。 */
    LIKE("like", 1),

    /** 收藏。 */
    FAV("fav", 2);

    private final String value;
    private final int index;

    CounterMetric(String value, int index) {
        this.value = value;
        this.index = index;
    }

    /**
     * 获取用于 Redis 键和 API 的指标名称。
     *
     * @return 小写指标名称
     */
    public String value() {
        return value;
    }

    /**
     * 获取该指标在实体计数 SDS 中的固定段下标。
     *
     * @return 从 0 开始的段下标
     */
    public int index() {
        return index;
    }
}
