package com.wangning.knowpost.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.domain.SnowflakeIdGenerator;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.KnowPostService;
import com.wangning.storage.ObjectStorageService;
import com.wangning.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 沿用旧项目数据流的知文写入服务。
 *
 * <p>浏览器将正文和图片直接上传至公共 OSS。正文确认后保存稳定公开 URL，图片则由前端提交
 * {@code imgUrls} 并原样以 JSON 数组存储。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowPostServiceImpl implements KnowPostService {

    private static final String STATUS_DRAFT = "draft";
    private static final String TYPE_IMAGE_TEXT = "image_text";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final Set<String> VALID_VISIBILITIES = Set.of(
            "public", "followers", "school", "private", "unlisted"
    );

    private final KnowPostMapper knowPostMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public long createDraft(long creatorId) {
        validateCreator(creatorId);

        long id = snowflakeIdGenerator.nextId();
        Instant now = Instant.now();
        KnowPost draft = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .status(STATUS_DRAFT)
                .type(TYPE_IMAGE_TEXT)
                .visible(VISIBILITY_PUBLIC)
                .isTop(false)
                .createTime(now)
                .updateTime(now)
                .build();

        int affectedRows = knowPostMapper.insertDraft(draft);
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "草稿创建失败");
        }
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void confirmContent(
            long creatorId,
            long id,
            String objectKey,
            String etag,
            Long size,
            String sha256
    ) {
        validateCreator(creatorId);
        validatePostId(id);
        requireText(objectKey, "正文对象键不能为空");
        requireText(etag, "正文 ETag 不能为空");
        requireText(sha256, "正文 SHA-256 不能为空");
        if (size == null || size < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文字节数非法");
        }

        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(ErrorCode.STORAGE_CONFIGURATION_ERROR);
        }

        KnowPost content = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .contentUrl(storageService.publicUrl(objectKey))
                .contentObjectKey(objectKey)
                .contentEtag(etag)
                .contentSize(size)
                .contentSha256(sha256)
                .updateTime(Instant.now())
                .build();

        ensureSingleRow(knowPostMapper.updateContent(content), "草稿不存在或无权限");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateMetadata(
            long creatorId,
            long id,
            String title,
            Long tagId,
            List<String> tags,
            List<String> imgUrls,
            String visible,
            Boolean isTop,
            String description
    ) {
        validateCreator(creatorId);
        validatePostId(id);
        if (visible != null && !VALID_VISIBILITIES.contains(visible)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        }

        KnowPost metadata = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .title(title)
                .tagId(tagId)
                .tags(toJsonOrNull(tags))
                .imgUrls(toJsonOrNull(imgUrls))
                .visible(visible)
                .isTop(isTop)
                .description(description)
                .type(TYPE_IMAGE_TEXT)
                .updateTime(Instant.now())
                .build();

        ensureSingleRow(knowPostMapper.updateMetadata(metadata), "草稿不存在或无权限");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void publish(long creatorId, long id) {
        validateCreator(creatorId);
        validatePostId(id);
        ensureSingleRow(knowPostMapper.publish(id, creatorId), "草稿不存在或无权限");
    }

    /**
     * 确保作者用户存在。
     *
     * @param creatorId 作者用户 ID
     */
    private void validateCreator(long creatorId) {
        if (creatorId <= 0 || userService.findById(creatorId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
    }

    /**
     * 校验知文 ID。
     *
     * @param id 知文 ID
     */
    private void validatePostId(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知文 ID 非法");
        }
    }

    /**
     * 将可选字符串列表转换为 JSON。
     *
     * @param values 字符串列表，{@code null} 表示不更新
     * @return JSON 数组或 {@code null}
     */
    private String toJsonOrNull(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 处理失败");
        }
    }

    /**
     * 校验 Mapper 只更新一条记录。
     *
     * @param affectedRows 实际受影响行数
     * @param missingMessage 未更新时的提示
     */
    private void ensureSingleRow(int affectedRows, String missingMessage) {
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, missingMessage);
        }
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "知文更新失败");
        }
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param message 校验失败提示
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
