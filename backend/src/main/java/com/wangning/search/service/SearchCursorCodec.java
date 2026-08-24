package com.wangning.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;

/**
 * 搜索分页游标的 Base64URL JSON 编解码器。
 *
 * <p>游标显式记录其数据源，确保 Elasticsearch 与 MySQL 降级检索不会混用不同的排序键。</p>
 */
@Component
@RequiredArgsConstructor
public class SearchCursorCodec {

    /** Elasticsearch 游标数据源标记。 */
    public static final String ES_SOURCE = "es";

    /** MySQL 降级游标数据源标记。 */
    public static final String MYSQL_SOURCE = "mysql";

    private final ObjectMapper objectMapper;

    /**
     * 读取游标的数据源标记。
     *
     * @param encodedCursor Base64URL JSON 游标，可为空
     * @return 数据源标记；首次请求时为 {@code null}
     */
    public String sourceOf(String encodedCursor) {
        if (!StringUtils.hasText(encodedCursor)) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor);
            String source = objectMapper.readTree(json).path("source").asText();
            if (!ES_SOURCE.equals(source) && !MYSQL_SOURCE.equals(source)) {
                throw new IllegalArgumentException("unknown source");
            }
            return source;
        } catch (IllegalArgumentException | IOException exception) {
            throw invalidCursor();
        }
    }

    /**
     * 编码 Elasticsearch 排序游标。
     *
     * @param score 相关性分数
     * @param publishTime 发布时间毫秒值
     * @param id 知文 ID
     * @return Base64URL JSON 游标
     */
    public String encodeEs(double score, long publishTime, long id) {
        return encode(new EsCursor(ES_SOURCE, score, publishTime, id));
    }

    /**
     * 解码 Elasticsearch 排序游标。
     *
     * @param encodedCursor Base64URL JSON 游标
     * @return 已校验的 ES 游标
     */
    public EsCursor decodeEs(String encodedCursor) {
        EsCursor cursor = decode(encodedCursor, EsCursor.class);
        if (!ES_SOURCE.equals(cursor.source()) || !Double.isFinite(cursor.score())
                || cursor.publishTime() < 0 || cursor.id() <= 0) {
            throw invalidCursor();
        }
        return cursor;
    }

    /**
     * 编码 MySQL 降级排序游标。
     *
     * @param isTop 是否置顶
     * @param publishTime 发布时间毫秒值
     * @param id 知文 ID
     * @return Base64URL JSON 游标
     */
    public String encodeMysql(boolean isTop, long publishTime, long id) {
        return encode(new MysqlCursor(MYSQL_SOURCE, isTop, publishTime, id));
    }

    /**
     * 解码 MySQL 降级排序游标。
     *
     * @param encodedCursor Base64URL JSON 游标
     * @return 已校验的 MySQL 游标
     */
    public MysqlCursor decodeMysql(String encodedCursor) {
        MysqlCursor cursor = decode(encodedCursor, MysqlCursor.class);
        if (!MYSQL_SOURCE.equals(cursor.source()) || cursor.publishTime() < 0 || cursor.id() <= 0) {
            throw invalidCursor();
        }
        return cursor;
    }

    private String encode(Object cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(cursor));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("搜索游标序列化失败", exception);
        }
    }

    private <T> T decode(String encodedCursor, Class<T> cursorType) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(encodedCursor), cursorType);
        } catch (IllegalArgumentException | IOException exception) {
            throw invalidCursor();
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "搜索游标无效");
    }

    /**
     * Elasticsearch {@code search_after} 游标。
     *
     * @param source 数据源标记
     * @param score 相关性分数
     * @param publishTime 发布时间毫秒值
     * @param id 知文 ID
     */
    public record EsCursor(String source, double score, long publishTime, long id) {
    }

    /**
     * MySQL 降级 keyset 游标。
     *
     * @param source 数据源标记
     * @param isTop 是否置顶
     * @param publishTime 发布时间毫秒值
     * @param id 知文 ID
     */
    public record MysqlCursor(String source, boolean isTop, long publishTime, long id) {
    }
}
