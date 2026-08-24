package com.wangning.cache.model;

import com.wangning.knowpost.api.dto.FeedItemResponse;

import java.util.List;

/**
 * 可跨用户共享的 Feed 单项快照。
 *
 * <p>互动计数和用户点赞、收藏状态不进入快照，返回接口前再按本次请求补齐。</p>
 *
 * @param id 字符串形式的知文 ID
 * @param title 标题
 * @param description 摘要
 * @param coverImage 封面图 URL
 * @param tags 标签列表
 * @param authorAvatar 作者头像 URL
 * @param authorNickname 作者昵称
 * @param tagJson 作者标签 JSON
 * @param isTop 是否置顶
 */
public record FeedItemSnapshot(
        String id,
        String title,
        String description,
        String coverImage,
        List<String> tags,
        String authorAvatar,
        String authorNickname,
        String tagJson,
        Boolean isTop
) {

    /**
     * 使用当前互动数据生成接口响应项。
     *
     * @param likeCount 最新点赞数
     * @param favoriteCount 最新收藏数
     * @param liked 当前用户是否已点赞
     * @param faved 当前用户是否已收藏
     * @return Feed 接口响应项
     */
    public FeedItemResponse toResponse(
            long likeCount,
            long favoriteCount,
            boolean liked,
            boolean faved
    ) {
        return new FeedItemResponse(
                id,
                title,
                description,
                coverImage,
                tags,
                authorAvatar,
                authorNickname,
                tagJson,
                likeCount,
                favoriteCount,
                liked,
                faved,
                isTop
        );
    }
}
