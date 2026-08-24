package com.wangning.knowpost.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 知文详情查询结果行，包含作者展示信息。
 */
@Data
public class KnowPostDetailRow {

    /** 知文 ID。 */
    private Long id;

    /** 作者用户 ID。 */
    private Long creatorId;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String description;

    /** 标签 JSON 数组。 */
    private String tags;

    /** 图片 URL JSON 数组。 */
    private String imgUrls;

    /** 正文稳定公开访问地址。 */
    private String contentUrl;

    /** 正文 ETag。 */
    private String contentEtag;

    /** 正文 SHA-256。 */
    private String contentSha256;

    /** 作者头像。 */
    private String authorAvatar;

    /** 作者昵称。 */
    private String authorNickname;

    /** 作者标签 JSON 数组。 */
    private String authorTagJson;

    /** 发布时间。 */
    private Instant publishTime;

    /** 是否置顶。 */
    private Boolean isTop;

    /** 可见性。 */
    private String visible;

    /** 内容类型。 */
    private String type;

    /** 知文状态。 */
    private String status;
}
