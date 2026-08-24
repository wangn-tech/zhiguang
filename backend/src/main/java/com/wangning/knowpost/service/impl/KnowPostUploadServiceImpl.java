package com.wangning.knowpost.service.impl;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.knowpost.api.dto.StoragePresignRequest;
import com.wangning.knowpost.api.dto.StoragePresignResponse;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.KnowPostUploadService;
import com.wangning.storage.ObjectStorageService;
import com.wangning.storage.aliyun.OssProperties;
import com.wangning.storage.model.PresignedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 沿用原项目对象键和请求字段的知文预签名上传服务。
 */
@Service
@RequiredArgsConstructor
public class KnowPostUploadServiceImpl implements KnowPostUploadService {

    private static final String CONTENT_SCENE = "knowpost_content";
    private static final String IMAGE_SCENE = "knowpost_image";
    private static final DateTimeFormatter IMAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private final KnowPostMapper knowPostMapper;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final OssProperties ossProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public StoragePresignResponse presignUpload(long userId, StoragePresignRequest request) {
        if (userId <= 0 || request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传请求非法");
        }
        long postId = parsePostId(request.postId());
        KnowPost post = knowPostMapper.findById(postId);
        if (post == null || !Objects.equals(post.getCreatorId(), userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        if (!CONTENT_SCENE.equals(request.scene()) && !IMAGE_SCENE.equals(request.scene())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的上传场景");
        }

        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(ErrorCode.STORAGE_CONFIGURATION_ERROR);
        }

        String objectKey = buildObjectKey(postId, request.scene(), request.ext(), request.contentType());
        PresignedUpload upload = storageService.presignUpload(
                objectKey,
                request.contentType(),
                ossProperties.getPresignTtl()
        );
        long seconds = upload.expiresIn().toSeconds();
        if (seconds > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "上传签名有效期非法");
        }
        return new StoragePresignResponse(upload.objectKey(), upload.putUrl(), upload.headers(), (int) seconds);
    }

    /**
     * 将字符串知文 ID 解析为正整数。
     *
     * @param postIdText 字符串知文 ID
     * @return 知文 ID
     */
    private long parsePostId(String postIdText) {
        try {
            long postId = Long.parseLong(postIdText);
            if (postId <= 0) {
                throw new NumberFormatException("postId must be positive");
            }
            return postId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "postId 非法");
        }
    }

    /**
     * 根据旧项目规则生成对象键。
     *
     * @param postId 知文 ID
     * @param scene 上传场景
     * @param ext 前端扩展名
     * @param contentType MIME 类型
     * @return OSS 对象键
     */
    private String buildObjectKey(long postId, String scene, String ext, String contentType) {
        String normalizedExt = normalizeExtension(ext, contentType, scene);
        if (CONTENT_SCENE.equals(scene)) {
            return "posts/%d/content%s".formatted(postId, normalizedExt);
        }
        String date = IMAGE_DATE_FORMATTER.format(Instant.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "posts/%d/images/%s/%s%s".formatted(postId, date, random, normalizedExt);
    }

    /**
     * 沿用旧项目的扩展名兼容逻辑：优先信任前端 ext，缺失时由 MIME 类型推导。
     *
     * @param ext 前端扩展名
     * @param contentType MIME 类型
     * @param scene 上传场景
     * @return 带点号的扩展名
     */
    private String normalizeExtension(String ext, String contentType, String scene) {
        if (StringUtils.hasText(ext)) {
            String trimmed = ext.trim();
            return trimmed.startsWith(".") ? trimmed : "." + trimmed;
        }
        if (CONTENT_SCENE.equals(scene)) {
            return switch (contentType) {
                case "text/markdown" -> ".md";
                case "text/html" -> ".html";
                case "text/plain" -> ".txt";
                case "application/json" -> ".json";
                default -> ".bin";
            };
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }
}
