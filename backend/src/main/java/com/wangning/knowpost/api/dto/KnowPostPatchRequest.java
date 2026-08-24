package com.wangning.knowpost.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 知文元数据局部更新请求，字段为空时不更新。
 *
 * @param title 标题
 * @param tagId 主分类 ID
 * @param tags 标签列表
 * @param imgUrls 图片公开 URL 列表
 * @param visible 可见性
 * @param isTop 是否置顶
 * @param description 摘要
 */
public record KnowPostPatchRequest(
        String title,
        Long tagId,
        @Size(max = 20, message = "标签最多 20 项") List<String> tags,
        @Size(max = 20, message = "图片最多 20 张") List<String> imgUrls,
        String visible,
        Boolean isTop,
        String description
) {
}
