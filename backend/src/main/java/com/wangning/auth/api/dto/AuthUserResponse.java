package com.wangning.auth.api.dto;

import java.time.LocalDate;

/**
 * 返回给客户端的认证用户信息。
 *
 * @param id 用户 ID
 * @param nickname 昵称
 * @param avatar 头像地址
 * @param phone 手机号
 * @param email 邮箱
 * @param zgId 知光号
 * @param birthday 生日
 * @param school 学校
 * @param bio 个人简介
 * @param gender 性别编码
 * @param tagJson JSON 数组格式的标签
 */
public record AuthUserResponse(
        Long id,
        String nickname,
        String avatar,
        String phone,
        String email,
        String zgId,
        LocalDate birthday,
        String school,
        String bio,
        String gender,
        String tagJson
) {
}
