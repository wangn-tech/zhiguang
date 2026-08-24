package com.wangning.knowpost.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 知文持久化模型，与 MySQL 的 {@code know_posts} 表字段对应。
 *
 * <p>{@code tags} 和 {@code imgUrls} 均为写入数据库的 JSON 字符串，不直接作为 HTTP 响应返回。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowPost {

    /** 知文雪花 ID。 */
    private Long id;

    /** 主分类 ID，第一版可为空。 */
    private Long tagId;

    /** 标签 JSON 数组。 */
    private String tags;

    /** 知文标题。 */
    private String title;

    /** 知文摘要。 */
    private String description;

    /** 正文稳定公开访问地址。 */
    private String contentUrl;

    /** 正文 OSS 对象键。 */
    private String contentObjectKey;

    /** 客户端确认的正文 ETag。 */
    private String contentEtag;

    /** 客户端确认的正文字节数。 */
    private Long contentSize;

    /** 客户端计算的正文 SHA-256。 */
    private String contentSha256;

    /** 作者用户 ID。 */
    private Long creatorId;

    /** 是否在作者个人主页置顶。 */
    private Boolean isTop;

    /** 内容类型，第一版固定为 {@code image_text}。 */
    private String type;

    /** 可见性值。 */
    private String visible;

    /** 图片公开 URL 的 JSON 数组。 */
    private String imgUrls;

    /** 视频地址，第一版不使用。 */
    private String videoUrl;

    /** 状态：draft、published 或 deleted。 */
    private String status;

    /** 创建时间。 */
    private Instant createTime;

    /** 最后更新时间。 */
    private Instant updateTime;

    /** 首次发布时间。 */
    private Instant publishTime;
}
