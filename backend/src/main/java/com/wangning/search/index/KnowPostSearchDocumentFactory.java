package com.wangning.search.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.search.config.SearchProperties;
import com.wangning.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 将知文详情数据投影为 Elasticsearch 搜索文档。
 *
 * <p>正文通过受控的 {@link ObjectStorageService} 对象键读取，不会根据数据库中的任意 URL 进行网络请求。
 * 对象存储暂不可用时仍索引标题和摘要，保证异步索引链路可以重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowPostSearchDocumentFactory {

    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final SearchProperties properties;

    /**
     * 判断知文是否应出现在公开搜索索引中。
     *
     * @param row 知文详情行
     * @return 已发布且公开时为 {@code true}
     */
    public boolean isSearchable(KnowPostDetailRow row) {
        return row != null
                && row.getId() != null
                && row.getId() > 0
                && STATUS_PUBLISHED.equals(row.getStatus())
                && VISIBILITY_PUBLIC.equals(row.getVisible());
    }

    /**
     * 将已校验的公开知文投影为搜索文档。
     *
     * @param row 已发布且公开的知文详情行
     * @return 搜索文档
     * @throws IllegalArgumentException 知文不满足公开索引条件时抛出
     */
    public KnowPostSearchDocument create(KnowPostDetailRow row) {
        if (!isSearchable(row)) {
            throw new IllegalArgumentException("只能索引已发布的公开知文");
        }
        String body = readBody(row.getContentObjectKey(), row.getId());
        return new KnowPostSearchDocument(
                row.getId(),
                row.getTitle(),
                row.getDescription(),
                StringUtils.hasText(body) ? body : row.getDescription(),
                parseStringArray(row.getTags()),
                row.getCreatorId() == null ? 0L : row.getCreatorId(),
                row.getAuthorAvatar(),
                row.getAuthorNickname(),
                row.getAuthorTagJson(),
                parseStringArray(row.getImgUrls()),
                row.getIsTop(),
                row.getPublishTime(),
                STATUS_PUBLISHED,
                StringUtils.hasText(row.getTitle()) ? row.getTitle() : null
        );
    }

    /**
     * 从对象存储读取受限长度的 UTF-8 正文。
     *
     * @param objectKey 正文对象键
     * @param knowPostId 用于安全日志的知文 ID
     * @return 正文；对象存储未启用、对象键为空或读取失败时返回 {@code null}
     */
    private String readBody(String objectKey, long knowPostId) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            return null;
        }
        try (InputStream inputStream = storageService.download(objectKey)) {
            byte[] content = inputStream.readNBytes(properties.getMaxBodyBytes());
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "读取知文正文用于搜索索引失败，将仅索引标题和摘要：knowPostId={}, exceptionType={}",
                    knowPostId,
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    /**
     * 解析 MySQL JSON 数组字段；历史异常数据降级为空列表。
     *
     * @param json JSON 数组文本
     * @return 字符串列表
     */
    private List<String> parseStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? Collections.emptyList() : List.copyOf(values);
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }
}
