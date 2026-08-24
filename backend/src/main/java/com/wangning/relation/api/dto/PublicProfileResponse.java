package com.wangning.relation.api.dto;

import java.time.LocalDate;

/**
 * 关注和粉丝列表使用的公开用户资料。
 *
 * <p>该响应刻意不包含手机号、邮箱和密码哈希等敏感字段。</p>
 *
 * @param id 用户 ID
 * @param nickname 昵称
 * @param avatar 头像地址
 * @param bio 个人简介
 * @param zgId 知光号
 * @param gender 性别编码
 * @param birthday 生日
 * @param school 学校或机构
 * @param tagJson JSON 字符串数组格式的用户标签
 */
public record PublicProfileResponse(
        Long id,
        String nickname,
        String avatar,
        String bio,
        String zgId,
        String gender,
        LocalDate birthday,
        String school,
        String tagJson
) {
}
