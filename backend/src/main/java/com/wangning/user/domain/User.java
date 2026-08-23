package com.wangning.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 用户持久化模型，与 MySQL 的 {@code users} 表字段对应。
 *
 * <p>该对象只在后端内部使用，不应直接作为接口响应返回给客户端。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** 用户主键。 */
    private Long id;

    /** 手机号账号。 */
    private String phone;

    /** 邮箱账号。 */
    private String email;

    /** 密码哈希，禁止写入日志或返回给客户端。 */
    @ToString.Exclude
    private String passwordHash;

    /** 用户昵称。 */
    private String nickname;

    /** 头像地址。 */
    private String avatar;

    /** 个人简介。 */
    private String bio;

    /** 知光号。 */
    private String zgId;

    /** 性别编码。 */
    private String gender;

    /** 生日。 */
    private LocalDate birthday;

    /** 学校名称。 */
    private String school;

    /** JSON 数组格式的用户标签。 */
    private String tagsJson;

    /** 创建时间。 */
    private Instant createdAt;

    /** 最后更新时间。 */
    private Instant updatedAt;
}
