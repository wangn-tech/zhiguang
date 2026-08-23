package com.wangning.profile.api.dto;

import com.wangning.profile.model.Gender;

import java.time.LocalDate;

/**
 * 当前用户完整资料响应。
 *
 * @param id 用户 ID
 * @param nickname 昵称
 * @param avatar 头像地址
 * @param bio 个人简介
 * @param zgId 知光号
 * @param gender 性别
 * @param birthday 生日
 * @param school 学校或机构
 * @param phone 手机号
 * @param email 邮箱
 * @param tagJson JSON 字符串数组格式的用户标签
 */
public record ProfileResponse(
        Long id,
        String nickname,
        String avatar,
        String bio,
        String zgId,
        Gender gender,
        LocalDate birthday,
        String school,
        String phone,
        String email,
        String tagJson
) {
}
