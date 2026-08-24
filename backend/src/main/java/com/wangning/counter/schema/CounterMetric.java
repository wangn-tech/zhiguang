package com.wangning.counter.schema;

/**
 * 当前支持的内容互动指标。
 */
public enum CounterMetric {

    /** 点赞。 */
    LIKE("like"),

    /** 收藏。 */
    FAV("fav");

    private final String value;

    CounterMetric(String value) {
        this.value = value;
    }

    /**
     * 获取用于 Redis 键和 API 的指标名称。
     *
     * @return 小写指标名称
     */
    public String value() {
        return value;
    }
}
