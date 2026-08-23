package com.wangning.user.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.user.domain.User;
import com.wangning.user.mapper.UserMapper;
import com.wangning.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * 用户基础服务实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String EMPTY_TAGS_JSON = "[]";

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户 Optional，不存在时为空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(long id) {
        return Optional.ofNullable(userMapper.findById(id));
    }

    /**
     * 根据手机号查询用户。
     *
     * @param phone 手机号
     * @return 用户 Optional，不存在时为空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByPhone(String phone) {
        String normalizedPhone = normalizeNullable(phone);
        if (normalizedPhone == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.findByPhone(normalizedPhone));
    }

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱
     * @return 用户 Optional，不存在时为空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.findByEmail(normalizedEmail));
    }

    /**
     * 判断手机号是否已经存在。
     *
     * @param phone 手机号
     * @return 存在时返回 {@code true}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {
        String normalizedPhone = normalizeNullable(phone);
        return normalizedPhone != null && userMapper.existsByPhone(normalizedPhone);
    }

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 邮箱
     * @return 存在时返回 {@code true}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return normalizedEmail != null && userMapper.existsByEmail(normalizedEmail);
    }

    /**
     * 校验并创建用户。
     *
     * @param user 待创建的用户
     * @return 已回填主键和时间的用户
     * @throws BusinessException 用户数据不完整、账号重复或创建失败时抛出
     */
    @Override
    @Transactional
    public User createUser(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户信息不能为空");
        }

        String phone = normalizeNullable(user.getPhone());
        String email = normalizeEmail(user.getEmail());
        String nickname = normalizeNullable(user.getNickname());

        validateRequiredFields(phone, email, nickname);
        validateFieldLengths(phone, email, nickname);

        if (phone != null && userMapper.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS, "手机号已存在");
        }
        if (email != null && userMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS, "邮箱已存在");
        }

        String tagsJson = StringUtils.hasText(user.getTagsJson())
                ? user.getTagsJson().trim()
                : EMPTY_TAGS_JSON;
        validateTagsJson(tagsJson);

        Instant now = Instant.now();
        user.setId(null);
        user.setPhone(phone);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setTagsJson(tagsJson);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            int affectedRows = userMapper.insert(user);
            if (affectedRows != 1 || user.getId() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户创建失败");
            }
            return user;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS, "账号已存在");
        }
    }

    /**
     * 校验用户创建所需字段。
     *
     * @param phone 标准化手机号
     * @param email 标准化邮箱
     * @param nickname 标准化昵称
     */
    private void validateRequiredFields(String phone, String email, String nickname) {
        if (phone == null && email == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号和邮箱不能同时为空");
        }
        if (nickname == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能为空");
        }
    }

    /**
     * 校验与数据库字段一致的长度上限。
     *
     * @param phone 标准化手机号
     * @param email 标准化邮箱
     * @param nickname 标准化昵称
     */
    private void validateFieldLengths(String phone, String email, String nickname) {
        if (phone != null && phone.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号长度不能超过 32 个字符");
        }
        if (email != null && email.length() > 128) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱长度不能超过 128 个字符");
        }
        if (nickname.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称长度不能超过 64 个字符");
        }
    }

    /**
     * 校验用户标签为 JSON 数组。
     *
     * @param tagsJson 用户标签 JSON
     */
    private void validateTagsJson(String tagsJson) {
        try {
            JsonNode tagsNode = objectMapper.readTree(tagsJson);
            if (tagsNode == null || !tagsNode.isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签必须是 JSON 数组");
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签格式错误");
        }
    }

    /**
     * 去除可空字符串的首尾空格，并将空白字符串转换为 {@code null}。
     *
     * @param value 原始字符串
     * @return 标准化字符串
     */
    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 标准化邮箱，避免大小写导致重复账号。
     *
     * @param email 原始邮箱
     * @return 小写邮箱，空白值返回 {@code null}
     */
    private String normalizeEmail(String email) {
        String normalizedEmail = normalizeNullable(email);
        return normalizedEmail == null ? null : normalizedEmail.toLowerCase(Locale.ROOT);
    }
}
