package com.wangning.counter.service;

/**
 * 用户主页展示所需的聚合计数快照。
 *
 * @param followings 关注数
 * @param followers 粉丝数
 * @param posts 已发布知文数
 * @param likesReceived 获赞数
 * @param favsReceived 获收藏数
 */
public record UserCounters(
        long followings,
        long followers,
        long posts,
        long likesReceived,
        long favsReceived
) {
}
