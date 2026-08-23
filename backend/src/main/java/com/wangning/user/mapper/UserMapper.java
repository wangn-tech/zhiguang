package com.wangning.user.mapper;

import com.wangning.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问接口。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户，不存在时返回 {@code null}
     */
    User findById(@Param("id") long id);

    /**
     * 根据手机号查询用户。
     *
     * @param phone 已标准化的手机号
     * @return 用户，不存在时返回 {@code null}
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 根据邮箱查询用户。
     *
     * @param email 已标准化的邮箱
     * @return 用户，不存在时返回 {@code null}
     */
    User findByEmail(@Param("email") String email);

    /**
     * 判断手机号是否已经存在。
     *
     * @param phone 已标准化的手机号
     * @return 存在时返回 {@code true}
     */
    boolean existsByPhone(@Param("phone") String phone);

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 已标准化的邮箱
     * @return 存在时返回 {@code true}
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * 新增用户，并将数据库生成的主键回填到用户对象。
     *
     * @param user 待新增的用户
     * @return 受影响的行数
     */
    int insert(User user);

    /**
     * 更新用户密码哈希和最后更新时间。
     *
     * @param id 用户 ID
     * @param passwordHash BCrypt 密码哈希
     * @return 受影响的行数
     */
    int updatePasswordHash(
            @Param("id") long id,
            @Param("passwordHash") String passwordHash
    );
}
