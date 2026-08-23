package com.wangning.user.service;

import com.wangning.user.domain.User;

import java.util.Optional;

/**
 * 用户基础服务。
 */
public interface UserService {

    /**
     * 根据用户 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户 Optional，不存在时为空
     */
    Optional<User> findById(long id);

    /**
     * 根据手机号查询用户。
     *
     * @param phone 手机号
     * @return 用户 Optional，不存在时为空
     */
    Optional<User> findByPhone(String phone);

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱
     * @return 用户 Optional，不存在时为空
     */
    Optional<User> findByEmail(String email);

    /**
     * 判断手机号是否已经存在。
     *
     * @param phone 手机号
     * @return 存在时返回 {@code true}
     */
    boolean existsByPhone(String phone);

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 邮箱
     * @return 存在时返回 {@code true}
     */
    boolean existsByEmail(String email);

    /**
     * 创建用户。
     *
     * @param user 待创建的用户
     * @return 已回填主键和时间的用户
     */
    User createUser(User user);

    /**
     * 更新用户密码哈希。
     *
     * @param userId 用户 ID
     * @param passwordHash BCrypt 密码哈希
     */
    void updatePasswordHash(long userId, String passwordHash);
}
