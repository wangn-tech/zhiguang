package com.wangning.search.index;

import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch 中的一篇知文搜索文档。
 *
 * <p>互动计数和用户态不写入此文档，搜索响应阶段从 Redis 事实层实时补齐。</p>
 *
 * @param id 知文 ID
 * @param title 标题
 * @param description 摘要
 * @param body 正文纯文本
 * @param tags 标签
 * @param authorId 作者 ID
 * @param authorAvatar 作者头像
 * @param authorNickname 作者昵称
 * @param authorTagJson 作者标签 JSON
 * @param imgUrls 图片 URL
 * @param isTop 是否置顶
 * @param publishTime 发布时间
 * @param status 索引状态，公开可搜索内容为 {@code published}
 * @param titleSuggest 标题联想字段
 */
public record KnowPostSearchDocument(
        long id,
        String title,
        String description,
        String body,
        List<String> tags,
        long authorId,
        String authorAvatar,
        String authorNickname,
        String authorTagJson,
        List<String> imgUrls,
        Boolean isTop,
        Instant publishTime,
        String status,
        String titleSuggest
) {

    /**
     * 构建保留文档 ID 的软删除索引文档。
     *
     * @param id 知文 ID
     * @return 状态为 {@code deleted} 的文档
     */
    public static KnowPostSearchDocument deleted(long id) {
        return new KnowPostSearchDocument(
                id, null, null, null, List.of(), 0L,
                null, null, null, List.of(), null, null, "deleted", null
        );
    }
}
