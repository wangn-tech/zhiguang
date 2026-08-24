package com.wangning.search.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 未启用时的搜索服务实现。
 *
 * <p>保留公开接口并明确返回 503，避免将“搜索未部署”误报为无搜索结果。</p>
 */
@Service
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSearchService implements SearchService {

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchResponse search(String keyword, int size, String tagsCsv, String after, Long currentUserId) {
        throw unavailable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SuggestResponse suggest(String prefix, int size) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.SEARCH_UNAVAILABLE, "搜索服务尚未启用");
    }
}
