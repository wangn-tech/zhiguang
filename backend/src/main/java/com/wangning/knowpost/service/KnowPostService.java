package com.wangning.knowpost.service;

import java.util.List;

/**
 * 知文写入与详情业务服务。
 */
public interface KnowPostService {

    /**
     * 为当前用户创建图文草稿。
     *
     * @param creatorId 当前作者用户 ID
     * @return 新草稿的雪花 ID
     */
    long createDraft(long creatorId);

    /**
     * 确认正文已上传，并保存对应的公开地址和客户端上传元数据。
     *
     * @param creatorId 当前作者用户 ID
     * @param id 知文 ID
     * @param objectKey 正文 OSS 对象键
     * @param etag 客户端取得的 OSS ETag
     * @param size 客户端取得的正文字节数
     * @param sha256 客户端计算的 SHA-256
     */
    void confirmContent(
            long creatorId,
            long id,
            String objectKey,
            String etag,
            Long size,
            String sha256
    );

    /**
     * 更新作者知文的元数据。
     *
     * @param creatorId 当前作者用户 ID
     * @param id 知文 ID
     * @param title 标题，可为空表示不更新
     * @param tagId 主分类 ID，可为空表示不更新
     * @param tags 标签列表，可为空表示不更新
     * @param imgUrls 图片 URL 列表，可为空表示不更新
     * @param visible 可见性，可为空表示不更新
     * @param isTop 是否置顶，可为空表示不更新
     * @param description 摘要，可为空表示不更新
     */
    void updateMetadata(
            long creatorId,
            long id,
            String title,
            Long tagId,
            List<String> tags,
            List<String> imgUrls,
            String visible,
            Boolean isTop,
            String description
    );

    /**
     * 发布作者的知文。
     *
     * @param creatorId 当前作者用户 ID
     * @param id 知文 ID
     */
    void publish(long creatorId, long id);
}
