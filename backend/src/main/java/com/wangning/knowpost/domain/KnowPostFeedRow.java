package com.wangning.knowpost.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 首页 Feed 查询结果行。
 */
@Data
public class KnowPostFeedRow {

    /** 知文 ID。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String description;

    /** 标签 JSON 数组。 */
    private String tags;

    /** 图片 URL JSON 数组。 */
    private String imgUrls;

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
}
