package com.wangning.knowpost.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 知文详情响应。
 *
 * @param id 字符串形式的知文 ID
 * @param title 标题
 * @param description 摘要
 * @param contentUrl 正文稳定公开 URL
 * @param images 图片 URL 列表
 * @param tags 标签列表
 * @param authorId 字符串形式的作者 ID
 * @param authorAvatar 作者头像 URL
 * @param authorNickname 作者昵称
 * @param authorTagJson 作者标签 JSON
 * @param likeCount 点赞数
 * @param favoriteCount 收藏数
 * @param liked 当前用户是否点赞
 * @param faved 当前用户是否收藏
 * @param isTop 是否置顶
 * @param visible 可见性
 * @param type 知文类型
 * @param publishTime 发布时间
 */
public record KnowPostDetailResponse(
        String id,
        String title,
        String description,
        String contentUrl,
        List<String> images,
        List<String> tags,
        String authorId,
        String authorAvatar,
        String authorNickname,
        String authorTagJson,
        Long likeCount,
        Long favoriteCount,
        Boolean liked,
        Boolean faved,
        Boolean isTop,
        String visible,
        String type,
        Instant publishTime
) {
}
