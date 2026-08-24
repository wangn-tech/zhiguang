package com.wangning.knowpost.api.dto;

import java.util.List;

/**
 * 知文 Feed 的单条响应。
 *
 * @param id 字符串形式的知文 ID
 * @param title 标题
 * @param description 摘要
 * @param coverImage 首张图片 URL，没有图片时为 {@code null}
 * @param tags 标签列表
 * @param authorAvatar 作者头像 URL
 * @param authorNickname 作者昵称
 * @param tagJson 作者标签 JSON，沿用当前前端字段名
 * @param likeCount 点赞数
 * @param favoriteCount 收藏数
 * @param liked 当前用户是否点赞
 * @param faved 当前用户是否收藏
 * @param isTop 是否置顶
 */
public record FeedItemResponse(
        String id,
        String title,
        String description,
        String coverImage,
        List<String> tags,
        String authorAvatar,
        String authorNickname,
        String tagJson,
        Long likeCount,
        Long favoriteCount,
        Boolean liked,
        Boolean faved,
        Boolean isTop
) {
}
