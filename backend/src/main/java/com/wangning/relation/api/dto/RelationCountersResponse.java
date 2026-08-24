package com.wangning.relation.api.dto;

/**
 * 用户主页展示的关系和互动计数。
 *
 * @param followings 关注数
 * @param followers 粉丝数
 * @param posts 已发布知文数
 * @param likedPosts 获赞数
 * @param favedPosts 获收藏数
 */
public record RelationCountersResponse(
        long followings,
        long followers,
        long posts,
        long likedPosts,
        long favedPosts
) {
}
