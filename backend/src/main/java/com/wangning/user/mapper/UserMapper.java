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
     * 判断知光号是否已被当前用户之外的其他用户使用。
     *
     * @param zgId 已标准化的知光号
     * @param excludeId 需要排除的当前用户 ID
     * @return 被其他用户使用时返回 {@code true}
     */
    boolean existsByZgIdExceptId(
            @Param("zgId") String zgId,
            @Param("excludeId") long excludeId
    );

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

    /**
     * 更新用户的全部可编辑文本资料。
     *
     * <p>调用方必须先合并未提交字段与原资料。本方法会无条件写入资料字段，
     * 因此字段值为 {@code null} 时会清空对应数据库字段。</p>
     *
     * @param user 已合并并完成校验的用户资料，必须包含用户 ID
     * @return 受影响的行数
     */
    int updateProfile(User user);
}
