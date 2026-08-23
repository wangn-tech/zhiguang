package com.wangning.profile.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.profile.api.dto.ProfilePatchRequest;
import com.wangning.profile.api.dto.ProfileResponse;
import com.wangning.profile.model.Gender;
import com.wangning.profile.service.AvatarFileValidator;
import com.wangning.profile.service.ProfileService;
import com.wangning.storage.ObjectStorageService;
import com.wangning.storage.model.StoredObject;
import com.wangning.user.domain.User;
import com.wangning.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 当前用户个人资料服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private static final String EMPTY_TAGS_JSON = "[]";
    private static final Pattern ZG_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,32}$");

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final AvatarFileValidator avatarFileValidator;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;

    /**
     * 合并、校验并更新当前用户资料。
     *
     * @param userId 当前用户 ID
     * @param request 资料局部更新请求
     * @return 更新后的完整资料
     * @throws BusinessException 用户不存在、参数错误、知光号冲突或更新失败时抛出
     */
    @Override
    @Transactional
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest request) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        User current = userMapper.findById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (request == null || !request.hasAnyField()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未提交任何更新字段");
        }

        User merged = mergeProfile(current, request);
        if (request.isZgIdPresent()
                && merged.getZgId() != null
                && !Objects.equals(merged.getZgId(), current.getZgId())
                && userMapper.existsByZgIdExceptId(merged.getZgId(), userId)) {
            throw new BusinessException(ErrorCode.ZGID_EXISTS);
        }

        int affectedRows;
        try {
            affectedRows = userMapper.updateProfile(merged);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.ZGID_EXISTS);
        }
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "个人资料更新失败");
        }

        User updated = userMapper.findById(userId);
        if (updated == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "个人资料读取失败");
        }
        return toResponse(updated);
    }

    /**
     * 校验头像、上传 OSS，并将公开地址保存到当前用户资料。
     *
     * <p>OSS 上传成功但数据库更新失败时，会尽力删除本次上传的新对象。
     * 旧头像可能来自外部地址，因此本方法不根据 URL 猜测对象键并删除旧文件。</p>
     *
     * @param userId 当前用户 ID
     * @param file 头像文件
     * @return 更新头像后的完整资料
     * @throws BusinessException 用户不存在、头像不合法、OSS 不可用或更新失败时抛出
     */
    @Override
    public ProfileResponse uploadAvatar(long userId, MultipartFile file) {
        if (userId <= 0 || userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        AvatarFileValidator.ValidatedAvatar avatar = avatarFileValidator.validate(file);
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(ErrorCode.STORAGE_CONFIGURATION_ERROR);
        }

        String objectKey = buildAvatarObjectKey(userId, avatar.extension());
        StoredObject storedObject = uploadAvatarObject(
                storageService,
                objectKey,
                avatar.contentType(),
                file
        );

        int affectedRows;
        try {
            affectedRows = userMapper.updateAvatar(userId, storedObject.publicUrl());
        } catch (RuntimeException exception) {
            compensateUploadedAvatar(storageService, storedObject.objectKey(), userId);
            throw exception;
        }
        if (affectedRows != 1) {
            compensateUploadedAvatar(storageService, storedObject.objectKey(), userId);
            if (affectedRows == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像更新失败");
        }

        User updated = userMapper.findById(userId);
        if (updated == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "个人资料读取失败");
        }
        return toResponse(updated);
    }

    /**
     * 生成不能由客户端控制的头像对象键。
     *
     * @param userId 当前用户 ID
     * @param extension 服务端确认的文件扩展名
     * @return 头像对象键
     */
    private String buildAvatarObjectKey(long userId, String extension) {
        return "avatars/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
    }

    /**
     * 打开头像输入流并上传对象存储。
     *
     * @param storageService 对象存储服务
     * @param objectKey 后端生成的对象键
     * @param contentType 服务端确认的 MIME 类型
     * @param file 头像文件
     * @return 已上传对象信息
     */
    private StoredObject uploadAvatarObject(
            ObjectStorageService storageService,
            String objectKey,
            String contentType,
            MultipartFile file
    ) {
        try (InputStream inputStream = file.getInputStream()) {
            return storageService.upload(objectKey, contentType, file.getSize(), inputStream);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        }
    }

    /**
     * 数据库更新失败后尽力删除本次新上传的头像。
     *
     * @param storageService 对象存储服务
     * @param objectKey 本次上传的对象键
     * @param userId 当前用户 ID，仅用于安全日志上下文
     */
    private void compensateUploadedAvatar(
            ObjectStorageService storageService,
            String objectKey,
            long userId
    ) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "Avatar upload compensation failed: userId={}, objectKey={}, exceptionType={}",
                    userId,
                    objectKey,
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 将请求中出现的字段与当前资料合并。
     *
     * @param current 当前数据库资料
     * @param request 局部更新请求
     * @return 已完成标准化和校验的完整更新对象
     */
    private User mergeProfile(User current, ProfilePatchRequest request) {
        String nickname = request.isNicknamePresent()
                ? normalizeNickname(request.getNickname())
                : current.getNickname();
        String bio = request.isBioPresent()
                ? normalizeNullableText(request.getBio(), 512, "个人简介长度不能超过 512 个字符")
                : current.getBio();
        String zgId = request.isZgIdPresent()
                ? normalizeZgId(request.getZgId())
                : current.getZgId();
        String school = request.isSchoolPresent()
                ? normalizeNullableText(request.getSchool(), 128, "学校名称不能超过 128 个字符")
                : current.getSchool();
        LocalDate birthday = request.isBirthdayPresent()
                ? validateBirthday(request.getBirthday())
                : current.getBirthday();
        String gender = request.isGenderPresent()
                ? toStoredGender(request.getGender())
                : current.getGender();
        String tagsJson = request.isTagJsonPresent()
                ? normalizeTagsJson(request.getTagJson())
                : defaultTagsJson(current.getTagsJson());

        return User.builder()
                .id(current.getId())
                .nickname(nickname)
                .bio(bio)
                .zgId(zgId)
                .gender(gender)
                .birthday(birthday)
                .school(school)
                .tagsJson(tagsJson)
                .build();
    }

    /**
     * 标准化并校验昵称。
     *
     * @param nickname 原始昵称
     * @return 标准化昵称
     */
    private String normalizeNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能为空");
        }
        String normalized = nickname.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称长度不能超过 64 个字符");
        }
        return normalized;
    }

    /**
     * 标准化可空文本，空白文本按清空处理。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @param errorMessage 超长时的错误提示
     * @return 标准化文本，null 或空白值返回 null
     */
    private String normalizeNullableText(String value, int maxLength, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    /**
     * 标准化并校验知光号。
     *
     * @param zgId 原始知光号
     * @return 标准化知光号，null 表示清空
     */
    private String normalizeZgId(String zgId) {
        if (zgId == null) {
            return null;
        }
        String normalized = zgId.trim();
        if (!ZG_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "知光号仅支持字母、数字、下划线，长度 4-32"
            );
        }
        return normalized;
    }

    /**
     * 校验生日不能晚于今天。
     *
     * @param birthday 生日，null 表示清空
     * @return 已校验的生日
     */
    private LocalDate validateBirthday(LocalDate birthday) {
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "生日不能晚于今天");
        }
        return birthday;
    }

    /**
     * 将枚举转换为数据库编码。
     *
     * @param gender 性别，null 表示清空
     * @return 数据库性别编码
     */
    private String toStoredGender(Gender gender) {
        return gender == null ? null : gender.name();
    }

    /**
     * 校验、去空格、去重并序列化用户标签。
     *
     * @param tagJson 原始标签 JSON，null 表示清空
     * @return 标准化 JSON 字符串数组
     */
    private String normalizeTagsJson(String tagJson) {
        if (tagJson == null) {
            return EMPTY_TAGS_JSON;
        }
        if (!StringUtils.hasText(tagJson)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签格式错误");
        }

        try {
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(tagJson);
            if (root == null || !root.isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签必须是字符串数组");
            }

            Set<String> normalizedTags = new LinkedHashSet<>();
            for (JsonNode tagNode : root) {
                if (!tagNode.isTextual()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签必须是字符串数组");
                }
                String tag = tagNode.textValue().trim();
                if (!StringUtils.hasText(tag)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签不能包含空白项");
                }
                normalizedTags.add(tag);
            }
            return objectMapper.writeValueAsString(normalizedTags);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户标签格式错误");
        }
    }

    /**
     * 将历史 null 标签统一为无标签数组。
     *
     * @param tagsJson 数据库标签 JSON
     * @return 非 null 的标签 JSON
     */
    private String defaultTagsJson(String tagsJson) {
        return StringUtils.hasText(tagsJson) ? tagsJson : EMPTY_TAGS_JSON;
    }

    /**
     * 将用户持久化模型转换为资料响应。
     *
     * @param user 用户持久化模型
     * @return 资料响应
     */
    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getBio(),
                user.getZgId(),
                parseStoredGender(user.getGender()),
                user.getBirthday(),
                user.getSchool(),
                user.getPhone(),
                user.getEmail(),
                defaultTagsJson(user.getTagsJson())
        );
    }

    /**
     * 将数据库性别编码转换为枚举。
     *
     * @param gender 数据库性别编码
     * @return 性别枚举，空值返回 null
     */
    private Gender parseStoredGender(String gender) {
        if (!StringUtils.hasText(gender)) {
            return null;
        }
        try {
            return Gender.valueOf(gender.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户性别数据异常");
        }
    }
}
