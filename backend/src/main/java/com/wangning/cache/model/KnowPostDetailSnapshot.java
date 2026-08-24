package com.wangning.cache.model;

import com.wangning.knowpost.api.dto.KnowPostDetailResponse;

import java.time.Instant;
import java.util.List;

/**
 * 可安全共享的公开知文详情快照。
 *
 * <p>快照不包含点赞数、收藏数以及 {@code liked}/{@code faved} 用户态；这些字段会在每次响应时
 * 从互动事实层读取并补齐，避免将某个用户的数据泄露给其他用户。</p>
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
 * @param isTop 是否置顶
 * @param visible 可见性
 * @param type 知文类型
 * @param publishTime 发布时间
 */
public record KnowPostDetailSnapshot(
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
        Boolean isTop,
        String visible,
        String type,
        Instant publishTime
) {

    /**
     * 使用当前的互动数据组装原有接口响应。
     *
     * @param likeCount 最新点赞数
     * @param favoriteCount 最新收藏数
     * @param liked 当前用户是否已点赞
     * @param faved 当前用户是否已收藏
     * @return 前端接口响应
     */
    public KnowPostDetailResponse toResponse(
            long likeCount,
            long favoriteCount,
            boolean liked,
            boolean faved
    ) {
        return new KnowPostDetailResponse(
                id,
                title,
                description,
                contentUrl,
                images,
                tags,
                authorId,
                authorAvatar,
                authorNickname,
                authorTagJson,
                likeCount,
                favoriteCount,
                liked,
                faved,
                isTop,
                visible,
                type,
                publishTime
        );
    }
}
