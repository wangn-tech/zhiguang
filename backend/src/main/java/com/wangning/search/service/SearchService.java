package com.wangning.search.service;

import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;

/**
 * 公开知文搜索服务。
 */
public interface SearchService {

    /**
     * 按关键词检索公开已发布知文。
     *
     * @param keyword 关键词
     * @param size 当前页最大结果数
     * @param tagsCsv 可选逗号分隔标签过滤条件
     * @param after Base64URL 翻页游标，可为空
     * @param currentUserId 当前用户 ID；匿名时为 {@code null}
     * @return 搜索结果页
     */
    SearchResponse search(String keyword, int size, String tagsCsv, String after, Long currentUserId);

    /**
     * 按标题前缀查询联想建议。
     *
     * @param prefix 标题前缀
     * @param size 最大候选数
     * @return 标题联想结果
     */
    SuggestResponse suggest(String prefix, int size);
}
